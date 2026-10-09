package com.ratig.app.data.remote

import com.ratig.app.core.result.AppError
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.result.map
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.local.dao.WorkerDao
import com.ratig.app.data.local.entity.CachedWorkerEntity
import com.ratig.app.data.offline.OfflinePolicyStore
import com.ratig.app.data.remote.dto.DepartmentDto
import com.ratig.app.data.remote.dto.SessionOverviewDto
import com.ratig.app.data.remote.dto.ShiftDto
import com.ratig.app.data.remote.dto.WorkAreaDto
import com.ratig.app.data.remote.dto.WorkerDto
import com.ratig.app.data.remote.dto.fromDomain
import com.ratig.app.data.remote.dto.toDomain
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.model.WorkerFilter
import com.ratig.app.domain.repository.WorkerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import javax.inject.Inject
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a worker lookup answer came from. */
enum class WorkerLookupSource { LIVE_SERVER, CACHE }

/**
 * Worker lookup with its provenance, for UI source badges
 * ("Live server" / "Cache — diperbarui HH:mm"). [cachedAt] is set only for
 * [WorkerLookupSource.CACHE] answers.
 */
data class WorkerLookup(
    val worker: Worker,
    val source: WorkerLookupSource,
    val cachedAt: Instant?,
)

/**
 * PostgREST-backed [WorkerRepository] with an offline-first worker cache.
 *
 * Workers are stored flat in `workers`; display names for department / work
 * area / shift are resolved LOCALLY from the (rarely changing) organization
 * lists instead of nested PostgREST joins. The lookup maps are loaded once
 * per process and transparently reloaded when a worker references an id that
 * is not in the cache (e.g. master data created moments ago).
 *
 * Cache contract (CONTRACT-2 §Offline-first):
 *  - Successful list/get/findByNik answers are upserted into the Room worker
 *    cache (server-confirmed rows, sync status SYNCED) so identification
 *    and lists keep working offline.
 *  - [findByNik] is cache-first: a fresh (within cacheMaxAgeHours) cached
 *    row is answered without a roundtrip; otherwise the server is queried
 *    and the cache refreshed. On a NETWORK failure a stale cached row is
 *    still returned (offline identification), never on other errors.
 */
class WorkerRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val workerDao: WorkerDao,
    private val offlinePolicy: OfflinePolicyStore,
) : WorkerRepository {

    // region workers

    override suspend fun findByNik(nik: String): AppResult<Worker?> =
        findByNikDetailed(nik).map { lookup -> lookup?.worker }

    /**
     * Cache-first NIK lookup with a source marker. NIK stays a String end to
     * end (digits, leading zeros preserved); it never appears in logs.
     */
    suspend fun findByNikDetailed(nik: String): AppResult<WorkerLookup?> {
        val normalized = nik.trim()
        if (normalized.isEmpty()) return AppResult.Success(null)

        val cached = workerDao.byNik(normalized)
        if (cached != null && offlinePolicy.isCacheFresh(cached.cachedAt)) {
            return AppResult.Success(
                WorkerLookup(
                    worker = cached.toDomain(),
                    source = WorkerLookupSource.CACHE,
                    cachedAt = Instant.ofEpochMilli(cached.cachedAt),
                ),
            )
        }

        val fetched = AppResult.of {
            supabase.postgrest["workers"].select {
                filter { eq("nik", normalized) }
                limit(1)
            }.decodeList<WorkerDto>().firstOrNull()
        }
        return when (fetched) {
            is AppResult.Success -> {
                val dto = fetched.value
                if (dto == null) {
                    // The server no longer knows this NIK: drop any stale row.
                    workerDao.deleteByNik(normalized)
                    AppResult.Success(null)
                } else {
                    val worker = enrich(dto)
                    cacheWorker(worker, dto.nik ?: normalized)
                    AppResult.Success(
                        WorkerLookup(worker, WorkerLookupSource.LIVE_SERVER, cachedAt = null),
                    )
                }
            }

            is AppResult.Failure ->
                if (cached != null && fetched.error is AppError.Network) {
                    // Offline fallback: stale cache beats no answer for NIK search.
                    AppResult.Success(
                        WorkerLookup(
                            worker = cached.toDomain(),
                            source = WorkerLookupSource.CACHE,
                            cachedAt = Instant.ofEpochMilli(cached.cachedAt),
                        ),
                    )
                } else {
                    AppResult.Failure(fetched.error)
                }
        }
    }

    override suspend fun list(filter: WorkerFilter): AppResult<List<Worker>> = AppResult.of {
        val query = filter.query?.trim()?.takeIf { it.isNotEmpty() }
        val rows = supabase.postgrest["workers"].select {
            filter {
                if (filter.activeOnly) eq("active_status", true)
                filter.departmentId?.let { eq("department_id", it) }
                filter.shiftId?.let { eq("shift_id", it) }
                if (query != null) {
                    or {
                        ilike("full_name", "%$query%")
                        ilike("employee_number", "%$query%")
                    }
                }
            }
            range(filter.offset.toLong(), (filter.offset + filter.limit - 1).toLong())
            order("full_name", Order.ASCENDING)
        }.decodeList<WorkerDto>()
        val workers = rows.map { enrich(it) }
        cacheWorkers(rows, workers)
        workers
    }

    override suspend fun get(id: String): AppResult<Worker> {
        val result = AppResult.of {
            val dto = supabase.postgrest["workers"].select {
                filter { eq("id", id) }
                limit(1)
            }.decodeList<WorkerDto>().firstOrNull()
            if (dto == null) {
                null
            } else {
                val worker = enrich(dto)
                cacheWorker(worker, dto.nik)
                worker
            }
        }
        return when (result) {
            is AppResult.Success -> result.value?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Validation("Pekerja tidak ditemukan."))
            is AppResult.Failure -> result
        }
    }

    override suspend fun create(worker: Worker): AppResult<Worker> {
        val available = employeeNumberAvailable(worker.employeeNumber, excludeId = null)
        if (available is AppResult.Failure) return available
        if ((available as AppResult.Success).value == false) {
            return AppResult.Failure(duplicateEmployeeNumberError(worker.employeeNumber))
        }
        // The unique constraint on employee_number remains the race-safe backstop;
        // a concurrent duplicate surfaces as AppError.Conflict via toAppError().
        return AppResult.of {
            val created = supabase.postgrest["workers"]
                .insert(WorkerDto.fromDomain(worker)) { select() }
                .decodeSingle<WorkerDto>()
            enrich(created)
        }
    }

    override suspend fun update(worker: Worker): AppResult<Worker> {
        val available = employeeNumberAvailable(worker.employeeNumber, excludeId = worker.id)
        if (available is AppResult.Failure) return available
        if ((available as AppResult.Success).value == false) {
            return AppResult.Failure(duplicateEmployeeNumberError(worker.employeeNumber))
        }
        val result = AppResult.of {
            val updated = supabase.postgrest["workers"].update(
                update = {
                    set("employee_number", worker.employeeNumber.trim())
                    set("full_name", worker.fullName.trim())
                    set("email", worker.email?.trim()?.takeIf { it.isNotEmpty() })
                    set("department_id", worker.departmentId)
                    set("work_area_id", worker.workAreaId)
                    set("job_title", worker.jobTitle?.trim()?.takeIf { it.isNotEmpty() })
                    set("shift_id", worker.shiftId)
                    set("active_status", worker.activeStatus)
                },
                request = {
                    filter { eq("id", worker.id) }
                    select()
                },
            ).decodeSingleOrNull<WorkerDto>()
            if (updated == null) null else enrich(updated)
        }
        return when (result) {
            is AppResult.Success -> result.value?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Validation("Pekerja tidak ditemukan."))
            is AppResult.Failure -> result
        }
    }

    override suspend fun deactivate(id: String): AppResult<Unit> = setActiveStatus(id, false)

    override suspend fun reactivate(id: String): AppResult<Unit> = setActiveStatus(id, true)

    override suspend fun getMyRecord(): AppResult<Worker?> {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return AppResult.Failure(AppError.NotAuthenticated)
        return AppResult.of {
            val dto = supabase.postgrest["workers"].select {
                filter { eq("user_id", userId) }
                limit(1)
            }.decodeList<WorkerDto>().firstOrNull()
            if (dto == null) null else enrich(dto)
        }
    }

    override suspend fun examinationHistory(
        workerId: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<TestSession>> = AppResult.of {
        supabase.postgrest["v_session_overview"].select {
            filter { eq("worker_id", workerId) }
            order("completed_at", Order.DESCENDING, nullsFirst = false)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<SessionOverviewDto>().map { it.toDomain() }
    }

    private suspend fun cacheWorker(worker: Worker, nik: String?) {
        val ownerUserId = supabase.auth.currentUserOrNull()?.id.orEmpty()
        workerDao.upsert(CachedWorkerEntity.fromWorker(worker, nik, ownerUserId, TimeProvider.nowUtc().toEpochMilli()))
    }

    private suspend fun cacheWorkers(rows: List<WorkerDto>, workers: List<Worker>) {
        if (rows.isEmpty()) return
        val ownerUserId = supabase.auth.currentUserOrNull()?.id.orEmpty()
        val now = TimeProvider.nowUtc().toEpochMilli()
        val byId = rows.associateBy({ it.id.orEmpty() }, { it.nik })
        workerDao.upsertAll(workers.map { CachedWorkerEntity.fromWorker(it, byId[it.id], ownerUserId, now) })
    }

    // region helpers

    private fun duplicateEmployeeNumberError(employeeNumber: String): AppError.Conflict =
        AppError.Conflict(
            "Nomor induk pekerja \"$employeeNumber\" sudah digunakan. Gunakan nomor lain.",
        )

    /** True when no OTHER row (excluding [excludeId] on update) uses the number. */
    private suspend fun employeeNumberAvailable(
        employeeNumber: String,
        excludeId: String?,
    ): AppResult<Boolean> = AppResult.of {
        val rows = supabase.postgrest["workers"].select {
            filter {
                eq("employee_number", employeeNumber)
                if (excludeId != null) neq("id", excludeId)
            }
            limit(1)
        }.decodeList<WorkerDto>()
        rows.isEmpty()
    }

    private suspend fun setActiveStatus(id: String, active: Boolean): AppResult<Unit> = AppResult.of {
        supabase.postgrest["workers"].update(
            update = { set("active_status", active) },
            request = { filter { eq("id", id) } },
        )
        Unit
    }

    // endregion

    // region organization name resolution (local join)

    private data class OrgMaps(
        val departments: Map<String, String>,
        val workAreas: Map<String, String>,
        val shifts: Map<String, String>,
    )

    private val orgCacheMutex = Mutex()

    @Volatile
    private var orgCache: OrgMaps? = null

    private suspend fun orgMaps(refresh: Boolean = false): OrgMaps {
        orgCache?.takeIf { !refresh }?.let { return it }
        return orgCacheMutex.withLock {
            orgCache?.takeIf { !refresh } ?: run {
                val departments = supabase.postgrest["departments"].select {
                    order("name", Order.ASCENDING)
                }.decodeList<DepartmentDto>().associate { it.id.orEmpty() to it.name }
                val workAreas = supabase.postgrest["work_areas"].select {
                    order("name", Order.ASCENDING)
                }.decodeList<WorkAreaDto>().associate { it.id.orEmpty() to it.name }
                val shifts = supabase.postgrest["shifts"].select {
                    order("name", Order.ASCENDING)
                }.decodeList<ShiftDto>().associate { it.id.orEmpty() to it.name }
                OrgMaps(departments, workAreas, shifts).also { orgCache = it }
            }
        }
    }

    /** Maps display names locally; busts the cache once for unknown ids. */
    private suspend fun enrich(dto: WorkerDto): Worker {
        var maps = orgMaps()
        val knownIds = dto.departmentId in maps.departments &&
            (dto.workAreaId == null || dto.workAreaId in maps.workAreas) &&
            (dto.shiftId == null || dto.shiftId in maps.shifts)
        if (!knownIds) {
            maps = orgMaps(refresh = true)
        }
        return dto.toDomain(
            departmentName = dto.departmentId?.let(maps.departments::get),
            workAreaName = dto.workAreaId?.let(maps.workAreas::get),
            shiftName = dto.shiftId?.let(maps.shifts::get),
        )
    }

    // endregion
}

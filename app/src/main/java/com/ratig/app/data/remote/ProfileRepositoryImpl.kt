package com.ratig.app.data.remote

import com.ratig.app.core.json.JsonCodec
import com.ratig.app.core.result.AppError
import com.ratig.app.core.result.AppResult
import com.ratig.app.data.remote.dto.ProfileDto
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.CreatedUser
import com.ratig.app.domain.repository.CreateUserRequest
import com.ratig.app.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST access to `profiles`. Listings and account mutations are
 * admin-only; authorization is enforced by RLS on the server and mirrored
 * here so self-modification is rejected before a roundtrip is made.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : ProfileRepository {

    override suspend fun getProfile(userId: String): AppResult<UserProfile> {
        val fetched = AppResult.of {
            supabase.postgrest["profiles"].select {
                filter { eq("id", userId) }
                limit(1)
            }.decodeList<ProfileDto>().firstOrNull()?.toDomain()
        }
        return when (fetched) {
            is AppResult.Failure -> AppResult.Failure(fetched.error)
            is AppResult.Success ->
                if (fetched.value == null) {
                    AppResult.Failure(AppError.Validation("Profil tidak ditemukan."))
                } else {
                    AppResult.Success(fetched.value)
                }
        }
    }

    override suspend fun listUsers(
        status: AccountStatus?,
        query: String?,
        limit: Int,
        offset: Int,
    ): AppResult<List<UserProfile>> = AppResult.of {
        supabase.postgrest["profiles"].select {
            filter {
                if (status != null) eq("account_status", status.name.lowercase())
                if (!query.isNullOrBlank()) ilike("full_name", "%${query.trim()}%")
            }
            order("created_at", Order.DESCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<ProfileDto>().map { it.toDomain() }
    }

    override suspend fun createUser(request: CreateUserRequest): AppResult<CreatedUser> {
        val payload = buildJsonObject {
            put("email", request.email.trim().lowercase())
            put("full_name", request.fullName.trim())
            put("role", request.role.name.lowercase())
            put("account_status", request.accountStatus.name.lowercase())
            request.password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
        }

        val call = AppResult.of {
            val response: HttpResponse = supabase.functions.invoke("admin-create-user", payload)
            response.status.value to response.body<String>()
        }

        return when (call) {
            is AppResult.Failure -> call
            is AppResult.Success -> {
                val (status, bodyText) = call.value
                if (status !in 200..299) {
                    // The Edge Function returns { "error": "..." } in Indonesian;
                    // surface it directly so the admin sees the real cause.
                    val serverMessage = runCatching {
                        JsonCodec.decode<JsonObject>(bodyText)["error"]?.jsonPrimitive?.content
                    }.getOrNull()
                    AppResult.Failure(
                        AppError.Validation(serverMessage ?: "Gagal membuat pengguna."),
                    )
                } else {
                    val json = JsonCodec.decode<JsonObject>(bodyText)
                    AppResult.Success(
                        CreatedUser(
                            userId = json["user_id"]?.jsonPrimitive?.content.orEmpty(),
                            email = json["email"]?.jsonPrimitive?.content ?: request.email,
                            fullName = json["full_name"]?.jsonPrimitive?.content ?: request.fullName,
                            role = UserRole.fromRaw(json["role"]?.jsonPrimitive?.content),
                            accountStatus = AccountStatus.fromRaw(
                                json["account_status"]?.jsonPrimitive?.content,
                            ),
                            inviteSent = json["invite_sent"]?.jsonPrimitive?.booleanOrNull ?: false,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun setAccountStatus(userId: String, status: AccountStatus): AppResult<Unit> {
        if (userId == currentUserId()) {
            return AppResult.Failure(AppError.Forbidden("Tidak dapat mengubah akun sendiri"))
        }
        return AppResult.of {
            supabase.postgrest["profiles"].update({
                set("account_status", status.name.lowercase())
            }) {
                filter { eq("id", userId) }
            }
        }
    }

    override suspend fun setRole(userId: String, role: UserRole): AppResult<Unit> {
        if (userId == currentUserId()) {
            return AppResult.Failure(AppError.Forbidden("Tidak dapat mengubah akun sendiri"))
        }
        return AppResult.of {
            supabase.postgrest["profiles"].update({
                set("role", role.name.lowercase())
            }) {
                filter { eq("id", userId) }
            }
        }
    }

    override suspend fun listPendingApprovals(): AppResult<List<UserProfile>> = AppResult.of {
        supabase.postgrest["profiles"].select {
            filter { eq("account_status", AccountStatus.PENDING.name.lowercase()) }
            order("created_at", Order.ASCENDING)
        }.decodeList<ProfileDto>().map { it.toDomain() }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id
}

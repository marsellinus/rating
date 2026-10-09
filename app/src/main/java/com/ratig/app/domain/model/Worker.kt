package com.ratig.app.domain.model

import com.ratig.app.core.json.InstantAsStringSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A worker registered for RATIG examinations.
 *
 * Privacy: no mandatory health/diagnosis fields. Age or other personal factors
 * are collected only if a protocol requires them (see docs/architecture.md).
 */
@Serializable
data class Worker(
    val id: String,
    val employeeNumber: String,
    val fullName: String,
    val email: String? = null,
    val departmentId: String? = null,
    val workAreaId: String? = null,
    val jobTitle: String? = null,
    val shiftId: String? = null,
    val activeStatus: Boolean = true,
    /** auth.users id when the worker links their own Google account (self-service view). */
    val userId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val updatedAt: Instant? = null,
    // Denormalized display fields (from joined views)
    val departmentName: String? = null,
    val workAreaName: String? = null,
    val shiftName: String? = null,
)

data class WorkerFilter(
    val query: String? = null,
    val departmentId: String? = null,
    val shiftId: String? = null,
    val activeOnly: Boolean = true,
    val limit: Int = 50,
    val offset: Int = 0,
)

package com.ratig.app.domain.model

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.core.json.LocalTimeAsStringSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime

@Serializable
data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val accountStatus: AccountStatus,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val updatedAt: Instant? = null,
)

@Serializable
data class Department(
    val id: String,
    val name: String,
    val description: String? = null,
    val activeStatus: Boolean = true,
)

@Serializable
data class WorkArea(
    val id: String,
    val departmentId: String,
    val name: String,
    val description: String? = null,
    val activeStatus: Boolean = true,
)

@Serializable
data class Shift(
    val id: String,
    val name: String,
    /** Local start/end time in the shift's timezone, e.g. "07:00", "15:00". */
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val endTime: LocalTime,
    val timezone: String = "Asia/Jakarta",
    val activeStatus: Boolean = true,
)

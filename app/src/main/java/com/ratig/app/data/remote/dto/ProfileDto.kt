package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row of the `profiles` table. `role` and `account_status` arrive as raw
 * text columns (CHECK-constrained on the server); they are kept as String
 * here and mapped leniently to the domain enums so an unexpected server
 * value can never crash decoding.
 */
@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    val role: String = "worker",
    @SerialName("account_status") val accountStatus: String = "pending",
    @SerialName("created_at")
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    @SerialName("updated_at")
    @Serializable(with = InstantAsStringSerializer::class)
    val updatedAt: Instant? = null,
) {
    fun toDomain(): UserProfile = UserProfile(
        id = id,
        fullName = fullName,
        email = email,
        role = UserRole.fromRaw(role),
        accountStatus = AccountStatus.fromRaw(accountStatus),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(profile: UserProfile): ProfileDto = ProfileDto(
            id = profile.id,
            fullName = profile.fullName,
            email = profile.email,
            role = profile.role.name.lowercase(),
            accountStatus = profile.accountStatus.name.lowercase(),
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
        )
    }
}

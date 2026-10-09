package com.ratig.app.domain.repository

import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import kotlinx.coroutines.flow.StateFlow

/** Current authenticated user state exposed to the UI. */
sealed interface SessionState {
    data object Loading : SessionState
    data object Unauthenticated : SessionState
    /** Google session valid but profile missing/pending/suspended - role UNKNOWN yet. */
    data class AwaitingApproval(val profile: UserProfile) : SessionState
    data class Authenticated(val profile: UserProfile) : SessionState
    data class Error(val message: String) : SessionState
}

interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    /** True when a Google Web client id is configured (Google sign-in usable). */
    val isGoogleAvailable: Boolean

    /** Restores a persisted Supabase session (app relaunch). */
    suspend fun restoreSession(): AppResult<Unit>

    /** Launches Google Credential Manager flow and exchanges the ID token with Supabase. */
    suspend fun signInWithGoogle(activityContext: android.content.Context): AppResult<Unit>

    /**
     * Debug/offline-friendly email+password sign-in (Supabase Auth).
     * Only surfaced by debug builds; production uses [signInWithGoogle].
     */
    suspend fun signInWithPassword(email: String, password: String): AppResult<Unit>

    suspend fun signOut(): AppResult<Unit>

    /** Reloads the current profile (after approval, role change, etc). */
    suspend fun refreshProfile(): AppResult<UserProfile>
}

interface ProfileRepository {
    suspend fun getProfile(userId: String): AppResult<UserProfile>
    /** Admin-only listings. */
    suspend fun listUsers(status: AccountStatus?, query: String?, limit: Int, offset: Int): AppResult<List<UserProfile>>
    /** Admin-only: create a new account (auth user + profile) via Edge Function. */
    suspend fun createUser(request: CreateUserRequest): AppResult<CreatedUser>
    /** Admin-only: set account status. Approval requires role admin + audit. */
    suspend fun setAccountStatus(userId: String, status: AccountStatus): AppResult<Unit>
    /** Admin-only: set role. Never callable for own account (server-enforced). */
    suspend fun setRole(userId: String, role: UserRole): AppResult<Unit>
    /** Admin-only: pending approval queue. */
    suspend fun listPendingApprovals(): AppResult<List<UserProfile>>
}

/** Input for [ProfileRepository.createUser]. */
data class CreateUserRequest(
    val email: String,
    val fullName: String,
    val role: UserRole,
    val accountStatus: AccountStatus,
    /** Blank -> the server emails an invite so the user sets their own password. */
    val password: String? = null,
)

/** Result of a successful user creation (mirrors the Edge Function response). */
data class CreatedUser(
    val userId: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val accountStatus: AccountStatus,
    val inviteSent: Boolean,
)

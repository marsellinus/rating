package com.ratig.app.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ratig.app.core.config.AppConfig
import com.ratig.app.core.result.AppError
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.result.map
import com.ratig.app.data.remote.dto.ProfileDto
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.SessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Credential Manager sign-in backed by Supabase Auth, plus the
 * app-wide [SessionState] flow consumed by navigation. The profile row in
 * `profiles` decides whether the session is usable (ACTIVE) or must wait
 * (PENDING/SUSPENDED).
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val appConfig: AppConfig,
) : AuthRepository {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override val isGoogleAvailable: Boolean get() = appConfig.isGoogleConfigured

    override suspend fun restoreSession(): AppResult<Unit> {
        // Wait until auth finished loading the persisted session from storage,
        // otherwise currentSessionOrNull() returns null during app startup.
        val initialization = AppResult.of { supabase.auth.awaitInitialization() }
        if (initialization is AppResult.Failure) {
            _sessionState.value = SessionState.Error(initialization.error.userMessage)
            return AppResult.Failure(initialization.error)
        }
        val session = supabase.auth.currentSessionOrNull()
        if (session == null) {
            _sessionState.value = SessionState.Unauthenticated
            return AppResult.Success(Unit)
        }
        val sessionUser = session.user
        if (sessionUser == null) {
            _sessionState.value = SessionState.Unauthenticated
            return AppResult.Failure(AppError.NotAuthenticated)
        }
        return loadProfileState(sessionUser.id).map { }
    }

    override suspend fun signInWithGoogle(activityContext: Context): AppResult<Unit> {
        if (!appConfig.isGoogleConfigured) return AppResult.Failure(AppError.NotConfigured)

        val credentialManager = CredentialManager.create(activityContext)
        val request = GetCredentialRequest(
            listOf(
                GetGoogleIdOption.Builder()
                    .setServerClientId(appConfig.googleWebClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build(),
            ),
        )
        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: NoCredentialException) {
            return AppResult.Failure(AppError.Validation("Tidak ada akun Google yang dipilih"))
        } catch (e: GetCredentialCancellationException) {
            return AppResult.Failure(AppError.Validation("Login dibatalkan"))
        } catch (e: GetCredentialException) {
            return AppResult.Failure(AppError.Unexpected("Gagal memilih akun Google. Silakan coba lagi."))
        }

        val googleIdToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken

        val exchange = AppResult.of {
            supabase.auth.signInWith(IDToken) {
                idToken = googleIdToken
                provider = Google
            }
        }
        return when (exchange) {
            is AppResult.Failure -> {
                _sessionState.value = SessionState.Error(exchange.error.userMessage)
                AppResult.Failure(exchange.error)
            }
            is AppResult.Success -> {
                val userId = supabase.auth.currentUserOrNull()?.id
                if (userId == null) {
                    _sessionState.value = SessionState.Unauthenticated
                    AppResult.Failure(AppError.NotAuthenticated)
                } else {
                    loadProfileState(userId).map { }
                }
            }
        }
    }

    override suspend fun signOut(): AppResult<Unit> = AppResult.of {
        supabase.auth.signOut()
        _sessionState.value = SessionState.Unauthenticated
    }

    override suspend fun signInWithPassword(email: String, password: String): AppResult<Unit> {
        if (!appConfig.isConfigured) return AppResult.Failure(AppError.NotConfigured)
        val exchange = AppResult.of {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }
        return when (exchange) {
            is AppResult.Failure -> {
                _sessionState.value = SessionState.Error(exchange.error.userMessage)
                AppResult.Failure(exchange.error)
            }
            is AppResult.Success -> {
                val userId = supabase.auth.currentUserOrNull()?.id
                if (userId == null) {
                    _sessionState.value = SessionState.Unauthenticated
                    AppResult.Failure(AppError.NotAuthenticated)
                } else {
                    loadProfileState(userId).map { }
                }
            }
        }
    }

    override suspend fun refreshProfile(): AppResult<UserProfile> {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            _sessionState.value = SessionState.Unauthenticated
            return AppResult.Failure(AppError.NotAuthenticated)
        }
        return loadProfileState(userId)
    }

    /**
     * Fetches the profile row and publishes the matching [SessionState]:
     * ACTIVE -> [SessionState.Authenticated], PENDING/SUSPENDED ->
     * [SessionState.AwaitingApproval]. A missing row is treated as pending -
     * the `profiles` trigger may not have run yet for a brand new account.
     */
    private suspend fun loadProfileState(userId: String): AppResult<UserProfile> {
        val fetched = AppResult.of {
            supabase.postgrest["profiles"].select {
                filter { eq("id", userId) }
                limit(1)
            }.decodeList<ProfileDto>()
        }
        val profile = when (fetched) {
            is AppResult.Failure -> {
                _sessionState.value = SessionState.Error(fetched.error.userMessage)
                return AppResult.Failure(fetched.error)
            }
            is AppResult.Success ->
                fetched.value.firstOrNull()?.toDomain() ?: syntheticPendingProfile(userId)
        }
        _sessionState.value = when (profile.accountStatus) {
            AccountStatus.ACTIVE -> SessionState.Authenticated(profile)
            AccountStatus.PENDING, AccountStatus.SUSPENDED -> SessionState.AwaitingApproval(profile)
        }
        return AppResult.Success(profile)
    }

    /**
     * Synthetic pending profile built from the Supabase session user metadata,
     * used when the profile row does not exist yet (trigger race on first
     * sign-in).
     */
    private fun syntheticPendingProfile(userId: String): UserProfile {
        val user: UserInfo? = supabase.auth.currentUserOrNull()
        val metadata = user?.userMetadata.orEmpty()
        fun meta(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> (metadata[key] as? JsonPrimitive)?.content }
        return UserProfile(
            id = userId,
            fullName = meta("full_name", "name").orEmpty().ifBlank { user?.email.orEmpty() },
            email = user?.email.orEmpty(),
            role = UserRole.fromRaw(meta("role")),
            accountStatus = AccountStatus.PENDING,
            createdAt = null,
            updatedAt = null,
        )
    }
}

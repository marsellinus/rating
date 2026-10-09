package com.ratig.app.core.result

/**
 * User-facing application error taxonomy. Internal exception details are never
 * surfaced verbatim (they can leak schema/URL information); callers map to one
 * of these cases and the UI renders friendly Indonesian copy.
 */
sealed class AppError(open val userMessage: String) {

    data object NotAuthenticated : AppError("Sesi berakhir. Silakan masuk kembali.")

    data object NotConfigured :
        AppError("Aplikasi belum dikonfigurasi (Supabase URL / anon key / Google Client ID). Lihat docs/deployment.md.")

    data object PendingApproval : AppError("Akun Anda menunggu persetujuan administrator.")

    data object AccountSuspended : AppError("Akun Anda dinonaktifkan. Hubungi administrator.")

    data class Forbidden(override val userMessage: String = "Anda tidak memiliki izin untuk aksi ini.") :
        AppError(userMessage)

    data class Network(override val userMessage: String = "Koneksi jaringan bermasalah. Periksa koneksi lalu coba lagi.") :
        AppError(userMessage)

    data class Validation(override val userMessage: String) : AppError(userMessage)

    data class Conflict(override val userMessage: String = "Data berubah atau sudah ada. Muat ulang lalu coba lagi.") :
        AppError(userMessage)

    data class Unexpected(override val userMessage: String = "Terjadi kesalahan tak terduga. Coba lagi.") :
        AppError(userMessage)
}

/** Result wrapper used by repositories; forces explicit error handling. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    companion object {
        inline fun <T> of(block: () -> T): AppResult<T> = try {
            Success(block())
        } catch (e: Throwable) {
            Failure(e.toAppError())
        }
    }
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

suspend inline fun <T, R> AppResult<T>.mapSuspend(transform: suspend (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

/** Maps low-level exceptions to [AppError]. HTTP status from Supabase is honored. */
fun Throwable.toAppError(): AppError {
    val message = message ?: ""
    val lower = message.lowercase()
    return when {
        this is io.github.jan.supabase.exceptions.HttpRequestException ||
            this is io.ktor.client.plugins.HttpRequestTimeoutException ||
            lower.contains("unable to resolve host") ||
            lower.contains("failed to connect") ||
            lower.contains("timeout") -> AppError.Network()
        // Supabase/PostgREST error bodies: RLS violations come back as 4xx with
        // a Postgres error code embedded in the message.
        lower.contains("42501") || lower.contains("row-level security") || lower.contains("permission denied") ->
            AppError.Forbidden()
        lower.contains("23505") || lower.contains("duplicate key") || lower.contains("already exists") ->
            AppError.Conflict("Data sudah ada (nomor pekerja duplikat?).")
        lower.contains("23503") || lower.contains("foreign key") ->
            AppError.Validation("Data terkait tidak ditemukan atau tidak valid.")
        lower.contains("23514") || lower.contains("check constraint") ->
            AppError.Validation("Nilai tidak memenuhi aturan data yang berlaku.")
        lower.contains("401") || lower.contains("jwt") || lower.contains("unauthorized") ->
            AppError.NotAuthenticated
        lower.contains("403") -> AppError.Forbidden()
        lower.contains("409") -> AppError.Conflict()
        lower.contains("invalid api key") || lower.contains("invalid supabase url") -> AppError.NotConfigured
        else -> AppError.Unexpected()
    }
}

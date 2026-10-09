package com.ratig.app.core.sync

/**
 * Local sync lifecycle for offline-first records. A record is SYNCED only
 * after the server confirmed persistence (finalize RPC 200 / upsert OK).
 * Room saves alone are PENDING ("Tersimpan di perangkat").
 */
enum class SyncStatus {
    /** Saved locally, never accepted by the server (or not yet attempted). */
    PENDING,

    /** A sync attempt is in flight. */
    SYNCING,

    /** Server confirmed persistence. */
    SYNCED,

    /** Temporary failure (network/timeout/401 after refresh) - will retry. */
    FAILED_RETRYABLE,

    /** Rejected permanently after max retries - needs administrator review. */
    FAILED_PERMANENT,

    /** Server refused for authorization/validation reasons - manual review. */
    NEEDS_REVIEW,
}

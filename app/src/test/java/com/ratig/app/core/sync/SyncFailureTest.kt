package com.ratig.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for sync failure classification and user-facing labels. The
 * classification decides whether the SyncWorker retries automatically or
 * escalates to an administrator, so it must be conservative and stable.
 */
class SyncFailureTest {

    private fun failureFrom(message: String?) =
        SyncFailure.from(RuntimeException(message))

    @Test
    fun `network timeouts and dns failures are retryable`() {
        assertTrue(SyncFailure.from(java.io.IOException("socket closed")) is SyncFailure.Network)
        assertTrue(failureFrom("Failed to connect to host") is SyncFailure.Network)
        assertTrue(failureFrom("Unable to resolve host \"x\"") is SyncFailure.Network)
        assertTrue(failureFrom("Request timeout") is SyncFailure.Network)
    }

    @Test
    fun `authentication problems map to retryable auth`() {
        assertTrue(failureFrom("HTTP 401 Unauthorized") is SyncFailure.Auth)
        assertTrue(failureFrom("JWT expired") is SyncFailure.Auth)
    }

    @Test
    fun `rls and permission errors are not retried`() {
        val forbidden = failureFrom("ERROR 42501: new row violates row-level security policy")
        assertTrue(forbidden is SyncFailure.Forbidden)
        assertFalse(forbidden.retryable)
        assertTrue(failureFrom("403 Forbidden") is SyncFailure.Forbidden)
    }

    @Test
    fun `postgres constraint codes map to validation with a specific reason`() {
        val fk = failureFrom("insert violates foreign key 23503")
        assertTrue(fk is SyncFailure.Validation)
        assertEquals("23503", (fk as SyncFailure.Validation).code)
        assertFalse(fk.retryable)

        assertEquals("23514", (failureFrom("check constraint 23514") as SyncFailure.Validation).code)
        assertEquals("22001", (failureFrom("value too long 22001") as SyncFailure.Validation).code)
    }

    @Test
    fun `generic 400 maps to validation without a code`() {
        val f = failureFrom("HTTP 400 Bad Request: invalid input syntax")
        assertTrue(f is SyncFailure.Validation)
        assertEquals(null, (f as SyncFailure.Validation).code)
    }

    @Test
    fun `unknown errors fall back to unexpected and are not retried`() {
        val f = failureFrom("something totally unforeseen")
        assertTrue(f is SyncFailure.Unexpected)
        assertFalse(f.retryable)
    }

    @Test
    fun `null message does not crash and maps to unexpected`() {
        val f = SyncFailure.from(RuntimeException())
        assertTrue(f is SyncFailure.Unexpected)
    }

    @Test
    fun `display labels follow the online flag for pending`() {
        assertEquals("Menunggu koneksi", SyncStatus.PENDING.displayLabel(isOnline = false))
        assertEquals("Tersimpan di perangkat", SyncStatus.PENDING.displayLabel(isOnline = true))
        assertEquals("Sedang disinkronkan", SyncStatus.SYNCING.displayLabel(true))
        assertEquals("Tersinkronisasi ke server", SyncStatus.SYNCED.displayLabel(true))
        assertEquals("Gagal, akan dicoba kembali", SyncStatus.FAILED_RETRYABLE.displayLabel(true))
        assertEquals(
            "Perlu pemeriksaan administrator",
            SyncStatus.FAILED_PERMANENT.displayLabel(true),
        )
        assertEquals(
            "Perlu pemeriksaan administrator",
            SyncStatus.NEEDS_REVIEW.displayLabel(false),
        )
    }

    @Test
    fun `snapshot hasWork is true only when there is pending or failed work`() {
        assertFalse(SyncSnapshot().hasWork)
        assertTrue(SyncSnapshot(pendingSessions = 1).hasWork)
        assertTrue(SyncSnapshot(failedCount = 1).hasWork)
        assertFalse(SyncSnapshot(pendingTrials = 5).hasWork)
    }
}

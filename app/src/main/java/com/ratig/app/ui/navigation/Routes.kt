package com.ratig.app.ui.navigation

/**
 * All navigation destinations. Route args are documented inline.
 * Screen composables referenced by RatigNavHost MUST exist with the exact
 * package + signature listed in docs/architecture.md (feature contract).
 */
object Routes {
    const val SPLASH = "splash"
    const val SETUP = "setup"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val PENDING_APPROVAL = "pending_approval"
    const val CONFIG_ERROR = "config_error"

    const val DASHBOARD = "dashboard"

    const val WORKERS = "workers"
    const val WORKER_EDIT = "worker_edit?workerId={workerId}"   // nullable query arg
    const val WORKER_DETAIL = "worker_detail/{workerId}"        // workerId

    const val TEST_INSTRUCTIONS = "test_instructions/{sessionId}"   // sessionId
    const val REACTION_TEST = "reaction_test/{sessionId}"           // sessionId
    const val MODE_TEST = "mode_test/{sessionId}"                   // sessionId
    const val ALL_MODES_TEST = "all_modes_test/{sessionId}"          // one session, 4 modes
    const val TEST_RESULT = "test_result/{sessionId}?durationMs={durationMs}"
    const val HEALTH_SCREENING = "health_screening/{sessionId}"     // sessionId

    const val HISTORY = "history"
    const val SESSION_DETAIL = "session_detail/{sessionId}"     // sessionId

    const val FOLLOW_UPS = "follow_ups"
    const val SCHEDULES = "schedules"
    const val SHIFTS = "shifts"
    const val REPORTS = "reports"
    const val PROFILE = "profile"

    const val ADMIN_USERS = "admin_users"
    const val ADMIN_PROTOCOLS = "admin_protocols"
    const val ADMIN_PROTOCOL_EDIT = "admin_protocol_edit?protocolId={protocolId}"
    const val ADMIN_RULES = "admin_rules"
    const val ADMIN_AUDIT = "admin_audit"
    const val ADMIN_MASTER_DATA = "admin_master_data"

    const val IDENTIFICATION = "identification"
    const val SYNC_STATUS = "sync_status"

    fun workerDetail(workerId: String) = "worker_detail/$workerId"
    // Always include the query key: the destination pattern contains it, so a
    // bare "worker_edit" would not match at all. Empty value == new worker.
    fun workerEdit(workerId: String?) = "worker_edit?workerId=${workerId.orEmpty()}"
    fun protocolEdit(protocolId: String?) =
        "admin_protocol_edit?protocolId=${protocolId.orEmpty()}"
    fun testInstructions(sessionId: String) = "test_instructions/$sessionId"
    fun reactionTest(sessionId: String) = "reaction_test/$sessionId"
    fun modeTest(sessionId: String) = "mode_test/$sessionId"
    fun allModesTest(sessionId: String) = "all_modes_test/$sessionId"
    fun testResult(sessionId: String, durationMs: Long = -1L) =
        "test_result/$sessionId?durationMs=$durationMs"
    fun healthScreening(sessionId: String) = "health_screening/$sessionId"
    fun sessionDetail(sessionId: String) = "session_detail/$sessionId"
}

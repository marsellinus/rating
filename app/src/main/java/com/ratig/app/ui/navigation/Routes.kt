package com.ratig.app.ui.navigation

/**
 * All navigation destinations. Route args are documented inline.
 * Screen composables referenced by RatigNavHost MUST exist with the exact
 * package + signature listed in docs/architecture.md (feature contract).
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val PENDING_APPROVAL = "pending_approval"
    const val CONFIG_ERROR = "config_error"

    const val DASHBOARD = "dashboard"

    const val WORKERS = "workers"
    const val WORKER_EDIT = "worker_edit"                       // ?workerId
    const val WORKER_DETAIL = "worker_detail/{workerId}"        // workerId

    const val TEST_INSTRUCTIONS = "test_instructions/{sessionId}"   // sessionId
    const val REACTION_TEST = "reaction_test/{sessionId}"           // sessionId
    const val MODE_TEST = "mode_test/{sessionId}"                   // sessionId
    const val TEST_RESULT = "test_result/{sessionId}"               // sessionId

    const val HISTORY = "history"
    const val SESSION_DETAIL = "session_detail/{sessionId}"     // sessionId

    const val FOLLOW_UPS = "follow_ups"
    const val SCHEDULES = "schedules"
    const val SHIFTS = "shifts"
    const val REPORTS = "reports"
    const val PROFILE = "profile"

    const val ADMIN_USERS = "admin_users"
    const val ADMIN_PROTOCOLS = "admin_protocols"
    const val ADMIN_PROTOCOL_EDIT = "admin_protocol_edit"       // ?protocolId
    const val ADMIN_RULES = "admin_rules"
    const val ADMIN_AUDIT = "admin_audit"
    const val ADMIN_MASTER_DATA = "admin_master_data"

    const val IDENTIFICATION = "identification"
    const val SYNC_STATUS = "sync_status"

    fun workerDetail(workerId: String) = "worker_detail/$workerId"
    fun workerEdit(workerId: String?) =
        if (workerId != null) "worker_edit?workerId=$workerId" else "worker_edit"
    fun protocolEdit(protocolId: String?) =
        if (protocolId != null) "admin_protocol_edit?protocolId=$protocolId" else "admin_protocol_edit"
    fun testInstructions(sessionId: String) = "test_instructions/$sessionId"
    fun reactionTest(sessionId: String) = "reaction_test/$sessionId"
    fun modeTest(sessionId: String) = "mode_test/$sessionId"
    fun testResult(sessionId: String) = "test_result/$sessionId"
    fun sessionDetail(sessionId: String) = "session_detail/$sessionId"
}

package com.ratig.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.SessionState

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

/**
 * Root app scaffold + navigation graph.
 * Bottom destinations and admin menu entries are gated by the user's role,
 * which comes from the server-verified profile - never from client input.
 */
@Composable
fun RatigNavHost(
    navController: NavHostController,
    sessionState: SessionState,
) {
    val role = (sessionState as? SessionState.Authenticated)?.profile?.role
    val bottomDests = buildList {
        add(BottomDest(Routes.DASHBOARD, "Beranda", Icons.Outlined.Home))
        if (role != null) {
            add(BottomDest(Routes.WORKERS, "Pekerja", Icons.Outlined.People))
        }
        add(BottomDest(Routes.HISTORY, "Riwayat", Icons.Outlined.History))
        add(BottomDest(Routes.PROFILE, "Profil", Icons.Outlined.Person))
    }

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val showBar = bottomDests.any { it.route == currentRoute }
            if (showBar) {
                NavigationBar {
                    bottomDests.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SPLASH) {
                com.ratig.app.feature.splash.SplashRoute(
                    onGoLogin = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                    onGoPending = { navController.navigate(Routes.PENDING_APPROVAL) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                    onGoHome = { navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                    onGoConfigError = { navController.navigate(Routes.CONFIG_ERROR) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                )
            }

            composable(Routes.CONFIG_ERROR) {
                com.ratig.app.feature.splash.ConfigErrorRoute(
                    onRetry = { navController.navigate(Routes.SPLASH) { popUpTo(Routes.CONFIG_ERROR) { inclusive = true } } },
                )
            }

            composable(Routes.LOGIN) {
                com.ratig.app.feature.auth.LoginRoute(
                    onSignedIn = { navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                    onPending = { navController.navigate(Routes.PENDING_APPROVAL) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                )
            }

            composable(Routes.PENDING_APPROVAL) {
                com.ratig.app.feature.auth.PendingApprovalRoute(
                    onApproved = { navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.PENDING_APPROVAL) { inclusive = true } } },
                    onSignOut = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.PENDING_APPROVAL) { inclusive = true } } },
                )
            }

            composable(Routes.DASHBOARD) {
                com.ratig.app.feature.dashboard.DashboardRoute(
                    onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                    onOpenWorker = { navController.navigate(Routes.workerDetail(it)) },
                    onOpenAdmin = { route -> navController.navigate(route) },
                    onStartIdentification = { navController.navigate(Routes.IDENTIFICATION) },
                    onOpenSyncStatus = { navController.navigate(Routes.SYNC_STATUS) },
                )
            }

            composable(Routes.WORKERS) {
                com.ratig.app.feature.workers.WorkersRoute(
                    onOpenWorker = { navController.navigate(Routes.workerDetail(it)) },
                    onAddWorker = { navController.navigate(Routes.workerEdit(null)) },
                )
            }

            composable(
                Routes.WORKER_EDIT,
                arguments = listOf(navArgument("workerId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                com.ratig.app.feature.workers.WorkerEditRoute(
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                Routes.WORKER_DETAIL,
                arguments = listOf(navArgument("workerId") { type = NavType.StringType }),
            ) {
                com.ratig.app.feature.workers.WorkerDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { workerId -> navController.navigate(Routes.workerEdit(workerId)) },
                    onStartTest = { sessionId -> navController.navigate(Routes.testInstructions(sessionId)) },
                    onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                )
            }

            composable(
                Routes.TEST_INSTRUCTIONS,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                com.ratig.app.feature.testflow.TestInstructionsRoute(
                    onReady = { navController.navigate(Routes.reactionTest(it)) { popUpTo(Routes.DASHBOARD) { inclusive = false } } },
                    onAborted = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                )
            }

            composable(
                Routes.REACTION_TEST,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                com.ratig.app.feature.testflow.ReactionTestRoute(
                    onFinished = { navController.navigate(Routes.testResult(it)) { popUpTo(Routes.DASHBOARD) { inclusive = false } } },
                    onAborted = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                )
            }

            composable(
                Routes.TEST_RESULT,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                com.ratig.app.feature.testflow.TestResultRoute(
                    onDone = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                )
            }

            composable(Routes.HISTORY) {
                com.ratig.app.feature.history.HistoryRoute(
                    onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                )
            }

            composable(
                Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                com.ratig.app.feature.history.SessionDetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenWorker = { navController.navigate(Routes.workerDetail(it)) },
                )
            }

            composable(Routes.FOLLOW_UPS) {
                com.ratig.app.feature.followup.FollowUpsRoute(
                    onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                )
            }

            composable(Routes.SCHEDULES) {
                com.ratig.app.feature.schedules.SchedulesRoute(
                    onOpenWorker = { navController.navigate(Routes.workerDetail(it)) },
                    onManageShifts = { navController.navigate(Routes.SHIFTS) },
                )
            }

            composable(Routes.SHIFTS) {
                com.ratig.app.feature.schedules.ShiftsRoute(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.REPORTS) {
                com.ratig.app.feature.reports.ReportsRoute()
            }

            composable(Routes.PROFILE) {
                com.ratig.app.feature.profile.ProfileRoute(
                    onSignOut = { navController.navigate(Routes.LOGIN) { popUpTo(0) } },
                    onOpenAdmin = { route -> navController.navigate(route) },
                )
            }

            composable(Routes.ADMIN_USERS) {
                com.ratig.app.feature.admin.UsersRoute(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ADMIN_PROTOCOLS) {
                com.ratig.app.feature.admin.ProtocolsRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { protocolId -> navController.navigate(Routes.protocolEdit(protocolId)) },
                )
            }

            composable(
                Routes.ADMIN_PROTOCOL_EDIT,
                arguments = listOf(navArgument("protocolId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                com.ratig.app.feature.admin.ProtocolEditRoute(
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.ADMIN_RULES) {
                com.ratig.app.feature.admin.RulesRoute(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ADMIN_AUDIT) {
                com.ratig.app.feature.admin.AuditLogRoute(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ADMIN_MASTER_DATA) {
                com.ratig.app.feature.admin.MasterDataRoute(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.IDENTIFICATION) {
                com.ratig.app.feature.identification.IdentificationRoute(
                    onBack = { navController.popBackStack() },
                    onSessionReady = { navController.navigate(Routes.testInstructions(it)) },
                    onOpenWorkerEdit = { navController.navigate(Routes.workerEdit(null)) },
                )
            }

            composable(Routes.SYNC_STATUS) {
                com.ratig.app.feature.sync.SyncStatusRoute(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

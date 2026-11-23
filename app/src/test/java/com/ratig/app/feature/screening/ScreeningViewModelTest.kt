package com.ratig.app.feature.screening

import com.ratig.app.data.local.dao.HealthScreeningDao
import com.ratig.app.data.local.entity.LocalHealthScreeningEntity
import com.ratig.app.domain.model.FitnessRecommendation
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.SessionState
import com.ratig.app.domain.usecase.VitalsClassifier
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the screening screen's view model: field parsing, live findings,
 * persistence and reload. This is the wiring between the paramedic's numbers
 * and what the result screen reports, so it must not drift.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreeningViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeDao : HealthScreeningDao {
        val stored = mutableMapOf<String, LocalHealthScreeningEntity>()
        override suspend fun upsert(screening: LocalHealthScreeningEntity) {
            stored[screening.sessionLocalId] = screening
        }
        override suspend fun findBySession(sessionLocalId: String) = stored[sessionLocalId]
        override fun observeBySession(sessionLocalId: String): Flow<LocalHealthScreeningEntity?> =
            flowOf(stored[sessionLocalId])
    }

    private class FakeAuth(private val id: String = "examiner-1") : AuthRepository {
        override val sessionState = MutableStateFlow<SessionState>(
            SessionState.Authenticated(
                UserProfile(
                    id = id,
                    fullName = "Pemeriksa",
                    email = "examiner@ratig.test",
                    role = UserRole.USER,
                    accountStatus = AccountStatus.ACTIVE,
                )
            )
        )
        override val isGoogleAvailable: Boolean = false
        override suspend fun restoreSession() = AppResult.Success(Unit)
        override suspend fun signInWithGoogle(
            activityContext: android.content.Context,
        ) = AppResult.Success(Unit)
        override suspend fun signInWithPassword(
            email: String,
            password: String,
        ) = AppResult.Success(Unit)
        override suspend fun signOut() = AppResult.Success(Unit)
        override suspend fun updatePassword(newPassword: String) = AppResult.Success(Unit)
        override suspend fun resetPasswordForEmail(email: String) = AppResult.Success(Unit)
        override suspend fun refreshProfile() =
            AppResult.Success((sessionState.value as SessionState.Authenticated).profile)
    }

    private fun viewModel(dao: HealthScreeningDao, sessionId: String = "session-1") =
        ScreeningViewModel(
            dao = dao,
            classifier = VitalsClassifier(),
            authRepository = FakeAuth(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        )

    @Test
    fun `typing a high systolic value raises a finding and a recommendation`() = runTest(dispatcher) {
        val vm = viewModel(FakeDao())
        advanceUntilIdle()

        vm.onSystolic("185")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("185", state.systolic)
        assertTrue("severe systolic must produce a finding", state.assessment.hasFindings)
        assertEquals(VitalsClassifier.SEVERE, state.assessment.overallSeverity)
        assertEquals(FitnessRecommendation.NOT_FIT, state.recommendation)
    }

    @Test
    fun `normal values produce no finding and a fit recommendation`() = runTest(dispatcher) {
        val vm = viewModel(FakeDao())
        advanceUntilIdle()

        vm.onSystolic("120")
        vm.onDiastolic("80")
        vm.onPulse("72")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.assessment.hasFindings)
        assertEquals(VitalsClassifier.NORMAL, state.assessment.overallSeverity)
        assertEquals(FitnessRecommendation.FIT, state.recommendation)
    }

    @Test
    fun `non numeric input is rejected without corrupting the field`() = runTest(dispatcher) {
        val vm = viewModel(FakeDao())
        advanceUntilIdle()

        vm.onPulse("7a2x")
        advanceUntilIdle()

        assertEquals("72", vm.uiState.value.pulse)
    }

    @Test
    fun `alcohol accepts a comma decimal separator`() = runTest(dispatcher) {
        val vm = viewModel(FakeDao())
        advanceUntilIdle()

        vm.onAlcohol("0,09")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("0,09", state.alcohol)
        assertTrue("0,09 must be read as 0.09 and fail the limit", state.assessment.hasFindings)
    }

    @Test
    fun `save persists the screening and records the examiner`() = runTest(dispatcher) {
        val dao = FakeDao()
        val vm = viewModel(dao)
        advanceUntilIdle()

        vm.onSystolic("150")
        vm.onNote("Istirahat 15 menit")
        advanceUntilIdle()

        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertTrue("callback must fire", saved)
        val row = dao.stored["session-1"]
        assertNotNull(row)
        assertEquals(150, row!!.systolic)
        assertEquals("Istirahat 15 menit", row.note)
        assertEquals("examiner-1", row.recordedBy)
        assertEquals(FitnessRecommendation.FIT_WITH_NOTE.name, row.recommendation)
        assertTrue("findings must be persisted as text", row.findingsText!!.contains("Hipertensi Ringan"))
    }

    @Test
    fun `reopening the screen reloads the stored screening`() = runTest(dispatcher) {
        val dao = FakeDao()
        val first = viewModel(dao)
        advanceUntilIdle()
        first.onSystolic("185")
        first.onDiastolic("120")
        first.onPulse("125")
        first.save { }
        advanceUntilIdle()

        val second = viewModel(dao)
        advanceUntilIdle()

        val state = second.uiState.value
        assertEquals("185", state.systolic)
        assertEquals("120", state.diastolic)
        assertEquals("125", state.pulse)
        assertEquals(FitnessRecommendation.NOT_FIT, state.recommendation)
        assertEquals(VitalsClassifier.SEVERE, state.assessment.overallSeverity)
    }

    @Test
    fun `saving twice updates the same row instead of duplicating it`() = runTest(dispatcher) {
        val dao = FakeDao()
        val vm = viewModel(dao)
        advanceUntilIdle()

        vm.onSystolic("120")
        vm.save { }
        advanceUntilIdle()
        vm.onSystolic("190")
        vm.save { }
        advanceUntilIdle()

        assertEquals(1, dao.stored.size)
        assertEquals(190, dao.stored["session-1"]!!.systolic)
    }
}

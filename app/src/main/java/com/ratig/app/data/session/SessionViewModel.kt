package com.ratig.app.data.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * App-root view model exposing the server-verified session state used by
 * navigation. Restores a persisted session on creation.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val sessionState = authRepository.sessionState

    init {
        viewModelScope.launch {
            runCatching {
                if (sessionState.value is SessionState.Loading || sessionState.value is SessionState.Error) {
                    authRepository.restoreSession()
                }
            }
        }
    }
}

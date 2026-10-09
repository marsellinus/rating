package com.ratig.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.rememberNavController
import com.ratig.app.data.session.SessionViewModel
import com.ratig.app.domain.repository.SessionState
import com.ratig.app.ui.navigation.RatigNavHost
import com.ratig.app.ui.theme.RatigTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Single-activity app. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RatigTheme {
                val vm: SessionViewModel = hiltViewModel()
                val session by vm.sessionState.collectAsState()
                val navController = rememberNavController()
                RatigNavHost(navController = navController, sessionState = session)
            }
        }
    }
}

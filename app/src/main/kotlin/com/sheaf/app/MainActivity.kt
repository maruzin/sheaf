package com.sheaf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sheaf.app.navigation.Routes
import com.sheaf.app.navigation.SheafNavHost
import com.sheaf.core.ui.theme.SheafTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SheafApp() }
    }
}

@Composable
private fun SheafApp(appViewModel: AppViewModel = hiltViewModel()) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    SheafTheme(dynamicColor = state.dynamicColor) {
        if (state.loading) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val navController = rememberNavController()
            SheafNavHost(
                navController = navController,
                startDestination = if (state.onboardingDone) Routes.LIBRARY else Routes.ONBOARDING,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

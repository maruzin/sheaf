package com.sheaf.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
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
        val incoming = incomingPdfUri(intent)
        setContent { SheafApp(incomingUri = incoming) }
    }
}

/** Extracts a PDF uri from an "Open with" (VIEW) or share (SEND) intent, if present. */
private fun incomingPdfUri(intent: Intent?): String? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data?.toString()
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.toString()
        }
        else -> null
    }
}

@Composable
private fun SheafApp(incomingUri: String? = null, appViewModel: AppViewModel = hiltViewModel()) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(incomingUri) { if (incomingUri != null) appViewModel.openIncoming(incomingUri) }
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
            val pendingOpen by appViewModel.pendingOpenDoc.collectAsStateWithLifecycle()
            LaunchedEffect(pendingOpen) {
                pendingOpen?.let { id ->
                    appViewModel.consumePendingOpen()
                    navController.navigate(Routes.reader(id))
                }
            }
        }
    }
}

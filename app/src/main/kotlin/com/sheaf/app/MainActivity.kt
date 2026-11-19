package com.sheaf.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    @javax.inject.Inject lateinit var settings: com.sheaf.core.domain.repository.SettingsRepository

    // Each "Open with" is a new event, even for the same file: a plain uri string would be equal to
    // the last one, so opening the same file again (e.g. from Files) did nothing.
    private val incoming = mutableStateOf<Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process restore) still carries the original "Open with"
        // intent. It was already handled, so reading it again would import and open the file twice.
        if (savedInstanceState == null) incoming.value = incomingPdfUri(intent)?.let(::Incoming)
        unlockProForTesting(intent)
        setContent {
            val event by incoming
            SheafApp(incoming = event)
        }
    }

    // Handle "Open with" while the app is already running.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = incomingPdfUri(intent)?.let(::Incoming)
        unlockProForTesting(intent)
    }

    /**
     * QA only: debuggable builds (never the Play release, which is not debuggable) unlock Pro when
     * launched with the "sheaf.debug_pro" extra, so Pro flows can be tested on an emulator without
     * Play billing: `adb shell am start -n <app>/com.sheaf.app.MainActivity --ez sheaf.debug_pro true`.
     */
    private fun unlockProForTesting(intent: Intent?) {
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable || intent?.getBooleanExtra(DEBUG_PRO_EXTRA, false) != true) return
        lifecycleScope.launch { settings.setPro(true) }
    }

    private companion object {
        const val DEBUG_PRO_EXTRA = "sheaf.debug_pro"
    }
}

/** One incoming file. Not a data class on purpose: every intent is a distinct event. */
private class Incoming(val uri: String)

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
private fun SheafApp(incoming: Incoming? = null, appViewModel: AppViewModel = hiltViewModel()) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { incoming?.let { appViewModel.openIncoming(it.uri) } }
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
            // Decide the start screen once (saved across rotation). Recomputing it when onboarding
            // completes rebuilt the nav graph mid-navigation: the back stack was reset and the
            // onboarding -> library navigation pointed at a route that no longer existed (crash).
            val startDestination = rememberSaveable {
                if (state.onboardingDone) Routes.LIBRARY else Routes.ONBOARDING
            }
            SheafNavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            )
            val openError by appViewModel.openError.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(openError) {
                openError?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    appViewModel.consumeOpenError()
                }
            }
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

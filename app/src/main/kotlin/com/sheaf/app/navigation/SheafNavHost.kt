package com.sheaf.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheaf.feature.reader.ReaderViewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sheaf.app.onboarding.OnboardingScreen
import com.sheaf.app.settings.SettingsScreen
import com.sheaf.feature.reader.ReaderScreen
import com.sheaf.feature.reader.library.LibraryScreen
import com.sheaf.feature.reader.pages.PagesScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val READER = "reader/{documentId}"
    const val PAGES = "pages/{documentId}"
    fun reader(documentId: Long) = "reader/$documentId"
    fun pages(documentId: Long) = "pages/$documentId"
}

@Composable
fun SheafNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.LIBRARY,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LIBRARY) {
            AdaptiveLibrary(
                onOpenReaderFullScreen = { id -> navController.navigate(Routes.reader(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onManagePages = { id -> navController.navigate(Routes.pages(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            ReaderScreen(
                documentId = documentId,
                onBack = { navController.popBackStack() },
                onManagePages = { id -> navController.navigate(Routes.pages(id)) },
                onOpenDocument = { id -> navController.navigate(Routes.reader(id)) },
                // Tabs replace the current reader so Back still returns to the library.
                onSwitchDocument = { id ->
                    navController.navigate(Routes.reader(id)) {
                        popUpTo(Routes.READER) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.PAGES,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            PagesScreen(
                documentId = documentId,
                onBack = { navController.popBackStack() },
                onSaved = { newId ->
                    navController.navigate(Routes.reader(newId)) {
                        popUpTo(Routes.PAGES) { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Width at which the library and reader sit side by side (Material "expanded" window class). */
private val TwoPaneMinWidth = 840.dp

/**
 * Phones and small tablets: the library alone (opening a document navigates to the reader).
 * Expanded widths (large tablets, unfolded foldables, desktop windows): library list + reader pane.
 */
@Composable
private fun AdaptiveLibrary(
    onOpenReaderFullScreen: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onManagePages: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < TwoPaneMinWidth) {
            LibraryScreen(onOpenDocument = onOpenReaderFullScreen, onOpenSettings = onOpenSettings)
        } else {
            var selected by rememberSaveable { mutableStateOf<Long?>(null) }
            Row(Modifier.fillMaxSize()) {
                LibraryScreen(
                    onOpenDocument = { selected = it },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                )
                VerticalDivider()
                val id = selected
                if (id == null) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select a document to read",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    key(id) {
                        ReaderScreen(
                            documentId = id,
                            onBack = { selected = null },
                            onManagePages = onManagePages,
                            onOpenDocument = { selected = it },
                            onSwitchDocument = { selected = it },
                            // One ViewModel per document in this pane (the nav entry is shared).
                            viewModel = hiltViewModel<ReaderViewModel>(key = "reader-pane-$id"),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

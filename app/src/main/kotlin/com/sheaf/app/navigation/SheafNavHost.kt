package com.sheaf.app.navigation

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
    startDestination: String = Routes.LIBRARY,
    modifier: Modifier = Modifier,
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
            LibraryScreen(
                onOpenDocument = { id -> navController.navigate(Routes.reader(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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

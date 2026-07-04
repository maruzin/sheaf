package com.sheaf.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sheaf.feature.reader.ReaderScreen
import com.sheaf.feature.reader.library.LibraryScreen

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{documentId}"
    fun reader(documentId: Long) = "reader/$documentId"
}

@Composable
fun SheafNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDocument = { id -> navController.navigate(Routes.reader(id)) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            ReaderScreen(
                documentId = documentId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

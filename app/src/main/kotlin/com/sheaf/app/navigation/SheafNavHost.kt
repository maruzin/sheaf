package com.sheaf.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/** Top-level navigation graph. Destinations are filled in as features land per milestone. */
object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{documentId}"
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
            // Library screen lands in M1.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sheaf — library (M1)")
            }
        }
    }
}

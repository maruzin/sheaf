package com.sheaf.feature.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder for the Pages feature. Implemented at its milestone
 * (see BUILD_NOTES.md feature-to-milestone map). Compiles today so the module graph is whole.
 */
@Composable
fun PagesScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pages — coming in its milestone")
    }
}

package com.sheaf.feature.billing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder for the Billing feature. Implemented at its milestone
 * (see BUILD_NOTES.md feature-to-milestone map). Compiles today so the module graph is whole.
 */
@Composable
fun BillingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Billing — coming in its milestone")
    }
}

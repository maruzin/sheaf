package com.sheaf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
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
    val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()
    SheafTheme(dynamicColor = dynamicColor) {
        val navController = rememberNavController()
        SheafNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

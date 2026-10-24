package com.sheaf.app.settings

import android.app.Activity

import android.widget.Toast
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val toastContext = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(toastContext, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val context = LocalContext.current
            ListItem(
                headlineContent = { Text(if (state.isPro) "Sheaf Pro — active" else "Sheaf Pro") },
                supportingContent = {
                    Text(
                        if (state.isPro) "Thanks for supporting Sheaf. All advanced tools are unlocked."
                        else "Unlock OCR, file compression, and password protection.",
                    )
                },
                trailingContent = {
                    if (state.isPro) {
                        TextButton(onClick = { viewModel.restore() }) { Text("Restore") }
                    } else {
                        TextButton(onClick = { (context as? Activity)?.let { viewModel.upgrade(it) } }) {
                            Text("Upgrade")
                        }
                    }
                },
            )
            if (!state.isPro) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { viewModel.restore() }) { Text("Restore purchase") }
                }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Material You colors") },
                supportingContent = { Text("Use your wallpaper palette instead of Ember") },
                trailingContent = {
                    Switch(
                        checked = state.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                },
            )
            HorizontalDivider()
            Text(
                text = "Default reading theme",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
            )
            Column(Modifier.selectableGroup()) {
            listOf("System", "Light", "Dark", "Sepia").forEach { theme ->
                // One selectable row per option so TalkBack reads "Sepia, radio button, selected".
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.defaultReaderTheme == theme,
                            onClick = { viewModel.setDefaultReaderTheme(theme) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.defaultReaderTheme == theme,
                        onClick = null,
                    )
                    Text(theme, modifier = Modifier.padding(start = 8.dp))
                }
            }
            }
        }
    }
}

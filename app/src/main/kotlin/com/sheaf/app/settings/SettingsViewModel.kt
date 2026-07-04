package com.sheaf.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val dynamicColor: Boolean = false,
    val defaultReaderTheme: String = "System",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(settings.dynamicColor, settings.defaultReaderTheme) { dyn, theme ->
            SettingsUiState(dynamicColor = dyn, defaultReaderTheme = theme)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setDefaultReaderTheme(theme: String) {
        viewModelScope.launch { settings.setDefaultReaderTheme(theme) }
    }
}

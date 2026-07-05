package com.sheaf.app.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.data.billing.BillingManager
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
    val isPro: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val billing: BillingManager,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(settings.dynamicColor, settings.defaultReaderTheme, settings.isPro) { dyn, theme, pro ->
            SettingsUiState(dynamicColor = dyn, defaultReaderTheme = theme, isPro = pro)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setDefaultReaderTheme(theme: String) {
        viewModelScope.launch { settings.setDefaultReaderTheme(theme) }
    }

    fun upgrade(activity: Activity) {
        viewModelScope.launch { runCatching { billing.purchasePro(activity) } }
    }

    fun restore() {
        viewModelScope.launch { runCatching { billing.restore() } }
    }
}

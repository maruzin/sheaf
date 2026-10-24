package com.sheaf.app.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.data.billing.BillingManager
import com.sheaf.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _message = MutableStateFlow<String?>(null)
    /** One-shot feedback for purchase / restore actions (shown as a toast, then consumed). */
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun upgrade(activity: Activity) {
        viewModelScope.launch {
            _message.value = runCatching { billing.purchasePro(activity) }.getOrElse { "Couldn't start the purchase" }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _message.value = when (runCatching { billing.restore() }.getOrNull()) {
                true -> "Sheaf Pro is active"
                false -> "No previous purchase found"
                null -> "Couldn't reach Google Play. Check your connection and try again."
            }
        }
    }
}

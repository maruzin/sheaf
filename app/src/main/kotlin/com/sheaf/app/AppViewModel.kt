package com.sheaf.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppState(
    val loading: Boolean = true,
    val dynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AppState> =
        combine(settings.dynamicColor, settings.onboardingComplete) { dyn, done ->
            AppState(loading = false, dynamicColor = dyn, onboardingDone = done)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState(loading = true))
}

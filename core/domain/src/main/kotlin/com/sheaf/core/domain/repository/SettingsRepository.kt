package com.sheaf.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** User preferences (appearance, reading defaults, first-run). Backed by DataStore in :core:data. */
interface SettingsRepository {
    val dynamicColor: Flow<Boolean>
    val defaultReaderTheme: Flow<String>
    val onboardingComplete: Flow<Boolean>
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setDefaultReaderTheme(theme: String)
    suspend fun setOnboardingComplete(complete: Boolean)
}

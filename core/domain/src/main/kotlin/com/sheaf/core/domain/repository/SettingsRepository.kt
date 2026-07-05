package com.sheaf.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** User preferences (appearance, reading defaults, first-run, saved signature). */
interface SettingsRepository {
    val dynamicColor: Flow<Boolean>
    val defaultReaderTheme: Flow<String>
    val onboardingComplete: Flow<Boolean>
    val signature: Flow<String>
    val isPro: Flow<Boolean>
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setDefaultReaderTheme(theme: String)
    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setSignature(encoded: String)
    suspend fun setPro(value: Boolean)
}

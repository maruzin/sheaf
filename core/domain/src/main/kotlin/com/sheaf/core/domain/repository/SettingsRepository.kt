package com.sheaf.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** User preferences (app appearance + reading defaults). Backed by DataStore in :core:data. */
interface SettingsRepository {
    val dynamicColor: Flow<Boolean>
    val defaultReaderTheme: Flow<String>
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setDefaultReaderTheme(theme: String)
}

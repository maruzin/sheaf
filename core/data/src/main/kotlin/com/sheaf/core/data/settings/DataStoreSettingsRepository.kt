package com.sheaf.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sheaf.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val dynamicColor: Flow<Boolean> =
        dataStore.data.map { it[KEY_DYNAMIC] ?: false }

    override val defaultReaderTheme: Flow<String> =
        dataStore.data.map { it[KEY_READER_THEME] ?: "System" }

    override val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDING] ?: false }

    override val signature: Flow<String> =
        dataStore.data.map { it[KEY_SIGNATURE] ?: "" }

    override val isPro: Flow<Boolean> =
        dataStore.data.map { it[KEY_PRO] ?: false }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC] = enabled }
    }

    override suspend fun setDefaultReaderTheme(theme: String) {
        dataStore.edit { it[KEY_READER_THEME] = theme }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = complete }
    }

    override suspend fun setSignature(encoded: String) {
        dataStore.edit { it[KEY_SIGNATURE] = encoded }
    }

    override suspend fun setPro(value: Boolean) {
        dataStore.edit { it[KEY_PRO] = value }
    }

    private companion object {
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_READER_THEME = stringPreferencesKey("default_reader_theme")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_SIGNATURE = stringPreferencesKey("signature")
        val KEY_PRO = booleanPreferencesKey("is_pro")
    }
}

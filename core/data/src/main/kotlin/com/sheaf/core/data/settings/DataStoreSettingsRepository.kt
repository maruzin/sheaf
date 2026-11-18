package com.sheaf.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sheaf.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    // The handwritten signature lives in noBackupFilesDir: personal data that must not go to cloud backup.
    @SignatureStore private val signatureStore: DataStore<Preferences>,
) : SettingsRepository {

    // A corrupt or unreadable preferences file must not crash every screen that collects a setting:
    // fall back to defaults instead.
    private val prefs = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val dynamicColor: Flow<Boolean> =
        prefs.map { it[KEY_DYNAMIC] ?: false }

    override val defaultReaderTheme: Flow<String> =
        prefs.map { it[KEY_READER_THEME] ?: "System" }

    override val onboardingComplete: Flow<Boolean> =
        prefs.map { it[KEY_ONBOARDING] ?: false }

    override val signature: Flow<String> =
        signatureStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_SIGNATURE] ?: "" }

    override val isPro: Flow<Boolean> =
        prefs.map { it[KEY_PRO] ?: false }

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
        signatureStore.edit { it[KEY_SIGNATURE] = encoded }
    }

    override suspend fun setPro(value: Boolean) {
        dataStore.edit { it[KEY_PRO] = value }
    }

    internal companion object {
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_READER_THEME = stringPreferencesKey("default_reader_theme")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_SIGNATURE = stringPreferencesKey("signature")
        val KEY_PRO = booleanPreferencesKey("is_pro")
    }
}

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

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC] = enabled }
    }

    override suspend fun setDefaultReaderTheme(theme: String) {
        dataStore.edit { it[KEY_READER_THEME] = theme }
    }

    private companion object {
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_READER_THEME = stringPreferencesKey("default_reader_theme")
    }
}

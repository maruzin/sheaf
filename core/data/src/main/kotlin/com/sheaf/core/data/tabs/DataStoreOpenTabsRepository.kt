package com.sheaf.core.data.tabs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sheaf.core.domain.tabs.OpenTabs
import com.sheaf.core.domain.tabs.OpenTabsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreOpenTabsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OpenTabsRepository {

    override val tabs: Flow<List<Long>> =
        dataStore.data.map { OpenTabs.decode(it[KEY_TABS]) }.distinctUntilChanged()

    override suspend fun open(documentId: Long) {
        dataStore.edit { it[KEY_TABS] = OpenTabs.encode(OpenTabs.open(OpenTabs.decode(it[KEY_TABS]), documentId)) }
    }

    override suspend fun close(documentId: Long) {
        dataStore.edit { it[KEY_TABS] = OpenTabs.encode(OpenTabs.close(OpenTabs.decode(it[KEY_TABS]), documentId)) }
    }

    private companion object {
        val KEY_TABS = stringPreferencesKey("open_tabs")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OpenTabsModule {
    @Binds
    abstract fun bind(impl: DataStoreOpenTabsRepository): OpenTabsRepository
}

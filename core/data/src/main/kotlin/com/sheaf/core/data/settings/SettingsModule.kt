package com.sheaf.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Qualifier
import com.sheaf.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {
    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    /**
     * The signature store, in noBackupFilesDir (never backed up or device-transferred). On first use it
     * moves a signature saved by older versions out of the backed-up settings file.
     */
    @Provides
    @Singleton
    @SignatureStore
    fun provideSignatureStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val settings = context.settingsDataStore
        val key = DataStoreSettingsRepository.KEY_SIGNATURE
        return PreferenceDataStoreFactory.create(
            migrations = listOf(
                object : DataMigration<Preferences> {
                    // A corrupt legacy settings file must not take the signature store down with it.
                    private suspend fun legacy(): String? = runCatching { settings.data.first()[key] }.getOrNull()

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                        currentData[key] == null && legacy() != null

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        val legacy = legacy() ?: return currentData
                        return currentData.toMutablePreferences().apply { this[key] = legacy }
                    }

                    override suspend fun cleanUp() {
                        runCatching { settings.edit { it.remove(key) } }
                    }
                },
            ),
            produceFile = { File(context.noBackupFilesDir, "signature.preferences_pb") },
        )
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SignatureStore

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindingModule {
    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}

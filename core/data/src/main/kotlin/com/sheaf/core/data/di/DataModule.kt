package com.sheaf.core.data.di

import android.content.Context
import androidx.room.Room
import com.sheaf.core.data.db.DocumentDao
import com.sheaf.core.data.db.SheafDatabase
import com.sheaf.core.data.repository.DefaultDocumentRepository
import com.sheaf.core.domain.repository.DocumentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SheafDatabase =
        Room.databaseBuilder(context, SheafDatabase::class.java, "sheaf.db").build()

    @Provides
    fun provideDocumentDao(db: SheafDatabase): DocumentDao = db.documentDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindDocumentRepository(impl: DefaultDocumentRepository): DocumentRepository
}

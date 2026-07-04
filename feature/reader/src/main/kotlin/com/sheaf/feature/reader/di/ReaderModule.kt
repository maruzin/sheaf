package com.sheaf.feature.reader.di

import com.sheaf.feature.reader.render.AndroidPdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object ReaderDispatcherModule {
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderBindingModule {
    @Binds
    abstract fun bindRenderFactory(impl: AndroidPdfRenderSource.Factory): PdfRenderSourceFactory
}

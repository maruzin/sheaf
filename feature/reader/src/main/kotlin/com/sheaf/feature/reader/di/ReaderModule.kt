package com.sheaf.feature.reader.di

import com.sheaf.feature.reader.compress.PdfBoxCompressor
import com.sheaf.feature.reader.compress.PdfCompressor
import com.sheaf.feature.reader.forms.PdfBoxFormReader
import com.sheaf.feature.reader.forms.PdfFormReader
import com.sheaf.feature.reader.pages.PdfBoxPageEditor
import com.sheaf.feature.reader.pages.PdfPageEditor
import com.sheaf.feature.reader.security.PdfBoxSecurity
import com.sheaf.feature.reader.security.PdfSecurity
import com.sheaf.feature.reader.render.AndroidPdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import com.sheaf.feature.reader.search.PdfBoxOutlineExtractor
import com.sheaf.feature.reader.search.PdfBoxTextSearcher
import com.sheaf.feature.reader.search.PdfOutlineExtractor
import com.sheaf.feature.reader.search.PdfTextSearcher
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

    @Binds
    abstract fun bindTextSearcher(impl: PdfBoxTextSearcher): PdfTextSearcher

    @Binds
    abstract fun bindOutlineExtractor(impl: PdfBoxOutlineExtractor): PdfOutlineExtractor

    @Binds
    abstract fun bindFormReader(impl: PdfBoxFormReader): PdfFormReader

    @Binds
    abstract fun bindPageEditor(impl: PdfBoxPageEditor): PdfPageEditor

    @Binds
    abstract fun bindSecurity(impl: PdfBoxSecurity): PdfSecurity

    @Binds
    abstract fun bindCompressor(impl: PdfBoxCompressor): PdfCompressor
}

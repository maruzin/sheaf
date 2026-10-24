package com.sheaf.feature.reader

import androidx.lifecycle.ViewModelStore
import com.sheaf.core.data.billing.BillingManager
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.model.OutlineEntry
import com.sheaf.core.domain.model.ReadingPosition
import com.sheaf.core.domain.repository.AnnotationRepository
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.core.domain.repository.SettingsRepository
import com.sheaf.core.domain.tabs.OpenTabs
import com.sheaf.core.domain.tabs.OpenTabsRepository
import com.sheaf.feature.reader.compress.PdfCompressor
import com.sheaf.feature.reader.flatten.AnnotationFlattener
import com.sheaf.feature.reader.forms.PdfFormReader
import com.sheaf.feature.reader.ocr.PdfOcr
import com.sheaf.feature.reader.render.PdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import com.sheaf.feature.reader.search.PdfOutlineExtractor
import com.sheaf.feature.reader.search.PdfTextSearcher
import com.sheaf.feature.reader.security.PdfSecurity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: the ViewModel builds file:// Uris (android.net.Uri), which plain JVM tests stub out.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private val doc = Document(1, "content://docs/a.pdf", "a.pdf", 10, 3, null, 0)
    private val protectedDoc = Document(2, "content://docs/locked.pdf", "locked.pdf", 10, 1, null, 0)

    private val repository = FakeDocumentRepository(listOf(doc, protectedDoc))
    private val renderFactory = FakeRenderFactory()
    private val searcher = FakeSearcher(listOf("hello world", "nothing", "Hello again"))
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val security = mockk<PdfSecurity>(relaxed = true)
    private val tabs = FakeTabs()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { settings.signature } returns flowOf("")
        coEvery { settings.isPro } returns flowOf(false)
        coEvery { settings.defaultReaderTheme } returns flowOf("Sepia")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): ReaderViewModel {
        val outline = mockk<PdfOutlineExtractor>()
        coEvery { outline.outline(any()) } returns emptyList()
        val forms = mockk<PdfFormReader>()
        coEvery { forms.readFields(any()) } returns emptyList()
        val annotations = mockk<AnnotationRepository>(relaxed = true)
        coEvery { annotations.observeForDocument(any()) } returns flowOf(emptyList())
        return ReaderViewModel(
            repository = repository,
            renderFactory = renderFactory,
            searcher = searcher,
            outlineExtractor = outline,
            annotationRepo = annotations,
            settings = settings,
            formReader = forms,
            security = security,
            compressor = mockk<PdfCompressor>(relaxed = true),
            ocr = mockk<PdfOcr>(relaxed = true),
            billing = mockk<BillingManager>(relaxed = true),
            flattener = mockk<AnnotationFlattener>(relaxed = true),
            openTabs = tabs,
        )
    }

    @Test
    fun `opening loads the document, registers a tab and marks it opened`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(3, s.pageCount)
        assertEquals("a.pdf", s.displayName)
        assertEquals(listOf(1L), tabs.ids.value)
        assertEquals(listOf(1L), repository.opened)
    }

    @Test
    fun `re-opening the same document (rotation) keeps the live page instead of reloading`() = runTest {
        repository.positions[1] = ReadingPosition(1, 0, 1f, 0f, 0f)
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        vm.onEvent(ReaderEvent.PageChanged(2))
        vm.onEvent(ReaderEvent.Open(1)) // what the screen sends again after a configuration change
        assertEquals(1, renderFactory.opens)
        assertEquals(2, vm.state.value.currentPage)
    }

    @Test
    fun `default reading theme from settings is applied`() = runTest {
        val vm = viewModel()
        assertEquals(ReaderTheme.Sepia, vm.state.value.theme)
        vm.onEvent(ReaderEvent.SetTheme(ReaderTheme.Dark))
        assertEquals(ReaderTheme.Dark, vm.state.value.theme)
    }

    @Test
    fun `search extracts text once and reuses it`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        vm.onEvent(ReaderEvent.Search("hello"))
        assertEquals(listOf(0, 2), vm.state.value.searchResults.map { it.pageIndex })
        vm.onEvent(ReaderEvent.NextResult)
        assertEquals(2, vm.state.value.currentPage)
        vm.onEvent(ReaderEvent.Search("again"))
        assertEquals(listOf(2), vm.state.value.searchResults.map { it.pageIndex })
        assertEquals(1, searcher.extractions)
    }

    @Test
    fun `reading mode builds paragraphs and flags scans without text`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        vm.onEvent(ReaderEvent.ToggleReflow)
        val s = vm.state.value
        assertTrue(s.reflow)
        assertFalse(s.reflowLoading)
        assertEquals(listOf("hello world"), s.reflowPages[0])
        assertFalse(s.textLayerMissing)

        searcher.pages = listOf("", "")
        val scan = viewModel()
        scan.onEvent(ReaderEvent.Open(1))
        scan.onEvent(ReaderEvent.ToggleReflow)
        assertTrue(scan.state.value.textLayerMissing)
    }

    @Test
    fun `reflow text size is clamped`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.SetReflowScale(5f))
        assertEquals(2f, vm.state.value.reflowScale)
        vm.onEvent(ReaderEvent.SetReflowScale(0.1f))
        assertEquals(0.8f, vm.state.value.reflowScale)
    }

    @Test
    fun `protected document asks for a password, rejects a wrong one and opens the unlocked copy`() = runTest {
        val unlocked = tmp.newFile("unlocked.pdf").apply { writeText("%PDF") }
        renderFactory.protectedUris += protectedDoc.uri
        coEvery { security.decrypt(protectedDoc.uri, "wrong") } returns null
        coEvery { security.decrypt(protectedDoc.uri, "secret") } returns unlocked.absolutePath

        val store = ViewModelStore()
        val vm = viewModel()
        store.put("reader", vm)
        vm.onEvent(ReaderEvent.Open(2))
        assertTrue(vm.state.value.needsPassword)

        vm.onEvent(ReaderEvent.SubmitOpenPassword("wrong"))
        assertTrue(vm.state.value.needsPassword)
        assertEquals("Incorrect password — try again", vm.state.value.passwordError)

        vm.onEvent(ReaderEvent.SubmitOpenPassword("secret"))
        val s = vm.state.value
        assertFalse(s.needsPassword)
        assertNull(s.passwordError)
        assertTrue(s.workingUri.endsWith("unlocked.pdf"))
        assertEquals(protectedDoc.uri, s.uri)

        // The plaintext copy is deleted when the reader goes away.
        store.clear()
        assertFalse(unlocked.exists())
    }

    @Test
    fun `closing a tab removes it`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        vm.onEvent(ReaderEvent.CloseTab(1))
        assertTrue(tabs.ids.value.isEmpty())
    }

    @Test
    fun `reading position is saved even when the ViewModel is cleared`() = runTest {
        val store = ViewModelStore()
        val vm = viewModel()
        store.put("reader", vm)
        vm.onEvent(ReaderEvent.Open(1))
        vm.onEvent(ReaderEvent.PageChanged(2))
        store.clear()
        // persistScope runs on Dispatchers.IO; wait briefly for the write.
        repeat(50) { if (repository.positions[1] == null) Thread.sleep(20) }
        assertEquals(2, repository.positions[1]?.pageIndex)
    }

    @Test
    fun `reopening scrolls back to the saved page`() = runTest {
        repository.positions[1] = ReadingPosition(1, 2, 1.5f, 0f, 0f)
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        assertEquals(2, vm.state.value.currentPage)
        assertEquals(2, vm.state.value.pendingScrollPage)
        assertEquals(1.5f, vm.state.value.zoom)
    }

    @Test
    fun `a saved page beyond the document is ignored`() = runTest {
        repository.positions[1] = ReadingPosition(1, 99, 1f, 0f, 0f)
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        assertEquals(0, vm.state.value.currentPage)
        assertNull(vm.state.value.pendingScrollPage)
    }

    @Test
    fun `cancelling a password prompt never overwrites the saved position`() = runTest {
        repository.positions[2] = ReadingPosition(2, 7, 1f, 0f, 0f)
        renderFactory.protectedUris += protectedDoc.uri
        val store = ViewModelStore()
        val vm = viewModel()
        store.put("reader", vm)
        vm.onEvent(ReaderEvent.Open(2))
        assertTrue(vm.state.value.needsPassword)
        vm.onEvent(ReaderEvent.CancelOpenPassword)
        vm.persistPosition()
        store.clear()
        Thread.sleep(200)
        assertEquals(7, repository.positions[2]?.pageIndex)
    }

    @Test
    fun `a protected document imported with 0 pages gets its page count after unlocking`() = runTest {
        val unlocked = tmp.newFile("unlocked.pdf").apply { writeText("%PDF") }
        repository.upsert(protectedDoc.copy(pageCount = 0))
        renderFactory.protectedUris += protectedDoc.uri
        coEvery { security.decrypt(protectedDoc.uri, "secret") } returns unlocked.absolutePath
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(2))
        vm.onEvent(ReaderEvent.SubmitOpenPassword("secret"))
        assertEquals(1, repository.document(2)?.pageCount)
    }

    @Test
    fun `release frees the session and a later open reloads`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(1))
        vm.release()
        vm.onEvent(ReaderEvent.Open(1))
        assertEquals(2, renderFactory.opens)
    }

    @Test
    fun `import counts pages and imports protected files with 0`() = runTest {
        assertEquals(3, com.sheaf.feature.reader.library.countPages(renderFactory, "content://docs/a.pdf"))
        renderFactory.protectedUris += "content://docs/locked.pdf"
        assertEquals(0, com.sheaf.feature.reader.library.countPages(renderFactory, "content://docs/locked.pdf"))
    }

    @Test
    fun `unreadable document shows an error`() = runTest {
        val vm = viewModel()
        vm.onEvent(ReaderEvent.Open(42))
        assertEquals("Document not found", vm.state.value.error)
        coVerify(exactly = 0) { security.decrypt(any(), any()) }
    }
}

private class FakeDocumentRepository(docs: List<Document>) : DocumentRepository {
    private val byId = docs.associateBy { it.id }.toMutableMap()
    val positions = java.util.concurrent.ConcurrentHashMap<Long, ReadingPosition>()
    val opened = mutableListOf<Long>()
    override fun observeRecents(limit: Int) = flowOf(byId.values.toList())
    override fun observeBookmarked() = flowOf(emptyList<Document>())
    override suspend fun document(id: Long) = byId[id]
    override suspend fun findByUri(uri: String) = byId.values.firstOrNull { it.uri == uri }
    override suspend fun upsert(document: Document): Long = document.id.also { byId[it] = document }
    override suspend fun setBookmarked(documentId: Long, bookmarked: Boolean) = Unit
    override suspend fun saveReadingPosition(position: ReadingPosition) { positions[position.documentId] = position }
    override suspend fun readingPosition(documentId: Long) = positions[documentId]
    override suspend fun findByNameAndSize(displayName: String, sizeBytes: Long) =
        byId.values.firstOrNull { it.displayName == displayName && it.sizeBytes == sizeBytes }
    override suspend fun markOpened(documentId: Long) { opened += documentId }
    override suspend fun setPageCount(documentId: Long, pageCount: Int) {
        byId[documentId]?.let { byId[documentId] = it.copy(pageCount = pageCount) }
    }
    override suspend fun delete(documentId: Long) { byId.remove(documentId) }
}

private class FakeRenderFactory : PdfRenderSourceFactory {
    var opens = 0
    val protectedUris = mutableSetOf<String>()
    override suspend fun open(uri: String, password: String?): PdfRenderSource {
        if (uri in protectedUris) throw SecurityException("password required")
        opens++
        return object : PdfRenderSource {
            override val pageCount = if (uri.endsWith("unlocked.pdf")) 1 else 3
            override suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int) =
                error("not used in unit tests")
            override fun pageAspectRatio(pageIndex: Int) = 1.4f
            override suspend fun outline() = emptyList<OutlineEntry>()
            override fun close() = Unit
        }
    }
}

private class FakeSearcher(var pages: List<String>) : PdfTextSearcher {
    var extractions = 0
    override suspend fun pageTexts(uri: String): List<String> {
        extractions++
        return pages
    }
}

private class FakeTabs : OpenTabsRepository {
    val ids = MutableStateFlow(emptyList<Long>())
    override val tabs = ids
    override suspend fun open(documentId: Long) { ids.value = OpenTabs.open(ids.value, documentId) }
    override suspend fun close(documentId: Long) { ids.value = OpenTabs.close(ids.value, documentId) }
}

package app.aroma.feature.editor

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import app.aroma.data.settings.AppSettings
import app.aroma.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddRecipeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val env = TestEnvironment()
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }
    private val screen = CoroutineScope(Dispatchers.Main)

    /** The time the test's clock shows, in milliseconds. */
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        screen.cancel()
        env.close()
    }

    @Test
    fun `a shared web link is imported at once, but not again when Android brings the screen back`() {
        val saved = SavedStateHandle()
        assertTrue(open(saved, sharedUrl = LINK).state.value.isImporting)

        val restored = open(saved, sharedUrl = LINK)
        assertFalse(restored.state.value.isImporting)
        assertEquals(LINK, restored.state.value.input)
    }

    @Test
    fun `shared text that is no web link waits for the import button`() {
        val shared = open(sharedUrl = "intent://scan#Intent;end")
        assertFalse(shared.state.value.isImporting)
        assertEquals("intent://scan#Intent;end", shared.state.value.input)
    }

    @Test
    fun `a recipe's text shared to Aroma fills the field and waits for the import button`() {
        val vm = open(sharedText = CAPTION)
        vm.state.launchIn(screen)

        assertEquals(CAPTION, vm.state.value.input)
        assertFalse(vm.state.value.isImporting)
        assertTrue(vm.state.value.canImport)
    }

    @Test
    fun `imports are read with AI and not translated, until the user chooses otherwise for the next ones too`() {
        // Watched rather than read at once: a choice is saved on the main thread, which the test's waiting keeps turning.
        var stored: AppSettings? = null
        settings.settings.onEach { stored = it }.launchIn(screen)
        val first = open()
        first.state.launchIn(screen)
        assertTrue(first.state.value.useAi)
        assertFalse(first.state.value.translate)

        first.onUseAiChange(false)
        first.onTranslateChange(true)
        awaitUntil { stored?.let { !it.importWithAi && it.translateImports } == true }

        val next = open()
        next.state.launchIn(screen)
        awaitUntil { !next.state.value.useAi && next.state.value.translate }
    }

    @Test
    fun `a link just copied is offered while the field has none, and pasting it fills the field`() {
        val vm = open()
        vm.state.launchIn(screen)

        vm.onClipboardChange(Copied(isLink = true, at = now - 1.minutes.inWholeMilliseconds))
        assertEquals(PasteOffer.Link, vm.shown.pasteOffer)

        vm.paste(" $LINK\n")
        assertEquals(LINK, vm.shown.input)
        assertNull("the link is in the field now", vm.shown.pasteOffer)
    }

    @Test
    fun `copied text is offered beside a link alone, and pasted below it`() {
        val vm = open()
        vm.state.launchIn(screen)
        vm.onInputChange(LINK)

        vm.onClipboardChange(Copied(isLink = true, at = now))
        assertNull("a second link has no place", vm.shown.pasteOffer)

        vm.onClipboardChange(Copied(isLink = false, at = now))
        assertEquals(PasteOffer.Text, vm.shown.pasteOffer)

        vm.paste(CAPTION)
        assertEquals("$LINK\n\n$CAPTION", vm.shown.input)
        assertEquals(ImportInput(LINK, CAPTION), ImportInput.of(vm.shown.input))
        assertNull(vm.shown.pasteOffer)
    }

    @Test
    fun `a clip copied long ago, or pasted already, is not offered`() {
        val vm = open()
        vm.state.launchIn(screen)

        vm.onClipboardChange(Copied(isLink = true, at = now - 30.minutes.inWholeMilliseconds))
        assertNull(vm.shown.pasteOffer)

        val copied = Copied(isLink = true, at = now)
        vm.onClipboardChange(copied)
        assertEquals(PasteOffer.Link, vm.shown.pasteOffer)
        vm.paste(LINK)
        vm.onInputChange("")
        // Coming back to the screen tells the same clip again.
        vm.onClipboardChange(copied)
        assertNull(vm.shown.pasteOffer)
    }

    @Test
    fun `nothing is offered while an import runs`() {
        val vm = open(sharedUrl = LINK)
        vm.state.launchIn(screen)

        vm.onClipboardChange(Copied(isLink = false, at = now))

        assertTrue(vm.shown.isImporting)
        assertNull("a caption would go below the link, but the import has started", vm.shown.pasteOffer)
        assertEquals("but it is there for the next import", PasteOffer.Text, vm.shown.copied)
    }

    private fun open(
        saved: SavedStateHandle = SavedStateHandle(),
        sharedUrl: String? = null,
        sharedText: String? = null,
    ): AddRecipeViewModel {
        val importer = RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" })
        val imports = RecipeImports(ApplicationProvider.getApplicationContext(), env.recipes, importer)
        return AddRecipeViewModel(env.recipes, imports, settings, sharedUrl, shownJob = null, saved, sharedText, clock = { now })
    }

    /** The view model's state as the screen shows it, once its work on the main thread is done. */
    private val AddRecipeViewModel.shown: AddRecipeUiState
        get() {
            shadowOf(Looper.getMainLooper()).idle()
            return state.value
        }

    /** Lets the view model's work on the main thread and in the background go on until [done]. */
    private fun awaitUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private companion object {
        const val LINK = "https://example.com/lauchpuffer"
        const val CAPTION = "Lauchpuffer\n\nZutaten:\n2 Stangen Lauch\n2 Eier"
    }
}

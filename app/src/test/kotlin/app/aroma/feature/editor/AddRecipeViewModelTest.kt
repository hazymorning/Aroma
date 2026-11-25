package app.aroma.feature.editor

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddRecipeViewModelTest {
    private val env = TestEnvironment()
    private val screen = CoroutineScope(Dispatchers.Main)
    private val opened = mutableListOf<AddRecipeViewModel>()

    /** The time the test's clock shows, in milliseconds. */
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        // What the screens started would otherwise go on in the next test.
        opened.forEach { it.viewModelScope.cancel() }
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
    fun `a recipe's text, or anything else but a web link, shared to Aroma fills the field and waits for the import button`() {
        val text = open(sharedText = CAPTION)
        text.state.launchIn(screen)
        assertEquals(CAPTION, text.state.value.input)
        assertFalse(text.state.value.isImporting)
        assertTrue(text.state.value.canImport)

        val other = open(sharedUrl = "intent://scan#Intent;end")
        assertEquals("intent://scan#Intent;end", other.state.value.input)
        assertFalse(other.state.value.isImporting)
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

    private fun open(
        saved: SavedStateHandle = SavedStateHandle(),
        sharedUrl: String? = null,
        sharedText: String? = null,
    ): AddRecipeViewModel {
        val importer = RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" })
        val imports = RecipeImports(ApplicationProvider.getApplicationContext(), env.recipes, importer)
        return AddRecipeViewModel(env.recipes, imports, env.settings, sharedUrl, shownJob = null, saved, sharedText, clock = { now })
            .also(opened::add)
    }

    /** The view model's state as the screen shows it, once its work on the main thread is done. */
    private val AddRecipeViewModel.shown: AddRecipeUiState
        get() {
            shadowOf(Looper.getMainLooper()).idle()
            return state.value
        }

    private companion object {
        const val LINK = "https://example.com/lauchpuffer"
        const val CAPTION = "Lauchpuffer\n\nZutaten:\n2 Stangen Lauch\n2 Eier"
    }
}

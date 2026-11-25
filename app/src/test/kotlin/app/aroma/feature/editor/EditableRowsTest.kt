package app.aroma.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import app.aroma.onNodeWithWords
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditableRowsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `removing a step above the one being edited leaves the cursor in it`() {
        var form by mutableStateOf(EditorForm(name = "Gnocchi", steps = listOf("Kochen.", "Braten.", "Servieren.")))
        compose.setContent {
            AromaTheme(darkTheme = false) {
                EditorScreen(EditorUiState(form = form, isLoading = false), EditorActions(onChange = { change -> form = change(form) }))
            }
        }
        compose.onNodeWithWords("Servieren.").performScrollTo().performClick()

        compose.onNodeWithContentDescription("Remove step 1").performScrollTo().performClick()

        compose.onNodeWithWords("Servieren.").assertIsFocused()
    }

    @Test
    fun `a step on its way out takes no second tap`() {
        var form by mutableStateOf(EditorForm(name = "Gnocchi", steps = listOf("Kochen.", "Braten.", "Servieren.")))
        compose.setContent {
            AromaTheme(darkTheme = false) {
                EditorScreen(EditorUiState(form = form, isLoading = false), EditorActions(onChange = { change -> form = change(form) }))
            }
        }
        val remove = compose.onNodeWithContentDescription("Remove step 1").performScrollTo()
        val place = remove.fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        remove.performClick()
        compose.mainClock.advanceTimeByFrame()

        // The first step still fades where it stood; a tap on its button there does nothing more.
        compose.onRoot().performTouchInput { click(place) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(listOf("Braten.", "Servieren."), form.steps)
    }
}

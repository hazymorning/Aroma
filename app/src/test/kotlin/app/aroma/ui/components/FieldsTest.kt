package app.aroma.ui.components

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A field of one line at the reader's size of text: as tall as a button where its line fits, and its
 * button's circle as far from the field's end as from its top and foot at any size. It shows only
 * with real fonts, which the tests draw with (robolectric.properties).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class FieldsTest(private val fontScale: Float) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun showPassword() {
        RuntimeEnvironment.setQualifiers("de-w360dp-h800dp-xxhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            AromaTheme(darkTheme = false) {
                LabeledSecureTextField(
                    state = rememberTextFieldState("geheim"),
                    label = "Passwort",
                    fieldModifier = Modifier.testTag(FIELD),
                    trailingIcon = { IconButton(R.drawable.ic_visibility, "Zeigen", onClick = {}, modifier = Modifier.testTag(BUTTON)) },
                )
            }
        }
    }

    @Test
    fun `a field is a button's height until its line no longer fits`() {
        val field = compose.onNodeWithTag(FIELD).getBoundsInRoot()
        val height = (field.bottom - field.top).value

        if (fontScale <= 1.5f) {
            assertEquals(ButtonHeight.value, height, HALF_DP)
        } else {
            assertTrue("at $fontScale the field grows rather than crowd its line", height >= ButtonHeight.value)
        }
    }

    @Test
    fun `a field's button keeps as much air at the end as above and below`() {
        val field = compose.onNodeWithTag(FIELD).getBoundsInRoot()
        val button = compose.onNodeWithTag(BUTTON).getBoundsInRoot()
        val end = (field.right - button.right).value

        assertEquals((button.top - field.top).value, end, HALF_DP)
        assertEquals((field.bottom - button.bottom).value, end, HALF_DP)
    }

    companion object {
        private const val FIELD = "field"
        private const val BUTTON = "button"
        private const val HALF_DP = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "font at {0}")
        fun scales() = listOf(1f, 1.15f, 1.3f, 1.5f, 2f)
    }
}

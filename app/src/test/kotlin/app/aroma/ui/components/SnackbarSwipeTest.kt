package app.aroma.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SnackbarSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a snackbar is swiped away to either side`() {
        val state = SnackbarHostState()
        compose.setContent { AromaSnackbarHost(state) }
        listOf("Zur Einkaufsliste hinzugefügt", "Eingeplant").forEachIndexed { index, message ->
            var result: SnackbarResult? = null
            CoroutineScope(Dispatchers.Main).launch { result = state.showSnackbar(message, duration = SnackbarDuration.Indefinite) }
            compose.waitForIdle()
            compose.onNodeWithText(message).performTouchInput {
                // A thumb's swipe, wider than the short message text it starts on.
                if (index ==
                    0
                ) {
                    swipeLeft(startX = centerX, endX = centerX - SWIPE)
                } else {
                    swipeRight(startX = centerX, endX = centerX + SWIPE)
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText(message).assertDoesNotExist()
            assertEquals(SnackbarResult.Dismissed, result)
        }
    }

    private companion object {
        const val SWIPE = 600f
    }
}

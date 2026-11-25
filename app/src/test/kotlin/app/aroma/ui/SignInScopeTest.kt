package app.aroma.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignInScopeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a screen's view model ends with the sign-in it was made for`() {
        var signIn by mutableStateOf<String?>("mara")
        val made = linkedSetOf<Probe>()
        compose.setContent {
            signIn?.let { current ->
                key(current) {
                    SignInScope(current) {
                        NavHost(rememberNavController(), startDestination = "home") {
                            composable("home") { made += viewModel { Probe() } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val first = made.single()
        assertFalse(first.cleared)

        signIn = "jo"
        compose.waitForIdle()
        assertTrue(first.cleared)
        assertEquals(2, made.size)
        val second = made.last()
        assertFalse(second.cleared)

        signIn = null
        compose.waitForIdle()
        assertTrue(second.cleared)
    }

    class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }
}

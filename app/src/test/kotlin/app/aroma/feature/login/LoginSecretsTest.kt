package app.aroma.feature.login

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextInput
import app.aroma.core.model.ServerInfo
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginSecretsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a typed password is not written into the saved state`() {
        val registry = SaveableStateRegistry(restoredValues = null) { true }
        compose.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                LoginScreen(
                    state =
                        LoginUiState(
                            step = LoginStep.Credentials,
                            server = ServerInfo("https://mealie.example.com", "3.31.0", allowPasswordLogin = true),
                        ),
                    actions = LoginActions(),
                )
            }
        }

        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("geheim")

        val saved = registry.performSave().values.flatten()
        assertFalse(saved.joinToString().contains("geheim"))
    }
}

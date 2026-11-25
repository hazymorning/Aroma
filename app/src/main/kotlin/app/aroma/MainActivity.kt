package app.aroma

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.aroma.data.session.SessionState
import app.aroma.ui.SignInScope
import app.aroma.ui.navigation.AromaNavHost
import app.aroma.ui.navigation.LaunchRequest
import app.aroma.ui.navigation.LoginDestination
import app.aroma.ui.navigation.Shortcut
import app.aroma.ui.theme.AromaActivityTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val launch = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as AromaApplication).container
        splash.setKeepOnScreenCondition { container.sessions.state.value == SessionState.Loading || container.appSettings.value == null }
        if (savedInstanceState == null) launch.value = LaunchRequest.from(intent)
        // AppCompat has read the language chosen by now, also one chosen before Aroma kept it itself.
        lifecycleScope.launch { container.settings.keepLanguage() }
        Shortcut.publish(this)
        container.updates.checkAtStart {
            container.settings.settings
                .first()
                .checkUpdatesAtStart
        }
        // Only while the app is in view does Aroma keep asking a server out of reach whether it answers again.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { container.connection.askWhileInView() } }

        setContent {
            val settings = container.appSettings.collectAsStateWithLifecycle().value ?: return@setContent
            val session by container.sessions.state.collectAsStateWithLifecycle()
            val pendingLaunch by launch.collectAsStateWithLifecycle()
            AromaActivityTheme(settings) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val current = session) {
                        SessionState.Loading -> {}

                        SessionState.SignedOut -> {
                            LoginDestination()
                        }

                        is SessionState.SignedIn -> {
                            key(current.session.serverUrl, current.session.userId) {
                                SignInScope(current.session.serverUrl to current.session.userId) {
                                    AromaNavHost(launch = pendingLaunch, onLaunchConsume = { launch.value = null })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LaunchRequest.from(intent)?.let { launch.value = it }
    }
}

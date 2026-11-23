package app.aroma

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aroma.data.session.SessionState
import app.aroma.data.settings.ThemeMode
import app.aroma.data.sync.OfflineRecipesWorker
import app.aroma.ui.navigation.AromaNavHost
import app.aroma.ui.navigation.LaunchRequest
import app.aroma.ui.navigation.LoginDestination
import app.aroma.ui.navigation.Shortcut
import app.aroma.ui.theme.AromaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class MainActivity : AppCompatActivity() {
    private val launch = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as AromaApplication).container
        splash.setKeepOnScreenCondition { container.sessions.state.value == SessionState.Loading || container.appSettings.value == null }
        if (savedInstanceState == null) launch.value = LaunchRequest.from(intent)
        Shortcut.publish(this)
        container.updates.checkAtStart {
            container.settings.settings
                .first()
                .checkUpdatesAtStart
        }

        setContent {
            val settings = container.appSettings.collectAsStateWithLifecycle().value ?: return@setContent
            val session by container.sessions.state.collectAsStateWithLifecycle()
            val pendingLaunch by launch.collectAsStateWithLifecycle()
            val dark =
                when (settings.themeMode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LaunchedEffect(session, settings.offlineRecipes) {
                if (session is SessionState.SignedIn &&
                    settings.offlineRecipes
                ) {
                    OfflineRecipesWorker.schedule(applicationContext, enabled = true)
                }
            }
            AromaTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val current = session) {
                        SessionState.Loading -> {}

                        SessionState.SignedOut -> {
                            LoginDestination()
                        }

                        is SessionState.SignedIn -> {
                            key(current.session.serverUrl, current.session.userId) {
                                AromaNavHost(launch = pendingLaunch, onLaunchConsume = { launch.value = null })
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

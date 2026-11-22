package app.kochi

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
import app.kochi.data.session.SessionState
import app.kochi.data.settings.AppSettings
import app.kochi.data.settings.ThemeMode
import app.kochi.data.sync.OfflineRecipesWorker
import app.kochi.ui.navigation.KochiNavHost
import app.kochi.ui.navigation.LoginDestination
import app.kochi.ui.theme.KochiTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    private val sharedUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as KochiApplication).container
        splash.setKeepOnScreenCondition { container.sessions.state.value == SessionState.Loading }
        if (savedInstanceState == null) readSharedUrl(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val session by container.sessions.state.collectAsStateWithLifecycle()
            val pendingUrl by sharedUrl.collectAsStateWithLifecycle()
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
            KochiTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val current = session) {
                        SessionState.Loading -> {}

                        SessionState.SignedOut -> {
                            LoginDestination()
                        }

                        is SessionState.SignedIn -> {
                            key(current.session.serverUrl, current.session.userId) {
                                KochiNavHost(sharedUrl = pendingUrl, onSharedUrlConsume = { sharedUrl.value = null })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSharedUrl(intent)
    }

    /** Links shared from a browser ("Share" > Kochi) open the import screen. */
    private fun readSharedUrl(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        sharedUrl.value = Regex("""https?://\S+""").find(text)?.value
    }
}

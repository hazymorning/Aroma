package app.aroma

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import app.aroma.data.JsonCache
import app.aroma.data.attempt
import app.aroma.data.local.AromaDatabase
import app.aroma.data.network.ConnectivityNetworkMonitor
import app.aroma.data.network.ServerConnection
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.remote.SocialPosts
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.repository.TimelineRepository
import app.aroma.data.session.KeystoreCipher
import app.aroma.data.session.SessionManager
import app.aroma.data.session.SessionState
import app.aroma.data.session.SessionStore
import app.aroma.data.settings.AppSettings
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.settings.inAppLanguage
import app.aroma.data.sync.OfflineRecipesWorker
import app.aroma.data.sync.ShoppingSyncWorker
import app.aroma.data.update.Updates
import app.aroma.feature.editor.RecipeImporter
import app.aroma.feature.editor.RecipeImports
import app.aroma.feature.editor.RecipeReworker
import app.aroma.feature.editor.RecipeReworks
import app.aroma.feature.editor.forgetCameraPhotos
import app.aroma.timer.CookingTimers
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Creates and holds the app's long-lived objects. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // A file that cannot be read any more starts over empty, which signs out, rather than crashing every start.
    private val dataStore =
        PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }) {
            appContext.preferencesDataStoreFile("aroma")
        }
    private val database = Room.databaseBuilder(appContext, AromaDatabase::class.java, DATABASE).build()

    val settings = SettingsRepository(dataStore)

    /** The settings, held from the start, so the first frame already has the right theme; null until read. */
    val appSettings: StateFlow<AppSettings?> = settings.settings.stateIn(scope, SharingStarted.Eagerly, null)
    val ratingReminder = RatingReminder(dataStore)

    /** How Aroma names itself: to Mealie, and to a platform whose embed shows a post only to apps, not to browsers. */
    private val appUserAgent = "Aroma/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"

    // The client reads the session, and tells the connection what became of a request, only when it
    // sends one, after all three exist.
    val clients: MealieClientFactory =
        MealieClientFactory(appUserAgent, answers = { if (it) connection.answered() else connection.unanswered() }) {
            sessions.credentials()
        }
    val sessions: SessionManager = SessionManager(SessionStore(dataStore, KeystoreCipher()), clients)

    /** Whether Mealie answers, as the phone's networks and every request tell. */
    val connection = ServerConnection(ConnectivityNetworkMonitor(appContext), scope) { sessions.api().about() }
    private val cache = JsonCache(database.cache(), connection, sessions)

    val auth = AuthRepository(clients, sessions, database, deviceName = Build.MODEL, forgetOutsideDatabase = ::forgetAccount)
    val recipes = RecipeRepository(sessions, database.recipes(), cache, connection)
    val mealPlans = MealPlanRepository(sessions, cache)
    val timeline = TimelineRepository(sessions)
    val shopping: ShoppingRepository =
        ShoppingRepository(sessions, database.cache(), cache, database.pendingChanges(), connection, ::syncShopping)
    val timers = CookingTimers(appContext, scope)
    val updates = Updates(clients.httpClient, BuildConfig.VERSION_NAME, appContext.cacheDir.resolve("update"), scope, appContext::isAroma)

    /** The phone's own browser, as social media platforms show link previews to browsers. */
    private val browserUserAgent by lazy {
        try {
            WebSettings.getDefaultUserAgent(appContext)
        } catch (_: RuntimeException) {
            // No web view installed, or one being updated.
            System.getProperty("http.agent").orEmpty()
        }
    }

    val imports =
        RecipeImports(
            appContext,
            recipes,
            RecipeImporter(recipes, SocialPosts(clients.httpClient, appUserAgent) { browserUserAgent }, untitled = {
                appContext.inAppLanguage().getString(R.string.add_untitled)
            }),
        )

    val reworks = RecipeReworks(appContext, RecipeReworker(recipes, resources = { appContext.inAppLanguage().resources }))

    init {
        // Another account's server is taken to answer until a request finds otherwise.
        scope.launch {
            sessions.state
                .map { (it as? SessionState.SignedIn)?.session?.serverUrl }
                .distinctUntilChanged()
                .drop(1)
                .collect { connection.reset() }
        }
        // Changes made to a shopping list while Mealie was out of reach go out as soon as it answers.
        scope.launch {
            connection.isReachable
                .drop(1)
                .filter { it }
                .collect { syncShopping() }
        }
    }

    /** Sends the shopping list changes waiting for Mealie, or has Android send them once it answers. */
    private fun syncShopping() {
        // Out of reach, a try would only wait out the connect timeout on every edit, and a sync queued anew
        // would start over at once; Mealie answering again sends the changes anyway.
        if (!connection.isReachable.value) {
            ShoppingSyncWorker.enqueue(appContext, ExistingWorkPolicy.KEEP)
            return
        }
        scope.launch {
            val done = attempt { shopping.sync() }.getOrDefault(false)
            if (!done) ShoppingSyncWorker.enqueue(appContext)
        }
    }

    /**
     * Reads the session kept from the last run. Without one, what an account may have left is removed:
     * a sign-out the app was ended during, or a session that could not be read any more, would leave
     * it to the next account.
     */
    fun restore() =
        scope.launch {
            sessions.restore()
            // Every account keeps something in the database: without its file, none was ever here.
            if (sessions.current == null && appContext.getDatabasePath(DATABASE).exists()) auth.forgetLocal()
        }

    /**
     * Signs out and removes the account's traces from this device; see [forgetAccount].
     * Runs in the app's scope: the screen that asked goes away as soon as the session ends.
     */
    fun signOut() = scope.launch { auth.signOut() }

    /**
     * Removes what an account leaves on this device besides the database, as the sign-out dialog
     * promises: offline copies, imports and their photos, timers, notifications, cached photos and
     * the rating reminder. [AuthRepository.forgetLocal] runs it with the database once the session
     * has ended, when no widget loads a photo any more.
     */
    private suspend fun forgetAccount() {
        OfflineRecipesWorker.schedule(appContext, enabled = false)
        timers.dismissAll()
        settings.forgetAccount()
        ratingReminder.clear()
        withContext(Dispatchers.IO) {
            imports.cancelAll()
            reworks.forgetAll()
            forgetCameraPhotos(appContext)
            SingletonImageLoader.get(appContext).run {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
        // Import results and finished timers name the account's recipes.
        NotificationManagerCompat.from(appContext).cancelAll()
    }
}

private const val DATABASE = "aroma.db"

/** Whether [apk] is this app; Android's installer then checks that it carries the same signing key. */
@Suppress("DEPRECATION") // The flags as an Int still work; the replacement needs Android 13.
private fun Context.isAroma(apk: File): Boolean = packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName == packageName

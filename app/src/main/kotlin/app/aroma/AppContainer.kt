package app.aroma

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.aroma.data.JsonCache
import app.aroma.data.attempt
import app.aroma.data.local.AromaDatabase
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
import app.aroma.timer.CookingTimers
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val database = Room.databaseBuilder(appContext, AromaDatabase::class.java, "aroma.db").build()
    private val cache = JsonCache(database.cache())

    val settings = SettingsRepository(dataStore)

    /** The settings, held from the start, so the first frame already has the right theme; null until read. */
    val appSettings: StateFlow<AppSettings?> = settings.settings.stateIn(scope, SharingStarted.Eagerly, null)
    val ratingReminder = RatingReminder(dataStore)

    /** How Aroma names itself: to Mealie, and to a platform whose embed shows a post only to apps, not to browsers. */
    private val appUserAgent = "Aroma/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"

    // The client reads the session only when it sends a request, after both exist.
    val clients: MealieClientFactory =
        MealieClientFactory(appUserAgent) { sessions.credentials() }
    val sessions: SessionManager = SessionManager(SessionStore(dataStore, KeystoreCipher()), clients)

    val auth = AuthRepository(clients, sessions, database, deviceName = Build.MODEL)
    val recipes = RecipeRepository(sessions, database.recipes(), cache)
    val mealPlans = MealPlanRepository(sessions, cache)
    val timeline = TimelineRepository(sessions, recipes)
    val shopping: ShoppingRepository =
        ShoppingRepository(sessions, database.cache(), cache, database.pendingChanges()) {
            scope.launch {
                val done = attempt { shopping.sync() }.getOrDefault(false)
                if (!done) ShoppingSyncWorker.enqueue(appContext)
            }
        }
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

    /**
     * Signs out and removes the account's traces from this device, as the sign-out dialog
     * promises: cached recipes and photos, offline copies, timers and the rating reminder.
     * Runs in the app's scope: the screen that asked goes away as soon as the session ends.
     */
    fun signOut() =
        scope.launch {
            OfflineRecipesWorker.schedule(appContext, enabled = false)
            imports.cancelAll()
            reworks.forgetAll()
            timers.dismissAll()
            settings.forgetAccount()
            ratingReminder.clear()
            withContext(Dispatchers.IO) {
                SingletonImageLoader.get(appContext).run {
                    memoryCache?.clear()
                    diskCache?.clear()
                }
            }
            auth.signOut()
        }
}

/** Whether [apk] is this app; Android's installer then checks that it carries the same signing key. */
@Suppress("DEPRECATION") // The flags as an Int still work; the replacement needs Android 13.
private fun Context.isAroma(apk: File): Boolean = packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName == packageName

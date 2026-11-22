package app.kochi

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.kochi.data.JsonCache
import app.kochi.data.local.KochiDatabase
import app.kochi.data.remote.MealieClientFactory
import app.kochi.data.repository.AuthRepository
import app.kochi.data.repository.MealPlanRepository
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.repository.ShoppingRepository
import app.kochi.data.session.KeystoreCipher
import app.kochi.data.session.SessionManager
import app.kochi.data.session.SessionStore
import app.kochi.data.settings.SettingsRepository
import app.kochi.data.sync.ShoppingSyncWorker
import app.kochi.timer.CookingTimers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Creates and holds the app's long-lived objects. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("kochi") }
    private val database = Room.databaseBuilder(appContext, KochiDatabase::class.java, "kochi.db").build()
    private val cache = JsonCache(database.cache())

    val settings = SettingsRepository(dataStore)
    val sessions = SessionManager(SessionStore(dataStore, KeystoreCipher()))

    val clients =
        MealieClientFactory(
            userAgent = "Kochi/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
            cache = null,
            credentials = { sessions.credentials() },
        ).also { sessions.clientFactory = it }

    val auth = AuthRepository(clients, sessions, database, deviceName = Build.MODEL)
    val recipes = RecipeRepository(sessions, database.recipes(), cache)
    val mealPlans = MealPlanRepository(sessions, cache)
    val shopping: ShoppingRepository =
        ShoppingRepository(sessions, database.cache(), cache, database.pendingChanges()) {
            scope.launch {
                val done = runCatching { shopping.sync() }.getOrDefault(false)
                if (!done) ShoppingSyncWorker.enqueue(appContext)
            }
        }
    val timers = CookingTimers(appContext, scope)
}

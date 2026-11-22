package app.aroma

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.aroma.data.JsonCache
import app.aroma.data.local.AromaDatabase
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.session.KeystoreCipher
import app.aroma.data.session.SessionManager
import app.aroma.data.session.SessionStore
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.sync.ShoppingSyncWorker
import app.aroma.timer.CookingTimers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Creates and holds the app's long-lived objects. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("aroma") }
    private val database = Room.databaseBuilder(appContext, AromaDatabase::class.java, "aroma.db").build()
    private val cache = JsonCache(database.cache())

    val settings = SettingsRepository(dataStore)
    val sessions = SessionManager(SessionStore(dataStore, KeystoreCipher()))

    val clients =
        MealieClientFactory(
            userAgent = "Aroma/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
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

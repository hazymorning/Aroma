package app.aroma.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aroma.data.local.AromaDatabase
import app.aroma.data.network.NetworkMonitor
import app.aroma.data.network.ServerConnection
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.session.SecretCipher
import app.aroma.data.session.SessionManager
import app.aroma.data.session.SessionStore
import app.aroma.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID

/** The real data layer wired up for tests, with an in-memory database and no Keystore. */
class TestEnvironment {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStoreFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")

    val database: AromaDatabase =
        Room
            .inMemoryDatabaseBuilder(context, AromaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The phone's network, on unless a test takes it away. */
    val network = TestNetwork()

    val clients: MealieClientFactory =
        MealieClientFactory(
            "Aroma-Test",
            answers = { if (it) connection.answered() else connection.unanswered() },
        ) { sessions.credentials() }
    val sessions: SessionManager = SessionManager(SessionStore(PreferenceDataStoreFactory.create { dataStoreFile }, PlainCipher), clients)
    val connection = ServerConnection(network, scope) { sessions.api().about() }

    private val cache = JsonCache(database.cache(), connection, sessions)
    val auth = AuthRepository(clients, sessions, database, deviceName = "Test")
    val recipes = RecipeRepository(sessions, database.recipes(), cache, connection)
    val mealPlans = MealPlanRepository(sessions, cache)
    var syncRequests = 0
    val shopping = ShoppingRepository(sessions, database.cache(), cache, database.pendingChanges(), connection) { syncRequests++ }

    private val settingsFile = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    val settings = SettingsRepository(PreferenceDataStoreFactory.create { settingsFile })

    fun close() {
        scope.cancel()
        database.close()
        dataStoreFile.delete()
        settingsFile.delete()
    }

    /** A network that is there or not as a test says; [online] set to true again counts as another one joining. */
    class TestNetwork : NetworkMonitor {
        override val isOnline = MutableStateFlow(true)
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain

        override fun decrypt(data: ByteArray) = data
    }
}

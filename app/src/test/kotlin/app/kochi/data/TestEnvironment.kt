package app.kochi.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.kochi.data.local.KochiDatabase
import app.kochi.data.remote.MealieClientFactory
import app.kochi.data.repository.AuthRepository
import app.kochi.data.repository.MealPlanRepository
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.repository.ShoppingRepository
import app.kochi.data.session.SecretCipher
import app.kochi.data.session.SessionManager
import app.kochi.data.session.SessionStore
import java.io.File
import java.util.UUID

/** The real data layer wired up for tests, with an in-memory database and no Keystore. */
class TestEnvironment {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStoreFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")

    val database: KochiDatabase =
        Room
            .inMemoryDatabaseBuilder(context, KochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    val sessions = SessionManager(SessionStore(PreferenceDataStoreFactory.create { dataStoreFile }, PlainCipher))

    val clients =
        MealieClientFactory("Kochi-Test", cache = null, credentials = { sessions.credentials() })
            .also { sessions.clientFactory = it }

    private val cache = JsonCache(database.cache())
    val auth = AuthRepository(clients, sessions, database, deviceName = "Test")
    val recipes = RecipeRepository(sessions, database.recipes(), cache)
    val mealPlans = MealPlanRepository(sessions, cache)
    var syncRequests = 0
    val shopping = ShoppingRepository(sessions, database.cache(), cache, database.pendingChanges()) { syncRequests++ }

    fun close() {
        database.close()
        dataStoreFile.delete()
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain

        override fun decrypt(data: ByteArray) = data
    }
}

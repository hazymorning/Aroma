package app.aroma.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aroma.data.local.AromaDatabase
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.session.SecretCipher
import app.aroma.data.session.SessionManager
import app.aroma.data.session.SessionStore
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

    val clients: MealieClientFactory = MealieClientFactory("Aroma-Test") { sessions.credentials() }
    val sessions: SessionManager = SessionManager(SessionStore(PreferenceDataStoreFactory.create { dataStoreFile }, PlainCipher), clients)

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

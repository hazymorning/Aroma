package app.aroma.testing

import androidx.room.Room
import app.aroma.AppContainer
import app.aroma.AromaApplication
import app.aroma.data.local.AromaDatabase
import app.aroma.data.network.NetworkMonitor
import app.aroma.data.session.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.seconds

/**
 * The app as it starts on a phone, with stand-ins only for what a test cannot have: the Keystore,
 * the phone's network, which [network] switches, and a database file, kept in memory instead. A
 * request Mealie leaves unanswered gives up within seconds rather than a minute. Tests name it in
 * `@Config(application = TestApp::class)`.
 */
class TestApp : AromaApplication() {
    val network = MutableStateFlow(true)

    lateinit var database: AromaDatabase
        private set

    override fun createContainer(): AppContainer {
        database = Room.inMemoryDatabaseBuilder(this, AromaDatabase::class.java).build()
        val networks =
            object : NetworkMonitor {
                override val isOnline = network
            }
        return AppContainer(this, PlainCipher, networks, database, answerTimeout = 5.seconds)
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain

        override fun decrypt(data: ByteArray) = data
    }
}

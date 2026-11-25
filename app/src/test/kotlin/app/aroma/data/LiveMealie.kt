package app.aroma.data

import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.session.Session
import app.aroma.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/**
 * The real Mealie server the live tests run against, set by MEALIE_TEST_URL, MEALIE_TEST_USER and
 * MEALIE_TEST_PASSWORD; without them those tests are skipped:
 *
 *     MEALIE_TEST_URL=http://localhost:9000 MEALIE_TEST_USER=... MEALIE_TEST_PASSWORD=... \
 *         ./gradlew :app:testDebugUnitTest --tests 'app.aroma.data.Live*'
 *
 * The tests make and remove their own data, so an empty server is fine. A test class keeps one of
 * these and signs in once: its first test signs in, the others take up that session, and
 * [signOut] revokes its token once the class is done.
 */
class LiveMealie {
    private var session: Session? = null

    /** Signs [env] in to the server. */
    fun signIn(env: TestEnvironment) {
        runBlocking {
            val shared = session
            if (shared != null) {
                env.sessions.start(shared)
            } else {
                env.auth.signIn(env.auth.probe(host), user, password)
                session = env.sessions.current
            }
        }
    }

    /** Revokes the token the class signed in with, as signing out does. */
    fun signOut() {
        val ended = session ?: return
        session = null
        val tokenId = ended.tokenId ?: return
        runBlocking {
            MealieClientFactory("Aroma-Test") { null }
                .create(SessionManager.baseUrl(ended.serverUrl))
                .deleteApiToken(tokenId, "Bearer ${ended.token}")
        }
    }

    companion object {
        val url: String? = System.getenv("MEALIE_TEST_URL")?.takeIf { it.isNotBlank() }
        val user: String get() = System.getenv("MEALIE_TEST_USER").orEmpty()
        val password: String get() = System.getenv("MEALIE_TEST_PASSWORD").orEmpty()

        /** The server as a user types it, without the scheme Aroma finds itself. */
        val host: String get() = url.orEmpty().removePrefix("http://").removePrefix("https://")

        /** Skips the test where no server is set. */
        fun assumeConfigured() = assumeTrue("MEALIE_TEST_URL is not set", url != null)
    }
}

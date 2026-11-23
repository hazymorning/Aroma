package app.aroma.data

import app.aroma.data.session.Session
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineCopyTest {
    private val server = MockWebServer()
    private val fetched = CopyOnWriteArrayList<String>()
    private lateinit var env: TestEnvironment

    /** Slug to last change, as the server lists them. */
    @Volatile
    private var onServer = mapOf("gnocchi" to "2026-11-01T10:00:00", "linsensuppe" to "2026-11-01T10:00:00")

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    val slug = path.removePrefix("/api/recipes/")
                    return when {
                        path == "/api/recipes" -> json(listing())
                        onServer.containsKey(slug) -> json(recipe(slug, onServer.getValue(slug))).also { fetched += slug }
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
        server.start()
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.url("/").toString().trimEnd('/'), token = "t", userId = "u", username = "mara"))
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `the offline copy follows changes, additions and deletions on the server`() =
        runBlocking<Unit> {
            assertEquals(2, env.recipes.downloadAll())
            assertEquals(setOf("gnocchi", "linsensuppe"), fetched.toSet())

            fetched.clear()
            onServer = mapOf("gnocchi" to "2026-11-20T18:30:00", "ofenkuerbis" to "2026-11-20T18:30:00")
            assertEquals(2, env.recipes.downloadAll())

            assertEquals("only what changed or is new is fetched again", setOf("gnocchi", "ofenkuerbis"), fetched.toSet())
            assertNull("a recipe deleted on the server is not kept", env.database.recipes().detail("linsensuppe"))
            assertNotNull(env.database.recipes().detail("ofenkuerbis"))
        }

    private fun listing() =
        onServer.entries.joinToString(
            prefix = """{"page":1,"per_page":-1,"total":${onServer.size},"total_pages":1,"items":[""",
            postfix = "]}",
        ) {
            """{"id":"id-${it.key}","slug":"${it.key}","name":"${it.key}","updatedAt":"${it.value}"}"""
        }

    private fun recipe(slug: String, updatedAt: String) = """{"id":"id-$slug","slug":"$slug","name":"$slug","updatedAt":"$updatedAt"}"""

    private fun json(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

package app.aroma.data

import app.aroma.core.model.ServerInfo
import app.aroma.data.session.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignOutTest {
    private val server = MockWebServer()
    private val itemsSent = CopyOnWriteArrayList<String>()
    private lateinit var env: TestEnvironment
    private lateinit var url: String

    /** Set to hold back the answers to the shopping lists until it is counted down. */
    @Volatile
    private var held: CountDownLatch? = null
    private val asked = CountDownLatch(1)

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    val auth = request.headers["Authorization"].orEmpty()
                    return when {
                        request.method == "GET" && path.startsWith("/api/households/shopping/lists") -> {
                            held?.let {
                                asked.countDown()
                                it.await(5, TimeUnit.SECONDS)
                            }
                            json(if (path.endsWith("/$LIST")) LIST_JSON else """{"items":[$LIST_JSON]}""")
                        }

                        request.method == "GET" && path == "/api/users/self" -> {
                            json("""{"id":"user-b","username":"jonas"}""")
                        }

                        request.method == "POST" && path == "/api/households/shopping/items" -> {
                            itemsSent += auth
                            json("""{"createdItems":[],"updatedItems":[],"deletedItems":[]}""")
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
                }
            }
        server.start()
        env = TestEnvironment()
        url = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        held?.countDown()
        env.close()
        server.close()
    }

    @Test
    fun `signing in removes what a sign-out cut short left behind`() =
        runBlocking {
            env.sessions.start(session("token-a"))
            env.shopping.refresh(LIST)
            env.shopping.add(LIST, "Milch")
            // Android ended the app right after the session, before anything was removed.
            env.sessions.end()

            env.auth.signInWithToken(ServerInfo(url, "3.0.0", allowPasswordLogin = true), "token-b")

            assertNull(env.shopping.observe(LIST).first())
            assertEquals(0, env.shopping.pendingCount(LIST).first())
            assertTrue(env.shopping.sync())
            assertEquals("the item must not go to the next account", emptyList<String>(), itemsSent)
        }

    @Test
    fun `a list that arrives after the sign-out is not kept`() =
        runBlocking {
            env.sessions.start(session("token-a"))
            held = CountDownLatch(1)
            val refresh = async(Dispatchers.IO) { env.shopping.refresh(LIST) }

            signOutWhileHeld()
            refresh.await()

            assertNull(env.shopping.observe(LIST).first())
        }

    @Test
    fun `the lists that arrive after the sign-out are not kept`() =
        runBlocking {
            env.sessions.start(session("token-a"))
            held = CountDownLatch(1)
            val lists = async(Dispatchers.IO) { env.shopping.lists().last() }

            signOutWhileHeld()

            assertEquals(1, lists.await().data.size)
            assertNull(env.database.cache().get("shopping:lists"))
        }

    /** Signs out once the shopping lists were asked for, then lets Mealie's answer through. */
    private suspend fun signOutWhileHeld() {
        assertTrue(asked.await(5, TimeUnit.SECONDS))
        env.auth.signOut()
        held?.countDown()
    }

    private fun session(token: String, tokenId: Int? = null) =
        Session(serverUrl = url, token = token, tokenId = tokenId, userId = "user-a", username = "mara")

    private fun json(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private companion object {
        const val LIST = "list-1"
        const val LIST_JSON =
            """{"id":"$LIST","name":"Wocheneinkauf","listItems":[],"labelSettings":[],"recipeReferences":[]}"""
    }
}

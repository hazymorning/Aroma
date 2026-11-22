package app.kochi.data

import app.kochi.data.session.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShoppingSyncTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment

    @Volatile
    private var writesFail = false

    @Volatile
    private var serverItems = listOf(item("item-1", "2 Zitronen"))

    @Before
    fun setUp() =
        runBlocking {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests += request
                        val path = request.url.encodedPath
                        return when {
                            request.method != "GET" && writesFail -> {
                                MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()
                            }

                            request.method == "GET" && path.endsWith("/lists/$LIST") -> {
                                json(list())
                            }

                            request.method == "POST" && path.endsWith("/shopping/items") -> {
                                serverItems = serverItems + item("item-2", "Milch")
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
            val url = server.url("/").toString().trimEnd('/')
            env.sessions.start(Session(serverUrl = url, token = "token", userId = "user", username = "mara"))
            env.shopping.refresh(LIST)
        }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `an item added while offline stays local, survives a refresh and is sent once`() =
        runBlocking {
            writesFail = true
            env.shopping.add(LIST, "Milch")

            assertEquals(
                listOf("2 Zitronen", "Milch"),
                env.shopping
                    .observe(LIST)
                    .first()!!
                    .items
                    .map { it.note },
            )
            assertEquals(1, env.shopping.pendingCount(LIST).first())
            assertFalse(env.shopping.sync())

            env.shopping.refresh(LIST)
            assertEquals(
                "a refresh must not drop unsent changes",
                2,
                env.shopping
                    .observe(LIST)
                    .first()!!
                    .items.size,
            )

            writesFail = false
            assertTrue(env.shopping.sync())
            assertEquals(0, env.shopping.pendingCount(LIST).first())
            val posts = requests.filter { it.method == "POST" && it.url.encodedPath.endsWith("/shopping/items") }
            val body = posts.last().body!!.utf8()
            assertTrue(body.contains("\"note\":\"Milch\""))
            assertTrue("offline items get their id on the device", body.contains("\"id\":"))

            env.shopping.refresh(LIST)
            assertEquals(
                listOf("2 Zitronen", "Milch"),
                env.shopping
                    .observe(LIST)
                    .first()!!
                    .items
                    .map { it.note },
            )
        }

    @Test
    fun `requests to the server carry the token`() {
        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer token" })
    }

    private fun json(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun list() =
        """{"id":"$LIST","name":"Wocheneinkauf","listItems":[${serverItems.joinToString(",")}],"labelSettings":[],"recipeReferences":[]}"""

    private fun item(id: String, note: String) =
        """{"id":"$id","shoppingListId":"$LIST","quantity":0,"note":"$note","display":"$note",""" +
            """"checked":false,"position":0,"recipeReferences":[]}"""

    private companion object {
        const val LIST = "list-1"
    }
}

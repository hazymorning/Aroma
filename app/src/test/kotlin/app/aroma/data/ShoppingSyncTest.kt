package app.aroma.data

import app.aroma.data.session.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
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
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val env = TestEnvironment()

    @Volatile
    private var writesFail = false

    @Volatile
    private var serverItems = listOf(item("item-1", "2 Zitronen"))

    private val server =
        mealieServer { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                request.method != "GET" && writesFail -> {
                    MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()
                }

                request.method in setOf("GET", "PUT") && path.endsWith("/lists/$LIST") -> {
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

    @Before
    fun setUp() =
        runBlocking {
            env.sessions.start(Session(serverUrl = server.address, token = "token", userId = "user", username = "mara"))
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
    fun `renaming a list keeps the items that still wait to be sent`() =
        runBlocking {
            env.shopping.add(LIST, "Milch")
            env.shopping.renameList(LIST, "Markt")

            assertEquals(
                listOf("2 Zitronen", "Milch"),
                env.shopping
                    .observe(LIST)
                    .first()!!
                    .items
                    .map { it.note },
            )
        }

    private fun list() =
        """{"id":"$LIST","name":"Wocheneinkauf","listItems":[${serverItems.joinToString(",")}],"labelSettings":[],"recipeReferences":[]}"""

    private fun item(id: String, note: String) =
        """{"id":"$id","shoppingListId":"$LIST","quantity":0,"note":"$note","display":"$note",""" +
            """"checked":false,"position":0,"recipeReferences":[]}"""

    private companion object {
        const val LIST = "list-1"
    }
}

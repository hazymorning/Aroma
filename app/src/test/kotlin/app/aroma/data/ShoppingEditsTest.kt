package app.aroma.data

import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.data.session.Session
import app.aroma.testing.address
import app.aroma.testing.json
import app.aroma.testing.mealieServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/** The changes the shopping list makes beyond a single tick: all at once, in a new order, into an aisle. */
@RunWith(RobolectricTestRunner::class)
class ShoppingEditsTest {
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val env = TestEnvironment()

    @Volatile
    private var serverList = listJson()

    private val server =
        mealieServer { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                request.method == "GET" && path.endsWith("/lists/$LIST") -> json(serverList)
                request.method == "PUT" && path.endsWith("/lists/$LIST/label-settings") -> json(serverList)
                path.contains("/shopping/items") -> json("""{"createdItems":[],"updatedItems":[],"deletedItems":[]}""")
                else -> MockResponse.Builder().code(404).build()
            }
        }

    @Before
    fun setUp() =
        runBlocking {
            env.sessions.start(Session(serverUrl = server.address, token = "token", userId = "user", username = "mara"))
            env.shopping.refresh(LIST)
            requests.clear()
        }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    private suspend fun list(): ShoppingList = env.shopping.observe(LIST).first()!!

    private fun sent(method: String, pathEnd: String) = requests.filter { it.method == method && it.url.encodedPath.endsWith(pathEnd) }

    @Test
    fun `everything open is ticked off at once, and put back as it was`() =
        runBlocking {
            val changed = env.shopping.setChecked(LIST, list().items.map { it.id }, checked = true)

            assertEquals("the one ticked off already stays as it is", listOf("avocado", "milk", "bread"), changed)
            assertTrue(list().items.all { it.checked })
            assertTrue(env.shopping.sync())
            assertEquals(3, sent("PUT", "").count { it.url.encodedPath.contains("/shopping/items/") })
            assertTrue(
                sent("PUT", "/items/milk")
                    .single()
                    .body!!
                    .utf8()
                    .contains("\"checked\":true"),
            )

            env.shopping.setChecked(LIST, changed, checked = false)
            assertEquals(listOf(false, false, false, true), list().items.map { it.checked })
        }

    @Test
    fun `what was ticked off leaves the list, and comes back as it was`() =
        runBlocking {
            val removed = env.shopping.clearChecked(LIST)

            assertEquals(listOf("lemon"), removed.map { it["id"]!!.jsonPrimitive.content })
            assertEquals(listOf("avocado", "milk", "bread"), list().items.map { it.id })

            env.shopping.restore(LIST, removed)
            assertEquals(listOf("avocado", "milk", "bread", "lemon"), list().items.map { it.id })
            assertTrue(list().items.single { it.id == "lemon" }.checked)
            assertTrue(env.shopping.sync())
            assertEquals(1, sent("DELETE", "/items/lemon").size)
            assertTrue(
                sent("POST", "/shopping/items")
                    .single()
                    .body!!
                    .utf8()
                    .contains("\"id\":\"lemon\""),
            )
        }

    @Test
    fun `items dragged into a new order take the places they held, and only those that move are sent`() =
        runBlocking {
            // Shown as bread (2), avocado (4), milk (9); the milk is dragged to the top.
            env.shopping.reorder(LIST, listOf("milk", "bread", "avocado"))

            assertEquals(mapOf("avocado" to 9, "milk" to 2, "bread" to 4, "lemon" to 7), list().items.associate { it.id to it.position })
            assertTrue(env.shopping.sync())
            assertEquals(3, requests.count { it.method == "PUT" })

            requests.clear()
            env.shopping.reorder(LIST, listOf("milk", "avocado", "bread"))
            assertEquals(mapOf("avocado" to 4, "milk" to 2, "bread" to 9, "lemon" to 7), list().items.associate { it.id to it.position })
            assertTrue(env.shopping.sync())
            assertEquals("the milk keeps its place", 2, requests.count { it.method == "PUT" })
        }

    @Test
    fun `items that shared a place take places one after another`() =
        runBlocking {
            // Three items at the same place, as Mealie can set them when a recipe adds them.
            serverList = listJson(avocado = 0, milk = 0, bread = 0)
            env.shopping.refresh(LIST)
            requests.clear()

            env.shopping.reorder(LIST, listOf("milk", "bread", "avocado"))

            val places = list().items.filter { !it.checked }.associate { it.id to it.position }
            assertEquals(mapOf("milk" to 0, "bread" to 1, "avocado" to 2), places)
            assertTrue(env.shopping.sync())
            assertEquals("the milk keeps its place", 2, requests.count { it.method == "PUT" })
        }

    @Test
    fun `the aisles take the shop's order, and Mealie gets every one of them`() =
        runBlocking {
            env.shopping.orderAisles(LIST, listOf("bakery", "produce"))

            assertEquals(listOf("bakery", "produce", "dairy"), list().labelOrder)
            assertEquals(listOf("Backwaren", "Obst & Gemüse", "Milchprodukte"), list().labels.map { it.name })
            assertEquals(1, env.shopping.pendingCount(LIST).first())
            assertTrue(env.shopping.sync())
            val body = Json.parseToJsonElement(sent("PUT", "/label-settings").single().body!!.utf8()).jsonArray
            assertEquals(
                listOf("s-bakery" to 0, "s-produce" to 1, "s-dairy" to 2),
                body.map { it.jsonObject["id"]!!.jsonPrimitive.content to it.jsonObject["position"]!!.jsonPrimitive.int },
            )
            assertTrue("each names its list and its label", body.all { "shoppingListId" in it.jsonObject && "labelId" in it.jsonObject })
            assertTrue("and nothing Mealie does not take", body.none { "label" in it.jsonObject })
        }

    @Test
    fun `a thing filed under an aisle goes there again when it is added anew`() =
        runBlocking {
            env.shopping.editItem(LIST, "avocado", text = null, label = PRODUCE)
            assertEquals(PRODUCE, list().items.single { it.id == "avocado" }.label)
            assertEquals("its text stays", "2 Avocados", list().items.single { it.id == "avocado" }.note)

            env.shopping.add(LIST, "3 avocados")
            env.shopping.add(LIST, "Zahnpasta")

            val added = list().items.takeLast(2)
            assertEquals(PRODUCE, added[0].label)
            assertEquals("what was never filed stays without an aisle", null, added[1].label)
            assertTrue(env.shopping.sync())
            assertTrue(
                sent("POST", "/shopping/items")
                    .first()
                    .body!!
                    .utf8()
                    .contains("\"labelId\":\"produce\""),
            )
        }

    @Test
    fun `new text replaces an item's amount and food, and the aisle it was given stays`() =
        runBlocking {
            env.shopping.editItem(LIST, "milk", text = "1 l Hafermilch", label = DAIRY)

            val milk = list().items.single { it.id == "milk" }
            assertEquals("1 l Hafermilch", milk.note)
            assertEquals(null, milk.food)
            assertEquals(0.0, milk.quantity, 0.0)
            assertEquals(DAIRY, milk.label)
        }

    private companion object {
        const val LIST = "list-1"
        val PRODUCE = ShoppingLabel("produce", "Obst & Gemüse")
        val DAIRY = ShoppingLabel("dairy", "Milchprodukte")

        fun setting(id: String, name: String, position: Int) =
            """{"id":"s-$id","shoppingListId":"$LIST","labelId":"$id","position":$position,""" +
                """"label":{"id":"$id","name":"$name","color":"#959595","groupId":"g"}}"""

        /** An item as Mealie sends it; one of a [food] carries its amount and food, and no note. */
        fun item(
            id: String,
            note: String,
            position: Int,
            checked: Boolean = false,
            food: String? = null,
        ): String {
            val quantity = if (food != null) 300 else 0
            val written = if (food != null) "" else note
            return """{"id":"$id","shoppingListId":"$LIST","quantity":$quantity,"note":"$written",""" +
                """"display":"$note","checked":$checked,"position":$position,"recipeReferences":[]""" +
                (food?.let { ""","food":{"id":"f-$id","name":"$it"},"foodId":"f-$id"""" } ?: "") + "}"
        }

        /** The list as Mealie has it, its open items at the places given. */
        fun listJson(avocado: Int = 4, milk: Int = 9, bread: Int = 2) =
            """{"id":"$LIST","name":"Wocheneinkauf","listItems":[""" +
                listOf(
                    item("avocado", "2 Avocados", avocado),
                    item("milk", "300 ml Milch", milk, food = "Milch"),
                    item("bread", "Brot", bread),
                    item("lemon", "Zitrone", 7, checked = true),
                ).joinToString(",") +
                """],"labelSettings":[""" +
                listOf(setting("produce", "Obst & Gemüse", 0), setting("dairy", "Milchprodukte", 1), setting("bakery", "Backwaren", 2))
                    .joinToString(",") +
                """],"recipeReferences":[]}"""
    }
}

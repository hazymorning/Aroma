package app.aroma.feature.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aroma.data.AromaError
import app.aroma.data.TestEnvironment
import app.aroma.data.address
import app.aroma.data.eventStream
import app.aroma.data.json
import app.aroma.data.mealieServer
import app.aroma.data.remote.MealieJson
import app.aroma.data.session.Session
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reworks against a stand-in for Mealie, whose AI import makes a draft of the recipe it is sent.
 * Some tests make Mealie fail along the way, or cut the connection while it makes the draft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de")
class RecipeReworkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val server = mealieServer { answer(it.also(requests::add)) }
    private val env = TestEnvironment()

    /** Recipes on the server by slug, and the newest of them, first. */
    private val stored = ConcurrentHashMap(mapOf(ORIGINAL_SLUG to ORIGINAL))
    private val listed = CopyOnWriteArrayList(listOf(summary("old-1", "aelteres-rezept")))

    /** The draft Mealie's AI makes. */
    @Volatile
    private var draft = draft()

    /** What fails: reading the draft back, or saving the recipe. */
    @Volatile
    private var draftUnreadable = false

    @Volatile
    private var patchRefused = false

    /** Whether the connection drops while Mealie makes the draft, which it saves all the same. */
    @Volatile
    private var connectionDrops = false

    @Before
    fun setUp() {
        runBlocking { env.sessions.start(Session(server.address, token = "t", userId = USER, username = "mara")) }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `the recipe goes to Mealie's AI as plain text and takes the draft's content in one change, keeping what is its own`() =
        runBlocking<Unit> {
            // Renamed by the AI, which the recipe takes only from a translation.
            draft = draft(slug = "dicke-pfannkuchen", name = "Dicke Pfannkuchen")

            val reworked = reworker().rework(ORIGINAL_ID, translateTo = null, onSending = {}) { }

            val sent = requests.single { it.url.encodedPath == "/api/recipes/create/ai/stream" }
            val text = sent.part("content")!!
            assertEquals(
                """
                Pfannkuchen

                Dicke Pfannkuchen.
                Schnell gemacht.

                4 Portionen
                Gesamt: 30 Min.

                Zutaten

                Für den Teig
                - 250 g Mehl
                - 2 Eier

                Zum Servieren
                - Ahornsirup

                Zubereitung
                1. Mehl und Eier verrühren, bis keine Klümpchen › 2 mm bleiben.

                Backen
                2. Je Seite ‹ 2 Minuten backen.

                Notizen
                Tipp: Mit Banane servieren.
                """.trimIndent(),
                text,
            )
            assertFalse("Mealie would read the text as a web page", text.contains('<') || text.contains('>'))
            assertNull("nothing to translate", sent.part("translateLanguage"))
            assertNull("only the text, never a page to load", sent.part("url"))

            assertEquals(ORIGINAL_SLUG, reworked.slug)
            val change = patches().single()
            assertEquals("/api/recipes/$ORIGINAL_SLUG", change.url.encodedPath)
            val patch = change.json()
            assertEquals(setOf("description", "totalTime", "recipeIngredient", "recipeInstructions", "notes", "tags"), patch.keys)
            assertEquals("Dicke Pfannkuchen, schnell gemacht.", patch.text("description"))
            assertEquals("30 Minuten", patch.text("totalTime"))
            assertEquals(listOf("d1", "d2", "d3"), patch.items("recipeIngredient").map { it.text("referenceId") })
            assertEquals(listOf("Für den Teig", null, "Zum Servieren"), patch.items("recipeIngredient").map { it.text("title") })
            val firstStep = patch.items("recipeInstructions").first()
            assertEquals(listOf("d1", "d2"), firstStep["ingredientReferences"]!!.jsonArray.map { it.jsonObject.text("referenceId") })
            assertEquals("n1", patch.items("notes").single().text("referenceId"))
            assertEquals(
                "the draft's tags join the recipe's own",
                listOf("t-schnell", "t-veggie"),
                patch.items("tags").map { it.text("id") },
            )

            val paths = requests.map { "${it.method} ${it.url.encodedPath}" }
            val deleted = paths.indexOf("DELETE /api/recipes/dicke-pfannkuchen")
            assertTrue("the draft goes", deleted >= 0 && !stored.containsKey("dicke-pfannkuchen"))
            assertTrue("before the recipe changes, which may take its name", deleted < paths.indexOf("PATCH /api/recipes/$ORIGINAL_SLUG"))
        }

    @Test
    fun `the name comes from the draft only where a translation changed it`() =
        runBlocking<Unit> {
            // The draft has the name the translation left as it was with the number Mealie gave it.
            reworker().rework(ORIGINAL_ID, translateTo = "de-DE", onSending = {}) { }
            assertFalse(patches().single().json().containsKey("name"))

            draft = draft(slug = "pancakes", name = "Pancakes")
            val reworked = reworker().rework(ORIGINAL_ID, translateTo = "en-US", onSending = {}) { }

            val sent = requests.last { it.url.encodedPath == "/api/recipes/create/ai/stream" }
            assertEquals("en-US", sent.part("translateLanguage"))
            assertEquals("Pancakes", patches().last().json().text("name"))
            assertEquals("pancakes", reworked.slug)
        }

    @Test
    fun `a draft that cannot be read or a recipe that cannot be changed leaves the recipe as it was, and the draft goes`() =
        runBlocking<Unit> {
            draftUnreadable = true
            assertFails<AromaError.Server> { reworker().rework(ORIGINAL_ID, translateTo = null, onSending = {}) { } }
            assertTrue(patches().isEmpty())

            draftUnreadable = false
            patchRefused = true
            assertFails<AromaError.Forbidden> { reworker().rework(ORIGINAL_ID, translateTo = null, onSending = {}) { } }

            assertEquals(2, requests.count { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/$DRAFT_SLUG" })
            assertEquals(ORIGINAL, stored[ORIGINAL_SLUG])
        }

    @Test
    fun `undo puts back what the rework replaced, in one change`() =
        runBlocking<Unit> {
            val reworked = reworker().rework(ORIGINAL_ID, translateTo = null, onSending = {}) { }
            val replaced = patches().single().json().keys

            env.recipes.update(reworked.slug, reworked.undo)

            val undo = patches().last().json()
            assertEquals(replaced, undo.keys)
            undo.keys.forEach { key -> assertEquals(key, ORIGINAL[key] ?: JsonNull, undo[key]) }
            assertEquals(ORIGINAL, stored[ORIGINAL_SLUG])
        }

    @Test
    fun `when the connection drops while Mealie makes the draft, Aroma waits for it instead of asking again`() =
        runBlocking<Unit> {
            connectionDrops = true
            val steps = mutableListOf<ImportStep>()

            reworker().rework(ORIGINAL_ID, translateTo = null, onSending = {}) { steps += it }

            assertEquals(1, requests.count { it.url.encodedPath == "/api/recipes/create/ai/stream" })
            assertTrue(ImportStep.StillWorking in steps)
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/$DRAFT_SLUG" })
            assertEquals("Dicke Pfannkuchen, schnell gemacht.", stored.getValue(ORIGINAL_SLUG).text("description"))
        }

    @Test
    fun `a rework picked up after Android ended the app sends no second draft and finishes with the one Mealie made`() =
        runBlocking<Unit> {
            val id = UUID.randomUUID()
            // The first run sent its request before Android ended the app; Mealie went on and saved the draft.
            File(context.noBackupFilesDir, "reworks/$id").apply { parentFile?.mkdirs() }.writeText("old-1")
            saveDraft()
            val reworks = RecipeReworks(context, reworker())
            val request = ReworkRequest(ORIGINAL_ID, ORIGINAL_SLUG, "Pfannkuchen", translateTo = null)

            reworks.run(id, request, startedAt = System.currentTimeMillis(), rerun = true)

            assertFalse(requests.any { it.url.encodedPath.startsWith("/api/recipes/create/") })
            assertTrue(reworks.job(ORIGINAL_ID).value?.outcome is ReworkOutcome.Reworked)
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/$DRAFT_SLUG" })
            assertEquals("Dicke Pfannkuchen, schnell gemacht.", stored.getValue(ORIGINAL_SLUG).text("description"))
            assertFalse(File(context.noBackupFilesDir, "reworks/$id").exists())
        }

    private fun reworker() =
        RecipeReworker(env.recipes, resources = { context.resources }, patience = 2.seconds, checkEvery = 10.milliseconds)

    private fun patches() = requests.filter { it.method == "PATCH" && it.url.encodedPath.startsWith("/api/recipes/") }

    private inline fun <reified T : AromaError> assertFails(block: () -> Unit): T =
        try {
            block()
            throw AssertionError("expected ${T::class.simpleName}")
        } catch (error: AromaError) {
            error as? T ?: throw error
        }

    private fun answer(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val key = path.removePrefix("/api/recipes/")
        return when {
            request.method == "GET" && path == "/api/recipes" -> {
                json("""{"page":1,"per_page":10,"total":${listed.size},"total_pages":1,"items":[${listed.joinToString(",")}]}""")
            }

            request.method == "POST" && path == "/api/recipes/create/ai/stream" -> {
                saveDraft()
                if (connectionDrops) {
                    MockResponse
                        .Builder()
                        .addHeader("Content-Type", "text/event-stream")
                        .chunkedBody("event: progress\ndata: {\"message\":\"Rezept erstellen...\"}\n\n", 64)
                        .onResponseBody(SocketEffect.CloseSocket())
                        .build()
                } else {
                    val done = "event: done\ndata: {\"slug\":\"${draft.text("slug")}\"}\n\n"
                    eventStream("event: progress\ndata: {\"message\":\"Rezept erstellen...\"}\n\n$done")
                }
            }

            request.method == "GET" && key == draft.text("slug") && draftUnreadable -> {
                MockResponse.Builder().code(500).build()
            }

            request.method == "GET" -> {
                recipe(key)?.let { json(it.toString()) } ?: MockResponse.Builder().code(404).build()
            }

            request.method == "PATCH" && patchRefused -> {
                MockResponse.Builder().code(403).build()
            }

            request.method == "PATCH" -> {
                val recipe = recipe(key) ?: return MockResponse.Builder().code(404).build()
                val patch = request.json()
                val slug = patch.text("name")?.lowercase() ?: recipe.text("slug")!!
                val changed = JsonObject(recipe + patch + ("slug" to JsonPrimitive(slug)))
                stored.remove(recipe.text("slug"))
                stored[slug] = changed
                json(changed.toString())
            }

            request.method == "DELETE" -> {
                stored.remove(key)
                listed.removeIf { it.contains("\"slug\":\"$key\"") }
                json("{}")
            }

            else -> {
                MockResponse.Builder().code(404).build()
            }
        }
    }

    /** Mealie saves the draft its AI made: the newest recipe now, the signed-in user's. */
    private fun saveDraft() {
        val slug = draft.text("slug")!!
        stored[slug] = draft
        listed.add(0, summary(draft.text("id")!!, slug))
    }

    /** Mealie finds a recipe by its slug or its id. */
    private fun recipe(key: String): JsonObject? = stored[key] ?: stored.values.firstOrNull { it.text("id") == key }

    private fun summary(id: String, slug: String) = """{"id":"$id","slug":"$slug","name":"$slug","userId":"$USER","orgURL":null}"""

    private fun RecordedRequest.json(): JsonObject = MealieJson.parseToJsonElement(body!!.utf8()).jsonObject

    /** The text of the form field [name] of a multipart request, or null without one. */
    private fun RecordedRequest.part(name: String): String? {
        val body = body!!.utf8()
        val field = body.indexOf("name=\"$name\"").takeIf { it >= 0 } ?: return null
        val start = body.indexOf("\r\n\r\n", field) + 4
        return body.substring(start, body.indexOf("\r\n--", start))
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.items(key: String): List<JsonObject> = (this[key] as JsonArray).map { it.jsonObject }

    private companion object {
        const val USER = "user-1"
        const val ORIGINAL_ID = "id-pfannkuchen"
        const val ORIGINAL_SLUG = "pfannkuchen"
        const val DRAFT_SLUG = "pfannkuchen-1"

        /** A recipe typed into Mealie by hand: some HTML, signs that look like it, an emoji and sections. */
        val ORIGINAL =
            """
            {"id":"$ORIGINAL_ID","slug":"$ORIGINAL_SLUG","name":"Pfannkuchen","description":"Dicke <b>Pfannkuchen</b>.<br>Schnell gemacht.",
             "image":"Ab12","rating":4.0,"orgURL":"https://example.com/pfannkuchen",
             "recipeServings":4.0,"recipeYieldQuantity":0.0,"recipeYield":"",
             "prepTime":null,"prepTimeSeconds":null,"performTime":null,"performTimeSeconds":null,"totalTime":null,"totalTimeSeconds":1800,
             "recipeIngredient":[
              {"title":"Für den Teig","note":"250 g Mehl","display":"250 g Mehl","quantity":0.0,"unit":null,"food":null,"referenceId":"o1"},
              {"title":null,"note":"2 Eier","display":"2 Eier","quantity":0.0,"unit":null,"food":null,"referenceId":"o2"},
              {"title":"Zum Servieren","note":"Ahornsirup","display":"Ahornsirup","quantity":0.0,"unit":null,"food":null,"referenceId":"o3"}],
             "recipeInstructions":[
              {"id":"s1","title":"","summary":"","text":"Mehl und Eier verrühren, bis keine Klümpchen > 2 mm bleiben.","ingredientReferences":[]},
              {"id":"s2","title":"Backen","summary":"","text":"Je Seite < 2 Minuten backen.","ingredientReferences":[]}],
             "notes":[{"title":"Tipp","text":"Mit Banane 🍌 servieren."}],
             "tags":[{"id":"t-schnell","name":"Schnell","slug":"schnell"}],
             "recipeCategory":[{"id":"c-fruehstueck","name":"Frühstück","slug":"fruehstueck"}],
             "tools":[{"id":"tool-pfanne","name":"Pfanne","slug":"pfanne"}],
             "nutrition":{"calories":"380"},"settings":{"public":true,"showNutrition":true},"comments":[]}
            """.trimIndent().let {
                MealieJson.parseToJsonElement(it).jsonObject
            }

        /** The draft Mealie's AI makes of it: the same name, which Mealie numbers, as the recipe has it already. */
        fun draft(slug: String = DRAFT_SLUG, name: String = "Pfannkuchen (1)"): JsonObject =
            MealieJson
                .parseToJsonElement(
                    """
                    {"id":"id-draft","slug":"$slug","name":"$name","description":"Dicke Pfannkuchen, schnell gemacht.",
                     "image":null,"orgURL":null,"rating":null,
                     "recipeServings":4.0,"recipeYieldQuantity":0.0,"recipeYield":"",
                     "prepTime":null,"prepTimeSeconds":null,"performTime":null,"performTimeSeconds":null,
                     "totalTime":"30 Minuten","totalTimeSeconds":null,
                     "recipeIngredient":[
                      {"title":"Für den Teig","note":"250 g Mehl","display":"250 g Mehl","referenceId":"d1"},
                      {"title":null,"note":"2 Eier","display":"2 Eier","referenceId":"d2"},
                      {"title":"Zum Servieren","note":"Ahornsirup","display":"Ahornsirup","referenceId":"d3"}],
                     "recipeInstructions":[
                      {"id":"ds1","title":"","summary":"","text":"Mehl und Eier glatt verrühren.",
                       "ingredientReferences":[{"referenceId":"d1"},{"referenceId":"d2"}]},
                      {"id":"ds2","title":"Backen","summary":"","text":"Je Seite knapp 2 Minuten backen.","ingredientReferences":[]}],
                     "notes":[{"title":"Tipp","text":"Mit Banane servieren.","referenceId":"n1"}],
                     "tags":[{"id":"t-schnell","name":"Schnell","slug":"schnell"},{"id":"t-veggie","name":"Vegetarisch","slug":"vegetarisch"}],
                     "recipeCategory":[{"id":"c-fruehstueck","name":"Frühstück","slug":"fruehstueck"}],
                     "tools":[{"id":"tool-schuessel","name":"Schüssel","slug":"schuessel"}],
                     "nutrition":null,"settings":{"public":true},"comments":[]}
                    """.trimIndent(),
                ).jsonObject
    }
}

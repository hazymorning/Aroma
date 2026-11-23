package app.aroma.feature.editor

import app.aroma.data.AromaError
import app.aroma.data.TestEnvironment
import app.aroma.data.session.Session
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A proxy or a mobile network may drop an import that waits long for an AI provider, while
 * Mealie finishes it anyway. These tests cut the connection the way such a proxy does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeImporterTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment

    /** Recipes on the server, newest first. */
    private val listed = CopyOnWriteArrayList(listOf(summary("old-1", "aelteres-rezept")))
    private val details = mutableMapOf<String, String>()

    /** The import to cut off, before or after the server is done with it. */
    @Volatile
    private var cut: Cut? = null

    /** Set once the connection was cut: the recipe Mealie goes on to save. */
    @Volatile
    private var finishing: Cut? = null

    @Volatile
    private var checksAfterCut = 0

    /** How many checks after the cut fail as if the phone were between networks. */
    @Volatile
    private var failingChecks = 0

    /** The slug the AI import answers with when it is not cut. */
    @Volatile
    private var aiReturns = "vorschau"

    /** Cuts the stream of [endpoint] ("url" or "ai"); [arrives] shows up on the server two checks later, if not null. */
    private class Cut(val endpoint: String, val arrives: String?)

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = answer(request.also { requests += it })
            }
        server.start()
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.url("/").toString().trimEnd('/'), token = "t", userId = USER, username = "mara"))
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `a recipe Mealie saves after the connection dropped is picked up`() =
        runBlocking<Unit> {
            details["gnocchi"] = recipe("gnocchi", "Tomaten-Gnocchi", ingredients = listOf("500 g Gnocchi"), steps = listOf("Braten."))
            cut = Cut("url", arrives = "gnocchi")
            val steps = mutableListOf<ImportStep>()

            val slug = importer().import(ImportRequest(LINK)) { steps += it }

            assertEquals("gnocchi", slug)
            assertTrue(ImportStep.StillWorking in steps)
        }

    @Test
    fun `a post rebuilt by AI after a dropped connection keeps its link and photo, the preview goes away`() =
        runBlocking<Unit> {
            preview()
            details["gnocchi"] = recipe("gnocchi", "Tomaten-Gnocchi", ingredients = listOf("500 g Gnocchi"), steps = listOf("Braten."))
            cut = Cut("ai", arrives = "gnocchi")

            val slug = importer().import(ImportRequest(LINK, useAi = true)) { }

            assertEquals("gnocchi", slug)
            val rebuild = requests.single { it.url.encodedPath == "/api/recipes/create/ai/stream" }.body!!.utf8()
            assertTrue("the caption goes to the AI", rebuild.contains("name=\"content\""))
            assertFalse("the post is not loaded a second time", rebuild.contains("name=\"url\""))
            assertTrue(
                requests.any {
                    it.method == "PATCH" && it.url.encodedPath == "/api/recipes/gnocchi" &&
                        it.body!!.utf8().contains(LINK)
                },
            )
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
        }

    @Test
    fun `a recipe someone else adds meanwhile is not taken for the dropped import`() =
        runBlocking<Unit> {
            details["gnocchi"] = recipe("gnocchi", "Tomaten-Gnocchi", ingredients = listOf("500 g Gnocchi"), steps = listOf("Braten."))
            cut = Cut("url", arrives = "gnocchi")
            // Another household member saves a recipe while Aroma waits; it must not be mistaken for the import.
            listed.add(0, summary("id-fremd", "fremdes-rezept", link = LINK, user = "someone-else"))

            assertEquals("gnocchi", importer().import(ImportRequest(LINK)) { })
        }

    @Test
    fun `when no recipe turns up the import says Mealie did not answer`() =
        runBlocking<Unit> {
            cut = Cut("url", arrives = null)
            try {
                importer(patience = 200.milliseconds).import(ImportRequest(LINK)) { }
                fail("Nothing arrived, so the import cannot succeed")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(AromaError.ImportFailed.Reason.NoAnswer, failure.reason)
            }
        }

    @Test
    fun `checks that fail for a moment, as on a phone between networks, do not end the wait`() =
        runBlocking<Unit> {
            details["gnocchi"] = recipe("gnocchi", "Tomaten-Gnocchi", ingredients = listOf("500 g Gnocchi"), steps = listOf("Braten."))
            cut = Cut("url", arrives = "gnocchi")
            failingChecks = 3

            assertEquals("gnocchi", importer().import(ImportRequest(LINK)) { })
        }

    @Test
    fun `without any connection the import says so at once instead of waiting`() =
        runBlocking<Unit> {
            server.close()
            try {
                importer(patience = 1000.seconds).import(ImportRequest(LINK)) { }
                fail("The server is gone, so the import cannot succeed")
            } catch (_: AromaError.Offline) {
                // Expected, and quickly: the patience above would otherwise hold the test for minutes.
            }
        }

    @Test
    fun `when the rebuild gets no answer, no second recipe is sorted from the caption`() =
        runBlocking<Unit> {
            preview()
            cut = Cut("ai", arrives = null)
            try {
                importer(patience = 200.milliseconds).import(ImportRequest(LINK, useAi = true)) { }
                fail("Nothing arrived, so the import cannot succeed")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(AromaError.ImportFailed.Reason.NoAnswer, failure.reason)
            }
            assertFalse(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
            assertFalse(requests.any { it.method == "DELETE" })
        }

    @Test
    fun `a post to translate starts with the regular import, the AI then reads only its caption`() =
        runBlocking<Unit> {
            preview()
            details["gnocchi"] = recipe("gnocchi", "Tomato gnocchi", ingredients = listOf("500 g gnocchi"), steps = listOf("Fry."))
            aiReturns = "gnocchi"

            val slug = importer().import(ImportRequest(LINK, useAi = true, translateTo = "en-GB")) { }

            assertEquals("gnocchi", slug)
            val imports = requests.filter { it.url.encodedPath.startsWith("/api/recipes/create/") }.map { it.url.encodedPath }
            assertEquals(listOf("/api/recipes/create/url/stream", "/api/recipes/create/ai/stream"), imports)
            val rebuild = requests.last { it.url.encodedPath == "/api/recipes/create/ai/stream" }.body!!.utf8()
            assertTrue(rebuild.contains("en-GB"))
            assertFalse(rebuild.contains("name=\"url\""))
        }

    /** What Mealie makes of an Instagram post: the caption as the name, placeholders, the photo. */
    private fun preview() {
        details["vorschau"] =
            recipe(
                "vorschau",
                "Koch mit mir on Instagram: \"Tomaten-Gnocchi\n\nZutaten:\n- 500 g Gnocchi\n\nZubereitung:\n1. Braten.\"",
                ingredients = listOf("Could not detect ingredients"),
                steps = listOf("Could not detect instructions"),
                image = "foto",
                link = LINK,
            )
        listed.add(0, summary("id-vorschau", "vorschau"))
    }

    private fun importer(patience: kotlin.time.Duration = 2.seconds) =
        RecipeImporter(env.recipes, "Rezept", patience, checkEvery = 10.milliseconds)

    private fun answer(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            request.method == "GET" && path == "/api/recipes" -> {
                list()
            }

            request.method == "POST" && path.startsWith("/api/recipes/create/") -> {
                val planned = cut
                if (planned != null && path == "/api/recipes/create/${planned.endpoint}/stream") {
                    cut = null
                    finishing = planned
                    cutStream()
                } else {
                    val slug = if (path.contains("/ai/")) aiReturns else "vorschau"
                    sse("event: done\ndata: {\"slug\":\"$slug\"}\n\n")
                }
            }

            request.method == "GET" && path.startsWith("/api/media/recipes/") -> {
                MockResponse.Builder().body(Buffer().write(byteArrayOf(1, 2, 3))).build()
            }

            request.method == "GET" && path.startsWith("/api/recipes/") -> {
                details[path.removePrefix("/api/recipes/")]?.let(::json) ?: MockResponse.Builder().code(404).build()
            }

            request.method == "PATCH" -> {
                json(details.getValue(path.removePrefix("/api/recipes/")))
            }

            request.method == "PUT" || request.method == "DELETE" -> {
                json("{}")
            }

            else -> {
                MockResponse.Builder().code(404).build()
            }
        }
    }

    private fun list(): MockResponse {
        val pending = finishing
        if (pending != null) {
            checksAfterCut++
            if (checksAfterCut <= failingChecks) return MockResponse.Builder().code(502).build()
            if (pending.arrives != null && checksAfterCut == failingChecks + 2) {
                // A recipe read from the link keeps it; one rebuilt from the caption has none yet.
                listed.add(0, summary("id-${pending.arrives}", pending.arrives, link = LINK.takeIf { pending.endpoint == "url" }))
            }
        }
        return json("""{"page":1,"per_page":10,"total":${listed.size},"total_pages":1,"items":[${listed.joinToString(",")}]}""")
    }

    /** A stream that reports progress and then loses its connection, like one cut by a proxy. */
    private fun cutStream() =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/event-stream")
            .chunkedBody("event: progress\ndata: {\"message\":\"Rezept wird erstellt\"}\n\n", 64)
            .onResponseBody(SocketEffect.CloseSocket())
            .build()

    private fun sse(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/event-stream")
            .body(body)
            .build()

    private fun json(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun summary(id: String, slug: String, link: String? = null, user: String = USER) =
        """{"id":"$id","slug":"$slug","name":"$slug","userId":"$user","orgURL":${link?.let { "\"$it\"" }}}"""

    private fun recipe(
        slug: String,
        name: String,
        ingredients: List<String>,
        steps: List<String>,
        image: String? = null,
        link: String? = null,
    ): String {
        val ingredientJson = ingredients.joinToString(",") { """{"note":"$it","display":"$it"}""" }
        val stepJson = steps.joinToString(",") { """{"text":"$it"}""" }
        val escapedName = name.replace("\"", "\\\"").replace("\n", "\\n")
        return """{"id":"id-$slug","slug":"$slug","name":"$escapedName","description":"","image":${image?.let { "\"$it\"" }},""" +
            """"orgURL":${link?.let { "\"$it\"" }},"recipeIngredient":[$ingredientJson],"recipeInstructions":[$stepJson]}"""
    }

    private companion object {
        const val USER = "user-1"
        const val LINK = "https://www.instagram.com/p/AromaTest02/"
    }
}

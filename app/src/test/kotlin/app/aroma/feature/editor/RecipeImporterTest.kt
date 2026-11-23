package app.aroma.feature.editor

import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import app.aroma.data.session.Session
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.Dns
import okhttp3.OkHttpClient
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
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Imports against a stand-in for Mealie, which also serves the social media post the way
 * Instagram does: its caption only in the link preview. Some tests cut the connection the way a
 * proxy does while Mealie finishes the recipe anyway.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeImporterTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment

    /** Recipes on the server, newest first. */
    private val listed = CopyOnWriteArrayList(listOf(summary("old-1", "aelteres-rezept")))
    private val known = setOf("old-1")
    private val details = mutableMapOf<String, String>()

    /** The post's caption in its link preview; without one, its page sends visitors to sign in. */
    @Volatile
    private var caption: String? = CAPTION

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

    /** Whether Mealie's AI answers with an error, as when its provider fails. */
    @Volatile
    private var aiFails = false

    /** The slug the imports answer with when they are not cut. */
    @Volatile
    private var answers = "gnocchi"

    /** The slug Mealie's regular import answers with, where it differs from [answers]. */
    @Volatile
    private var linkAnswers: String? = null

    /** Whether Mealie's regular import cannot read the post either, as when Instagram blocks the server too. */
    @Volatile
    private var linkFails = false

    /** Names other recipes have already, which Mealie refuses for a renamed recipe. */
    private val takenNames = mutableSetOf<String>()

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
        details["gnocchi"] = recipe("gnocchi", "Tomaten-Gnocchi", ingredients = listOf("500 g Gnocchi"), steps = listOf("Braten."))
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
    fun `a caption that lists its ingredients and steps becomes the recipe at once, without the AI`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(post, useAi = true)) { steps += it }

            assertEquals("gnocchi", slug)
            assertEquals("neither Mealie's import nor its AI is asked", emptyList<String>(), creations())
            assertFalse("the post's page is not loaded, its embed has it all", requests.any { it.url.encodedPath == "/p/AromaTest02/" })
            val saved = requests.single { it.method == "PATCH" && it.body!!.utf8().contains("recipeIngredient") }.body!!.utf8()
            assertTrue(saved.contains("500 g Gnocchi") && saved.contains(post))
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
            assertEquals(listOf(ImportStep.ReadingPost, ImportStep.Saving, ImportStep.AddingPhoto), steps)
        }

    @Test
    fun `a caption in prose goes to Mealie's AI as text alone, and its link and photo follow`() =
        runBlocking<Unit> {
            caption = PROSE

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)))
            assertEquals(listOf("/api/recipes/create/ai/stream"), creations())
            val sent = requests.single { it.url.encodedPath == "/api/recipes/create/ai/stream" }.body!!.utf8()
            assertTrue("the caption goes to the AI", sent.contains("Gnocchi in Butter goldbraun braten"))
            assertFalse("Mealie does not load the post itself", sent.contains("name=\"url\""))
            assertTrue(requests.any { it.method == "PATCH" && it.body!!.utf8().contains(post) })
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
        }

    @Test
    fun `the emoji Mealie's AI copies from a caption leave the recipe`() =
        runBlocking<Unit> {
            caption = PROSE
            details["gnocchi"] =
                recipe(
                    "gnocchi",
                    "Tomaten-Gnocchi 🍅",
                    ingredients = listOf("🥔 500 g Gnocchi", "🍅 250 g Kirschtomaten"),
                    steps = listOf("🔥 Braten."),
                )

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)))
            val cleaned = requests.last { it.method == "PATCH" && it.url.encodedPath == "/api/recipes/gnocchi" }.body!!.utf8()
            assertTrue(cleaned.contains("\"note\":\"500 g Gnocchi\"") && cleaned.contains("\"text\":\"Braten.\""))
            assertTrue(cleaned.contains("\"name\":\"Tomaten-Gnocchi\""))
            assertFalse(cleaned.contains("🍅") || cleaned.contains("🔥"))
        }

    @Test
    fun `a recipe without emoji is left as it is`() =
        runBlocking<Unit> {
            importer().run(ImportRequest(PAGE))

            assertFalse(requests.any { it.method == "PATCH" })
        }

    @Test
    fun `a caption to be translated goes to Mealie's AI, ordered or not`() =
        runBlocking<Unit> {
            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true, translateTo = "en-US")))
            val sent = requests.single { it.url.encodedPath == "/api/recipes/create/ai/stream" }.body!!.utf8()
            assertTrue(sent.contains("- 500 g Gnocchi") && sent.contains("en-US"))
        }

    @Test
    fun `a post whose text holds no recipe is read by Mealie, and its caption kept as the recipe's text`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"
            aiFails = true
            preview("Das Rezept steht im Video! #gnocchi")

            val slug = importer().run(ImportRequest(post, useAi = true))

            assertEquals("gnocchi", slug)
            assertEquals(listOf("/api/recipes/create/ai/stream", "/api/recipes/create/url/stream"), creations())
            val saved = requests.single { it.method == "PATCH" && it.url.encodedPath == "/api/recipes/gnocchi" }.body!!.utf8()
            assertTrue(saved.contains("Das Rezept steht im Video!"))
            assertTrue("no placeholders", saved.contains("\"recipeIngredient\":[]"))
            assertTrue(
                "the preview gives its photo and goes",
                requests.any {
                    it.method == "DELETE" &&
                        it.url.encodedPath == "/api/recipes/vorschau"
                },
            )
        }

    @Test
    fun `without AI a post becomes one recipe from its text, with its link and photo`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(post)) { steps += it }

            assertEquals("gnocchi", slug)
            assertEquals(emptyList<String>(), creations())
            val saved = requests.single { it.method == "PATCH" && it.body!!.utf8().contains("recipeIngredient") }.body!!.utf8()
            assertTrue(saved.contains("500 g Gnocchi") && saved.contains(post))
            assertEquals(listOf(ImportStep.ReadingPost, ImportStep.Saving, ImportStep.AddingPhoto), steps)
        }

    @Test
    fun `a post the phone may not see is read by Mealie, and its link preview becomes the recipe`() =
        runBlocking<Unit> {
            caption = null
            preview(CAPTION)
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(post)) { steps += it }

            assertEquals("gnocchi", slug)
            assertEquals(listOf("/api/recipes/create/url/stream"), creations())
            assertTrue(
                "named by the caption",
                requests
                    .single {
                        it.method == "POST" && it.url.encodedPath == "/api/recipes"
                    }.body!!
                    .utf8()
                    .contains("Tomaten-Gnocchi"),
            )
            val saved = requests.single { it.method == "PATCH" && it.url.encodedPath == "/api/recipes/gnocchi" }.body!!.utf8()
            assertTrue(saved.contains("500 g Gnocchi") && saved.contains(post))
            assertTrue(requests.any { it.method == "GET" && it.url.encodedPath == "/api/media/recipes/id-vorschau/images/original.webp" })
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
            assertEquals(listOf(ImportStep.ReadingPost, ImportStep.Waiting, ImportStep.Saving, ImportStep.AddingPhoto), steps)
        }

    @Test
    fun `with AI, a caption in prose Mealie read goes to the AI as text, and the new recipe takes the preview's photo`() =
        runBlocking<Unit> {
            caption = null
            preview(PROSE)

            val slug = importer().run(ImportRequest(post, useAi = true))

            assertEquals("gnocchi", slug)
            assertEquals(listOf("/api/recipes/create/url/stream", "/api/recipes/create/ai/stream"), creations())
            val sent = requests.single { it.url.encodedPath == "/api/recipes/create/ai/stream" }.body!!.utf8()
            assertTrue(sent.contains("Gnocchi in Butter goldbraun braten"))
            assertFalse("the AI never gets the post's link, which shows it the sign-in page", sent.contains("name=\"url\""))
            assertTrue(requests.any { it.method == "GET" && it.url.encodedPath == "/api/media/recipes/id-vorschau/images/original.webp" })
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
            assertTrue(
                requests.any {
                    it.method == "PATCH" && it.url.encodedPath == "/api/recipes/gnocchi" &&
                        it.body!!.utf8().contains(post)
                },
            )
        }

    @Test
    fun `a post neither the phone nor Mealie can read asks for its text and leaves nothing behind`() =
        runBlocking<Unit> {
            caption = null
            linkFails = true
            try {
                importer().run(ImportRequest(post))
                fail("Without its text the post cannot be read")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.PostUnreadable, failure.reason)
            }
            assertFalse(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
        }

    @Test
    fun `a post imported again is named by Mealie, which numbers a name another recipe has`() =
        runBlocking<Unit> {
            caption = null
            preview(CAPTION)
            takenNames += "Tomaten-Gnocchi"

            assertEquals("gnocchi", importer().run(ImportRequest(post)))
            assertTrue(
                requests
                    .single { it.method == "POST" && it.url.encodedPath == "/api/recipes" }
                    .body!!
                    .utf8()
                    .contains("Tomaten-Gnocchi"),
            )
            assertFalse(
                "nothing is renamed to a name taken",
                requests.any { it.method == "PATCH" && it.body!!.utf8().contains("\"name\"") },
            )
        }

    @Test
    fun `text pasted with a post is read instead of its caption`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"

            assertEquals("gnocchi", importer().run(ImportRequest(post, text = CAPTION)))
            val saved = requests.single { it.method == "PATCH" && it.body!!.utf8().contains("recipeIngredient") }.body!!.utf8()
            assertTrue(saved.contains("500 g Gnocchi"))
        }

    @Test
    fun `text pasted for a post the phone may not see is saved at once, without asking Mealie`() =
        runBlocking<Unit> {
            caption = null
            preview("Koch mit mir")

            assertEquals("gnocchi", importer().run(ImportRequest(post, text = CAPTION)))
            assertEquals(emptyList<String>(), creations())
            val saved = requests.single { it.method == "PATCH" && it.body!!.utf8().contains("recipeIngredient") }.body!!.utf8()
            assertTrue(saved.contains("500 g Gnocchi"))
        }

    @Test
    fun `a sign-in page Mealie saved instead of the post asks for its text and leaves nothing behind`() =
        runBlocking<Unit> {
            caption = null
            signInPreview()
            try {
                importer().run(ImportRequest(post, useAi = true))
                fail("A sign-in page holds no recipe")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.PostUnreadable, failure.reason)
            }
            assertEquals("the AI never reads the sign-in page", listOf("/api/recipes/create/url/stream"), creations())
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
            assertFalse(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
        }

    @Test
    fun `pasted text beside a sign-in page becomes a recipe of its own, without the platform's logo`() =
        runBlocking<Unit> {
            caption = null
            signInPreview()

            assertEquals("gnocchi", importer().run(ImportRequest(post, text = "Linsensuppe mit Zitrone, das Rezept steht im Video")))
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
            assertFalse(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
            assertTrue(requests.any { it.method == "PATCH" && it.body!!.utf8().contains("Linsensuppe mit Zitrone") })
        }

    @Test
    fun `a post the phone read keeps its photo when Mealie is shown the sign-in page`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"
            signInPreview()

            assertEquals("gnocchi", importer().run(ImportRequest(post)))
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
        }

    @Test
    fun `a post read without a title keeps the name Mealie numbered for it`() =
        runBlocking<Unit> {
            caption = "Zutaten:\n- 500 g Gnocchi\n\nZubereitung:\n1. Gnocchi braten."
            details["gnocchi"] = recipe("gnocchi", "Rezept (1)", ingredients = emptyList(), steps = emptyList())
            takenNames += "Rezept"

            assertEquals("gnocchi", importer().run(ImportRequest(post)))
            val saved = requests.filter { it.method == "PATCH" }.map { it.body!!.utf8() }
            assertFalse("the name is not taken back", saved.any { it.contains("\"name\":\"Rezept\"") || it.contains("Rezept (2)") })
            assertTrue(saved.any { it.contains("500 g Gnocchi") })
        }

    @Test
    fun `with AI switched on, a website still goes through Mealie's regular import`() =
        runBlocking<Unit> {
            assertEquals("gnocchi", importer().run(ImportRequest(PAGE, useAi = true)))
            assertEquals(listOf("/api/recipes/create/url/stream"), creations())
        }

    @Test
    fun `text pasted for a website that lists its ingredients and steps is read without the AI`() =
        runBlocking<Unit> {
            assertEquals("gnocchi", importer().run(ImportRequest(PAGE, text = CAPTION, useAi = true)))
            assertEquals(emptyList<String>(), creations())
            assertTrue(requests.any { it.method == "PATCH" && it.body!!.utf8().contains("500 g Gnocchi") })
        }

    @Test
    fun `when Mealie's AI fails on a caption Aroma can read in part, Aroma reads it itself`() =
        runBlocking<Unit> {
            caption = "Tomaten-Gnocchi\n\nZutaten:\n- 500 g Gnocchi\n- 250 g Kirschtomaten\n\nAlles Weitere im Video!"
            aiFails = true

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)))
            assertEquals(listOf("/api/recipes/create/ai/stream"), creations())
            assertTrue(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
        }

    @Test
    fun `a recipe saved before the request went out is not taken for it`() =
        runBlocking<Unit> {
            // Saved by the import queued before this one, while this one waited.
            listed.add(0, summary("id-frueher", "frueheres-rezept"))
            caption = PROSE
            cut = Cut("ai", arrives = "gnocchi")

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)))
        }

    @Test
    fun `a page Mealie can only read as a link preview leaves nothing behind`() =
        runBlocking<Unit> {
            details["vorschau"] =
                recipe(
                    "vorschau",
                    "Blog",
                    ingredients = listOf("Could not detect ingredients"),
                    steps = listOf("Could not detect instructions"),
                )
            answers = "vorschau"
            try {
                importer().run(ImportRequest(PAGE))
                fail("The page holds no recipe")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.NoRecipe, failure.reason)
            }
            assertTrue(requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/recipes/vorschau" })
        }

    @Test
    fun `a recipe Mealie saves after the connection dropped is picked up`() =
        runBlocking<Unit> {
            cut = Cut("url", arrives = "gnocchi")
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(PAGE)) { steps += it }

            assertEquals("gnocchi", slug)
            assertTrue(ImportStep.StillWorking in steps)
        }

    @Test
    fun `a post whose AI import was cut off keeps its link and photo once the recipe arrives`() =
        runBlocking<Unit> {
            caption = PROSE
            cut = Cut("ai", arrives = "gnocchi")

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)))
            assertTrue(requests.any { it.method == "PATCH" && it.body!!.utf8().contains(post) })
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
        }

    @Test
    fun `a recipe someone else adds meanwhile is not taken for the dropped import`() =
        runBlocking<Unit> {
            cut = Cut("url", arrives = "gnocchi")
            // Another household member saves a recipe while Aroma waits; it must not be mistaken for the import.
            listed.add(0, summary("id-fremd", "fremdes-rezept", link = PAGE, user = "someone-else"))

            assertEquals("gnocchi", importer().run(ImportRequest(PAGE)))
        }

    @Test
    fun `a post whose link import was cut off waits for Mealie instead of saving a second recipe`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"
            cut = Cut("url", arrives = null)
            try {
                importer(patience = 200.milliseconds).run(ImportRequest(post))
                fail("Nothing arrived, so the import cannot succeed")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.NoAnswer, failure.reason)
            }
            assertFalse(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
        }

    @Test
    fun `when no recipe turns up the import says Mealie did not answer, and creates nothing itself`() =
        runBlocking<Unit> {
            caption = PROSE
            cut = Cut("ai", arrives = null)
            try {
                importer(patience = 200.milliseconds).run(ImportRequest(post, useAi = true))
                fail("Nothing arrived, so the import cannot succeed")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.NoAnswer, failure.reason)
            }
            assertFalse(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
        }

    @Test
    fun `checks that fail for a moment, as on a phone between networks, do not end the wait`() =
        runBlocking<Unit> {
            cut = Cut("url", arrives = "gnocchi")
            failingChecks = 3

            assertEquals("gnocchi", importer().run(ImportRequest(PAGE)))
        }

    @Test
    fun `without any connection the import says so at once instead of waiting`() =
        runBlocking<Unit> {
            server.close()
            try {
                importer(patience = 1000.seconds).run(ImportRequest(PAGE))
                fail("The server is gone, so the import cannot succeed")
            } catch (_: AromaError.Offline) {
                // Expected, and quickly: the patience above would otherwise hold the test for minutes.
            }
        }

    @Test
    fun `an import picked up after Android ended the app waits for its recipe and sends nothing again`() =
        runBlocking<Unit> {
            finishing = Cut("ai", arrives = "gnocchi")

            assertEquals("gnocchi", importer().resume(ImportRequest(post, useAi = true), known, before = known, onSending = {}) { })
            assertEquals(emptyList<String>(), creations())
        }

    @Test
    fun `a picked-up import removes only the link preview it made itself`() =
        runBlocking<Unit> {
            val placeholders = { slug: String ->
                recipe(slug, "Instagram-Post", listOf("Could not detect ingredients"), listOf("Could not detect instructions"))
            }
            listOf("vorschau", "fremde-vorschau", "fruehere-vorschau").forEach { details[it] = placeholders(it) }
            listed.add(0, summary("id-fruehere-vorschau", "fruehere-vorschau", link = post))
            listed.add(0, summary("id-fremde-vorschau", "fremde-vorschau", link = post, user = "someone-else"))
            listed.add(0, summary("id-vorschau", "vorschau", link = post))
            finishing = Cut("ai", arrives = "gnocchi")
            val before = known + "id-fruehere-vorschau"
            val last = before + "id-fremde-vorschau" + "id-vorschau"

            assertEquals("gnocchi", importer().resume(ImportRequest(post, useAi = true), last, before, onSending = {}) { })
            val deleted = requests.filter { it.method == "DELETE" }.map { it.url.encodedPath }
            assertEquals(listOf("/api/recipes/vorschau"), deleted)
            assertTrue(requests.any { it.method == "PUT" && it.url.encodedPath == "/api/recipes/gnocchi/image" })
        }

    private val post get() = "http://www.instagram.com:${server.port}/p/AromaTest02/"

    private fun creations() = requests.map { it.url.encodedPath }.filter { it.startsWith("/api/recipes/create/") }

    private suspend fun RecipeImporter.run(request: ImportRequest, onStep: (ImportStep) -> Unit = {}) =
        import(request, onSending = {}, onStep = onStep)

    private fun importer(patience: Duration = 2.seconds): RecipeImporter {
        // Every host is the test server, so the post keeps its real address.
        val client = OkHttpClient.Builder().dns(Dns { InetAddress.getAllByName(server.hostName).toList() }).build()
        val posts = SocialPosts(client) { "Test browser" }
        return RecipeImporter(env.recipes, posts, untitled = { "Rezept" }, patience, checkEvery = 10.milliseconds)
    }

    private fun answer(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path == "/p/AromaTest02/" -> {
                caption?.let { html(postPage(it)) } ?: MockResponse
                    .Builder()
                    .code(302)
                    .addHeader("Location", "/accounts/login/")
                    .build()
            }

            path == "/p/AromaTest02/embed/captioned/" -> {
                // Without a caption the embed is the page a browser gets, which loads the post by script.
                html(caption?.let(::embedPage) ?: "", inBody = true)
            }

            path == "/accounts/login/" -> {
                html("""<meta property="og:title" content="Instagram">""")
            }

            path == "/photo.jpg" -> {
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(byteArrayOf(1, 2, 3)))
                    .build()
            }

            request.method == "GET" && path == "/api/recipes" -> {
                list()
            }

            path == "/api/groups/self" -> {
                json("""{"aiProviderSettings":{"aiEnabled":true}}""")
            }

            request.method == "POST" && path == "/api/recipes" -> {
                json("\"gnocchi\"")
            }

            request.method == "POST" && path.startsWith("/api/recipes/create/") -> {
                val planned = cut
                if (planned != null && path == "/api/recipes/create/${planned.endpoint}/stream") {
                    cut = null
                    finishing = planned
                    cutStream()
                } else if (aiFails && path.contains("/ai/")) {
                    sse("event: error\ndata: {\"message\":\"The AI provider did not answer\"}\n\n")
                } else if (linkFails && path.contains("/url/")) {
                    sse("event: error\ndata: {\"message\":\"No recipe was found in the provided source\"}\n\n")
                } else {
                    val slug = linkAnswers?.takeIf { path.contains("/url/") } ?: answers
                    sse("event: done\ndata: {\"slug\":\"$slug\"}\n\n")
                }
            }

            request.method == "GET" && path.startsWith("/api/media/recipes/") -> {
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "image/webp")
                    .body(Buffer().write(byteArrayOf(4, 5, 6)))
                    .build()
            }

            request.method == "GET" && path.startsWith("/api/recipes/") -> {
                details[path.removePrefix("/api/recipes/")]?.let(::json) ?: MockResponse.Builder().code(404).build()
            }

            request.method == "PATCH" && takenNames.any { request.body!!.utf8().contains("\"name\":\"$it\"") } -> {
                MockResponse
                    .Builder()
                    .code(400)
                    .body("""{"detail":{"message":"Recipe already exists"}}""")
                    .build()
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
                // A recipe read from a page keeps its link; one built from a post's text has none yet.
                listed.add(0, summary("id-${pending.arrives}", pending.arrives, link = PAGE.takeIf { pending.endpoint == "url" }))
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

    private fun html(content: String, inBody: Boolean = false) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body(if (inBody) "<html><body>$content</body></html>" else "<html><head>$content</head><body></body></html>")
            .build()

    /** The post's embed as Instagram serves it to Aroma: the caption and the photo in the page. */
    private fun embedPage(caption: String) =
        """<img class="EmbeddedMediaImage" alt="" src="http://www.instagram.com:${server.port}/photo.jpg">""" +
            """<div class="Caption"><a class="CaptionUsername" href="/kochmitmir/">kochmitmir</a><br /><br />""" +
            caption.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br />") +
            """<div class="CaptionComments"><a href="#">View all comments</a></div></div>"""

    /** The post's page as Instagram shows it: the caption in the link preview, framed by the platform. */
    private fun postPage(caption: String) =
        """<meta property="og:title" content="Koch mit mir on Instagram: &quot;${caption.replace("\n", "&#10;")}&quot;">""" +
            """<meta property="og:image" content="http://www.instagram.com:${server.port}/photo.jpg">"""

    /** Mealie's regular import saves the post's link preview: the whole caption as the name, placeholders, the photo. */
    private fun preview(caption: String) {
        val name = "Koch mit mir on Instagram: \\\"${caption.replace("\n", "\\n")}\\\""
        details["vorschau"] =
            recipe("vorschau", name, ingredients = listOf("Could not detect ingredients"), steps = listOf("Could not detect instructions"))
        linkAnswers = "vorschau"
    }

    /** What Mealie saves when the platform shows the server its sign-in page too. */
    private fun signInPreview() {
        details["vorschau"] =
            recipe(
                "vorschau",
                "Instagram",
                ingredients = listOf("Could not detect ingredients"),
                steps = listOf("Could not detect instructions"),
            )
        linkAnswers = "vorschau"
    }

    private fun summary(id: String, slug: String, link: String? = null, user: String = USER) =
        """{"id":"$id","slug":"$slug","name":"$slug","userId":"$user","orgURL":${link?.let { "\"$it\"" }}}"""

    private fun recipe(slug: String, name: String, ingredients: List<String>, steps: List<String>): String {
        val ingredientJson = ingredients.joinToString(",") { """{"note":"$it","display":"$it"}""" }
        val stepJson = steps.joinToString(",") { """{"text":"$it"}""" }
        return """{"id":"id-$slug","slug":"$slug","name":"$name","description":"","image":null,"orgURL":null,""" +
            """"recipeIngredient":[$ingredientJson],"recipeInstructions":[$stepJson]}"""
    }

    private companion object {
        const val USER = "user-1"
        const val PAGE = "https://www.example.com/gnocchi"

        /** A recipe told in sentences, as many captions tell it: no list Aroma could sort. */
        const val PROSE =
            "Tomaten-Gnocchi für zwei: Gnocchi in Butter goldbraun braten, eine Handvoll Kirschtomaten halbieren und " +
                "mitbraten, mit einem Schuss Sahne ablöschen und mit Parmesan servieren."
        val CAPTION =
            """
            Tomaten-Gnocchi

            Zutaten:
            - 500 g Gnocchi
            - 250 g Kirschtomaten

            Zubereitung:
            1. Gnocchi braten.
            2. Tomaten dazu.
            """.trimIndent()
    }
}

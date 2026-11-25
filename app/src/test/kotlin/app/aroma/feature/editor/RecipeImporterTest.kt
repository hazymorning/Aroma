package app.aroma.feature.editor

import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason
import app.aroma.data.TestEnvironment
import app.aroma.data.address
import app.aroma.data.eventStream
import app.aroma.data.json
import app.aroma.data.mealieServer
import app.aroma.data.remote.SocialPosts
import app.aroma.data.session.Session
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.Dns
import okhttp3.OkHttpClient
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
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
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val server = mealieServer { answer(it.also(requests::add)) }
    private val env = TestEnvironment()

    /** Recipes on the server, newest first. */
    private val listed = CopyOnWriteArrayList(listOf(summary("old-1", "aelteres-rezept")))
    private val known = setOf("old-1")
    private val details = mutableMapOf("gnocchi" to recipe("gnocchi", "Tomaten-Gnocchi", listOf("500 g Gnocchi"), listOf("Braten.")))

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

    /** A recipe someone else in the household saves while Aroma waits for its own. */
    @Volatile
    private var savedMeanwhile: String? = null

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
        runBlocking { env.sessions.start(Session(server.address, token = "t", userId = USER, username = "mara")) }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `with AI a post's caption goes to Mealie's AI as text alone, and its link and photo follow`() =
        runBlocking<Unit> {
            // Told in sentences, with signs that would have Mealie take it for a web page.
            caption = "$PROSE Mit Liebe <3 und Basilikum -> fertig!"
            // Mealie's AI copies the caption's emoji into the recipe.
            details["gnocchi"] =
                recipe("gnocchi", "Tomaten-Gnocchi 🍅", listOf("🥔 500 g Gnocchi", "🍅 250 g Kirschtomaten"), listOf("🔥 Braten."))
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(post, useAi = true, translateTo = "en-US")) { steps += it }

            assertEquals("gnocchi", slug)
            assertEquals("Mealie's AI makes the recipe, as the group's prompts ask", listOf(AI_IMPORT), creations())
            val sent = bodies("POST", AI_IMPORT).single()
            assertTrue("the caption goes to the AI", sent.contains("Gnocchi in Butter goldbraun braten"))
            assertTrue("as plain text, which Mealie reads without an extra pass", sent.contains("‹3 und Basilikum -› fertig"))
            assertTrue("to be translated", sent.contains("name=\"translateLanguage\"") && sent.contains("en-US"))
            assertFalse("Mealie does not load the post itself", sent.contains("name=\"url\""))
            assertFalse("the post's page is not loaded, its embed has it all", requests.any { it.url.encodedPath == "/p/AromaTest02/" })
            assertTrue(asked("PUT", RECIPE_PHOTO))
            val finished = bodies("PATCH", RECIPE).single()
            assertTrue(finished.contains(post))
            assertTrue(finished.contains("\"name\":\"Tomaten-Gnocchi\"") && finished.contains("\"note\":\"500 g Gnocchi\""))
            assertTrue(finished.contains("\"text\":\"Braten.\""))
            assertFalse("the emoji leave the recipe", finished.contains("🍅") || finished.contains("🔥"))
            assertEquals(ImportStep.ReadingPost, steps.first())
            assertTrue(ImportStep.AddingPhoto in steps)
        }

    @Test
    fun `a post whose text holds no recipe is read by Mealie, and its caption kept as the recipe's text`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"
            aiFails = true
            preview("Das Rezept steht im Video! #gnocchi")

            val slug = importer().run(ImportRequest(post, useAi = true))

            assertEquals("gnocchi", slug)
            assertEquals(listOf(AI_IMPORT, LINK_IMPORT), creations())
            val saved = bodies("PATCH", RECIPE).single { "recipeIngredient" in it }
            assertTrue(saved.contains("Das Rezept steht im Video!"))
            assertTrue("no placeholders", saved.contains("\"recipeIngredient\":[]"))
            assertTrue("the preview gives its photo and goes", asked("DELETE", PREVIEW))
        }

    @Test
    fun `without AI a post becomes one recipe from its text, with its link and photo`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(post)) { steps += it }

            assertEquals("gnocchi", slug)
            assertEquals(emptyList<String>(), creations())
            val saved = bodies("PATCH", RECIPE).single { "recipeIngredient" in it }
            assertTrue(saved.contains("500 g Gnocchi") && saved.contains(post))
            assertEquals(listOf(ImportStep.ReadingPost, ImportStep.Saving, ImportStep.AddingPhoto), steps)
        }

    @Test
    fun `a post the phone may not see is read by Mealie, and its link preview becomes the recipe`() =
        runBlocking<Unit> {
            caption = null
            preview(CAPTION)
            // Imported before: Mealie numbers the name of the new recipe, which nothing renames back.
            takenNames += "Tomaten-Gnocchi"
            val steps = mutableListOf<ImportStep>()

            val slug = importer().run(ImportRequest(post)) { steps += it }

            assertEquals("gnocchi", slug)
            assertEquals(listOf(LINK_IMPORT), creations())
            val created = bodies("POST", NEW_RECIPE).single()
            assertTrue("named by the caption", created.contains("Tomaten-Gnocchi"))
            assertTrue("nothing is renamed to a name taken", bodies("PATCH", RECIPE).none { "\"name\"" in it })
            val saved = bodies("PATCH", RECIPE).single { "recipeIngredient" in it }
            assertTrue(saved.contains("500 g Gnocchi") && saved.contains(post))
            assertTrue(asked("GET", PREVIEW_IMAGE))
            assertTrue(asked("PUT", RECIPE_PHOTO))
            assertTrue(asked("DELETE", PREVIEW))
            assertEquals(listOf(ImportStep.ReadingPost, ImportStep.Waiting, ImportStep.Saving, ImportStep.AddingPhoto), steps)
        }

    @Test
    fun `with AI, a caption in prose Mealie read goes to the AI as text, and the new recipe takes the preview's photo`() =
        runBlocking<Unit> {
            caption = null
            preview(PROSE)

            val slug = importer().run(ImportRequest(post, useAi = true))

            assertEquals("gnocchi", slug)
            assertEquals(listOf(LINK_IMPORT, AI_IMPORT), creations())
            val sent = bodies("POST", AI_IMPORT).single()
            assertTrue(sent.contains("Gnocchi in Butter goldbraun braten"))
            assertFalse("the AI never gets the post's link, which shows it the sign-in page", sent.contains("name=\"url\""))
            assertTrue(asked("GET", PREVIEW_IMAGE))
            assertTrue(asked("PUT", RECIPE_PHOTO))
            assertTrue(asked("DELETE", PREVIEW))
            assertTrue(bodies("PATCH", RECIPE).any { post in it })
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
            assertFalse(asked("POST", NEW_RECIPE))
        }

    @Test
    fun `text pasted for a post the phone may not see is saved at once, without asking Mealie`() =
        runBlocking<Unit> {
            caption = null
            preview("Koch mit mir")

            assertEquals("gnocchi", importer().run(ImportRequest(post, text = CAPTION)))
            assertEquals(emptyList<String>(), creations())
            val saved = bodies("PATCH", RECIPE).single { "recipeIngredient" in it }
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
            assertEquals("the AI never reads the sign-in page", listOf(LINK_IMPORT), creations())
            assertTrue(asked("DELETE", PREVIEW))
            assertFalse(asked("POST", NEW_RECIPE))
        }

    @Test
    fun `pasted text becomes a recipe with the post's photo, not the platform's logo, when Mealie is shown the sign-in page`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"
            signInPreview()

            assertEquals("gnocchi", importer().run(ImportRequest(post, text = "Linsensuppe mit Zitrone, das Rezept steht im Video")))
            assertTrue(bodies("PATCH", RECIPE).any { "Linsensuppe mit Zitrone" in it })
            assertTrue(asked("DELETE", PREVIEW))
            val photo = requests.single { it.method == "PUT" && it.url.encodedPath == RECIPE_PHOTO }.body!!
            assertTrue("the photo the phone read", photo.indexOf(POST_PHOTO) >= 0)
            assertTrue("not the one Mealie saved", photo.indexOf(PREVIEW_PHOTO) < 0)
        }

    @Test
    fun `with AI switched on, a website still goes through Mealie's regular import, and text pasted for it to the AI with its link`() =
        runBlocking<Unit> {
            assertEquals("gnocchi", importer().run(ImportRequest(PAGE, useAi = true)))
            assertEquals(listOf(LINK_IMPORT), creations())

            assertEquals("gnocchi", importer().run(ImportRequest(PAGE, text = CAPTION, useAi = true)))
            val sent = bodies("POST", AI_IMPORT).single()
            assertTrue(sent.contains("- 500 g Gnocchi") && sent.contains(PAGE))
        }

    @Test
    fun `when Mealie's AI fails on a caption Aroma can read in part, Aroma reads it itself`() =
        runBlocking<Unit> {
            caption = "Tomaten-Gnocchi\n\nZutaten:\n- 500 g Gnocchi\n- 250 g Kirschtomaten\n\nAlles Weitere im Video!"
            aiFails = true

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)))
            assertEquals(listOf(AI_IMPORT), creations())
            assertTrue(asked("POST", NEW_RECIPE))
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
            assertTrue(asked("DELETE", PREVIEW))
        }

    @Test
    fun `a post whose AI import was cut off waits through failing checks for its own recipe, which keeps the post's link and photo`() =
        runBlocking<Unit> {
            caption = PROSE
            cut = Cut("ai", arrives = "gnocchi")
            failingChecks = 1
            savedMeanwhile = summary("id-fremd", "fremdes-rezept", user = "someone-else")
            val steps = mutableListOf<ImportStep>()

            assertEquals("gnocchi", importer().run(ImportRequest(post, useAi = true)) { steps += it })
            assertTrue(ImportStep.StillWorking in steps)
            assertTrue(bodies("PATCH", RECIPE).any { post in it })
            assertTrue(asked("PUT", RECIPE_PHOTO))
        }

    @Test
    fun `a post whose link import was cut off waits for Mealie instead of saving a second recipe`() =
        runBlocking<Unit> {
            caption = "Das Rezept steht im Video! #gnocchi"
            cut = Cut("url", arrives = null)
            try {
                importer(patience = 20.milliseconds).run(ImportRequest(post))
                fail("Nothing arrived, so the import cannot succeed")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.NoAnswer, failure.reason)
            }
            assertFalse(asked("POST", NEW_RECIPE))
        }

    @Test
    fun `when no recipe turns up the import says Mealie did not answer, and creates nothing itself`() =
        runBlocking<Unit> {
            caption = PROSE
            cut = Cut("ai", arrives = null)
            try {
                importer(patience = 20.milliseconds).run(ImportRequest(post, useAi = true))
                fail("Nothing arrived, so the import cannot succeed")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals(Reason.NoAnswer, failure.reason)
            }
            assertFalse(asked("POST", NEW_RECIPE))
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
            assertEquals(listOf(PREVIEW), deleted)
            assertTrue(asked("PUT", RECIPE_PHOTO))
        }

    private val post get() = "http://www.instagram.com:${server.port}/p/AromaTest02/"

    private fun creations() = requests.map { it.url.encodedPath }.filter { it.startsWith("/api/recipes/create/") }

    /** Whether Aroma sent [method] to [path]. */
    private fun asked(method: String, path: String) = requests.any { it.method == method && it.url.encodedPath == path }

    /** What Aroma sent with [method] to [path], each time it did. */
    private fun bodies(method: String, path: String) =
        requests.filter { it.method == method && it.url.encodedPath == path }.map { it.body!!.utf8() }

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
                image("image/jpeg", POST_PHOTO)
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
                    eventStream("event: error\ndata: {\"message\":\"The AI provider did not answer\"}\n\n")
                } else if (linkFails && path.contains("/url/")) {
                    eventStream("event: error\ndata: {\"message\":\"No recipe was found in the provided source\"}\n\n")
                } else {
                    val slug = linkAnswers?.takeIf { path.contains("/url/") } ?: answers
                    eventStream("event: done\ndata: {\"slug\":\"$slug\"}\n\n")
                }
            }

            request.method == "GET" && path.startsWith("/api/media/recipes/") -> {
                image("image/webp", PREVIEW_PHOTO)
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
            if (checksAfterCut == failingChecks + 1) savedMeanwhile?.let { listed.add(0, it) }
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

    private fun image(type: String, bytes: ByteString) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", type)
            .body(Buffer().write(bytes))
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

    /** What Mealie saves when the platform shows the server its sign-in page too, the platform's logo as its photo. */
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

    private fun recipe(
        slug: String,
        name: String,
        ingredients: List<String>,
        steps: List<String>,
        link: String? = null,
    ): String {
        val ingredientJson = ingredients.joinToString(",") { """{"note":"$it","display":"$it"}""" }
        val stepJson = steps.joinToString(",") { """{"text":"$it"}""" }
        return """{"id":"id-$slug","slug":"$slug","name":"$name","description":"","image":null,"orgURL":${link?.let { "\"$it\"" }},""" +
            """"recipeIngredient":[$ingredientJson],"recipeInstructions":[$stepJson]}"""
    }

    private companion object {
        const val USER = "user-1"
        const val PAGE = "https://www.example.com/gnocchi"

        const val AI_IMPORT = "/api/recipes/create/ai/stream"
        const val LINK_IMPORT = "/api/recipes/create/url/stream"
        const val NEW_RECIPE = "/api/recipes"
        const val RECIPE = "/api/recipes/gnocchi"
        const val RECIPE_PHOTO = "/api/recipes/gnocchi/image"

        /** The link preview Mealie saves, and its photo. */
        const val PREVIEW = "/api/recipes/vorschau"
        const val PREVIEW_IMAGE = "/api/media/recipes/id-vorschau/images/original.webp"

        /** The photo of the post as the phone reads it, and the one Mealie saved with its link preview. */
        val POST_PHOTO = byteArrayOf(1, 2, 3).toByteString()
        val PREVIEW_PHOTO = byteArrayOf(4, 5, 6).toByteString()

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

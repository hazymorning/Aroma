package app.aroma.data

import app.aroma.data.remote.SocialPosts
import app.aroma.data.repository.MealieVersion
import app.aroma.feature.editor.ImportRequest
import app.aroma.feature.editor.ImportStep
import app.aroma.feature.editor.RecipeImporter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress

/**
 * Imports against a real Mealie server (see [LiveMealie]). The post comes from a stand-in for
 * Instagram, which shows its caption only in the link preview, as Instagram does.
 */
@RunWith(RobolectricTestRunner::class)
class LiveImportTest {
    private val platform = MockWebServer()
    private lateinit var env: TestEnvironment
    private lateinit var known: Set<String>
    private val created = mutableListOf<String>()

    @Before
    fun setUp() {
        LiveMealie.assumeConfigured()
        platform.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.url.encodedPath) {
                        "/p/AromaTest01/" -> {
                            page()
                        }

                        else -> {
                            MockResponse
                                .Builder()
                                .addHeader("Content-Type", "image/png")
                                .body(Buffer().write(PHOTO))
                                .build()
                        }
                    }
            }
        platform.start()
        env = TestEnvironment()
        live.signIn(env)
        known = runBlocking { env.recipes.newestRecipeIds() }
    }

    @After
    fun tearDown() {
        try {
            if (::env.isInitialized) runBlocking { created.forEach { env.recipes.delete(it) } }
        } finally {
            if (::env.isInitialized) env.close()
            platform.close()
        }
    }

    @Test
    fun `without AI a post becomes one recipe, sorted from its caption, with its link and photo`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()

            val slug = importer().import(ImportRequest(post), onSending = {}) { steps += it }
            created += slug

            val recipe = env.recipes.recipeJson(slug)
            assertEquals("Cremige Tomaten-Gnocchi in nur 15 Minuten", recipe.text("name"))
            assertEquals(
                listOf(
                    "500 g Gnocchi",
                    "250 g Kirschtomaten",
                    "1 Knoblauchzehe",
                    "100 ml Sahne",
                    "30 g Parmesan",
                    "Basilikum, Salz, Pfeffer",
                ),
                recipe.ingredientNotes(),
            )
            assertEquals(4, recipe["recipeInstructions"]!!.jsonArray.size)
            assertEquals(post, recipe.text("orgURL"))
            assertNotNull("the post's photo came along", recipe.text("image"))
            assertEquals(listOf(ImportStep.ReadingPost, ImportStep.Saving, ImportStep.AddingPhoto), steps)
            assertOnlyNewRecipe(recipe)
        }

    @Test
    fun `a page without a recipe fails as an import error after reporting progress, and leaves nothing behind`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()
            try {
                // A page with no recipe on it, which Mealie fetches from the internet.
                created += importer().import(ImportRequest("http://example.com/"), onSending = {}) { steps += it }
                fail("example.com holds no recipe")
            } catch (_: AromaError.ImportFailed) {
                // Mealie 3 reports its progress on the way; Mealie 2 has no stream to report it on.
                val streams = MealieVersion.isAtLeast(env.sessions.current!!.serverVersion, 3, 0)
                assertEquals(streams, steps.any { it is ImportStep.Server })
                assertFalse(steps.any { it is ImportStep.Server && it.message.isBlank() })
            }
            assertEquals(known, env.recipes.newestRecipeIds())
        }

    private val post get() = "http://www.instagram.com:${platform.port}/p/AromaTest01/"

    private fun importer(): RecipeImporter {
        // The platform's hosts all lead to the stand-in, so the post keeps a real Instagram address.
        val client = OkHttpClient.Builder().dns(Dns { InetAddress.getAllByName(platform.hostName).toList() }).build()
        return RecipeImporter(env.recipes, SocialPosts(client) { "Test browser" }, untitled = { "Importiertes Rezept" })
    }

    /** The import saved this one recipe and nothing else: no link preview, no draft. */
    private suspend fun assertOnlyNewRecipe(recipe: JsonObject) {
        assertEquals(setOf(recipe.text("id")), env.recipes.newestRecipeIds() - known)
    }

    private fun page() =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body(
                """
                <html><head>
                <meta property="og:title" content="Koch mit mir on Instagram: &quot;${CAPTION.escaped()}&quot;">
                <meta property="og:description" content="1,234 likes, 56 comments - kochmitmir on November 20, 2026: &quot;${CAPTION.take(
                    60,
                ).escaped()}&quot;. ">
                <meta property="og:image" content="http://www.instagram.com:${platform.port}/photo.png">
                <title>Instagram</title></head><body><div id="root">Instagram</div></body></html>
                """.trimIndent(),
            ).build()

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.ingredientNotes(): List<String?> =
        get("recipeIngredient")!!.jsonArray.map { it.jsonObject.text("note") ?: it.jsonObject.text("display") }

    companion object {
        private val live = LiveMealie()

        @JvmStatic
        @AfterClass
        fun signOut() = live.signOut()

        private val CAPTION =
            """
            Cremige Tomaten-Gnocchi 🍅 in nur 15 Minuten! Perfekt für den Feierabend 🙌

            Zutaten (2 Portionen):
            - 500 g Gnocchi
            - 250 g Kirschtomaten
            - 1 Knoblauchzehe
            - 100 ml Sahne
            - 30 g Parmesan
            - Basilikum, Salz, Pfeffer

            Zubereitung:
            1. Gnocchi in einer Pfanne mit etwas Öl goldbraun braten.
            2. Tomaten halbieren, Knoblauch hacken und 3 Minuten mitbraten.
            3. Sahne und Parmesan unterrühren, 2 Minuten köcheln lassen.
            4. Mit Salz, Pfeffer und Basilikum abschmecken.

            Speichert euch das Rezept! 💛
            #gnocchi #feierabendküche #pasta
            """.trimIndent()

        /** A photo of one pixel, enough for Mealie to store as the recipe's image. */
        private val PHOTO =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
                .decodeBase64()!!

        private fun String.escaped() =
            replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "&#10;")
    }
}

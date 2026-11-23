package app.aroma.data

import app.aroma.data.remote.SocialPosts
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

/**
 * Imports against a real Mealie server, configured like [LiveMealieTest]. The post comes from a
 * stand-in for Instagram, which shows its caption only in the link preview, as Instagram does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveImportTest {
    private val url: String? = System.getenv("MEALIE_TEST_URL")
    private val platform = MockWebServer()
    private lateinit var env: TestEnvironment
    private lateinit var known: Set<String>
    private val created = mutableListOf<String>()

    @Before
    fun setUp() {
        assumeTrue("MEALIE_TEST_URL is not set", !url.isNullOrBlank())
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
        runBlocking {
            val server = env.auth.probe(url!!.removePrefix("http://").removePrefix("https://"))
            env.auth.signIn(server, System.getenv("MEALIE_TEST_USER")!!, System.getenv("MEALIE_TEST_PASSWORD")!!)
            known = env.recipes.newestRecipeIds()
        }
    }

    @After
    fun tearDown() {
        if (::env.isInitialized) {
            runBlocking {
                created.forEach { slug -> runCatching { env.recipes.delete(slug) } }
                env.auth.signOut()
            }
            env.close()
        }
        platform.close()
    }

    @Test
    fun `without AI a post becomes one recipe, sorted from its caption, with its link and photo`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()

            val slug = importer().import(ImportRequest(post), known) { steps += it }
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
    fun `with AI the post goes to Mealie as its text, and only the finished recipe is saved`() =
        runBlocking<Unit> {
            assumeTrue("The server has no AI provider set up", env.recipes.canImportWithAi())

            val slug = importer().import(ImportRequest(post, useAi = true), known) { }
            created += slug

            val recipe = env.recipes.recipeJson(slug)
            assertEquals(6, recipe.ingredientNotes().size)
            assertEquals(4, recipe["recipeInstructions"]!!.jsonArray.size)
            assertEquals("the recipe links to the post", post, recipe.text("orgURL"))
            assertNotNull("the post's photo came along", recipe.text("image"))
            assertOnlyNewRecipe(recipe)
        }

    @Test
    fun `a page without a recipe fails as an import error after reporting progress, and leaves nothing behind`() =
        runBlocking<Unit> {
            val steps = mutableListOf<ImportStep>()
            try {
                created += importer().import(ImportRequest("https://example.com/"), known) { steps += it }
                fail("example.com holds no recipe")
            } catch (_: AromaError.ImportFailed) {
                val streams =
                    env.sessions.current!!
                        .serverVersion
                        .substringBefore('.')
                        .removePrefix("v")
                        .toInt() >= 3
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

    private companion object {
        val CAPTION =
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
        val PHOTO = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==".decodeBase64()!!

        private fun String.escaped() =
            replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "&#10;")
    }
}

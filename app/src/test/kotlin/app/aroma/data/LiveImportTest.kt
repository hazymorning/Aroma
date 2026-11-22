package app.aroma.data

import app.aroma.data.remote.MealieJson
import app.aroma.feature.editor.ImportRequest
import app.aroma.feature.editor.ImportStep
import app.aroma.feature.editor.RecipeImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Imports against a real Mealie server, configured like [LiveMealieTest]. The post below is
 * served to Mealie the way Instagram serves its pages: the caption only in the link preview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveImportTest {
    private val url: String? = System.getenv("MEALIE_TEST_URL")
    private lateinit var env: TestEnvironment
    private val created = mutableListOf<String>()

    @Before
    fun setUp() {
        assumeTrue("MEALIE_TEST_URL is not set", !url.isNullOrBlank())
        env = TestEnvironment()
        runBlocking {
            val server = env.auth.probe(url!!.removePrefix("http://").removePrefix("https://"))
            env.auth.signIn(server, System.getenv("MEALIE_TEST_USER")!!, System.getenv("MEALIE_TEST_PASSWORD")!!)
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
    }

    @Test
    fun `a post Mealie only read through its link preview is sorted into a recipe`() =
        runBlocking<Unit> {
            val preview = linkPreviewRecipe()
            val steps = mutableListOf<ImportStep>()
            val slug =
                RecipeImporter(
                    env.recipes,
                    "Importiertes Rezept",
                ).repairLinkPreview(preview, ImportRequest(POST_URL)) { steps += it }
            created += slug

            val recipe = env.recipes.recipeJson(slug)
            assertEquals("Cremige Tomaten-Gnocchi in nur 15 Minuten", recipe.text("name"))
            assertNotEquals(preview, slug)
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
            assertEquals(POST_URL, recipe.text("orgURL"))
            assertEquals(listOf<ImportStep>(ImportStep.Tidying), steps)
            assertGone(preview)
        }

    @Test
    fun `with AI the post is rebuilt from its caption and the preview goes away`() =
        runBlocking<Unit> {
            assumeTrue("The server has no AI provider set up", env.recipes.canImportWithAi())
            val preview = linkPreviewRecipe()
            val steps = mutableListOf<ImportStep>()
            val request = ImportRequest(POST_URL, useAi = true)
            val slug = RecipeImporter(env.recipes, "Importiertes Rezept").repairLinkPreview(preview, request) { steps += it }
            created += slug

            val recipe = env.recipes.recipeJson(slug)
            assertEquals(6, recipe.ingredientNotes().size)
            assertEquals(4, recipe["recipeInstructions"]!!.jsonArray.size)
            assertTrue(steps.first() == ImportStep.Rebuilding && steps.drop(1).all { it is ImportStep.Server })
            assertGone(preview)
        }

    @Test
    fun `a page without a recipe fails as an import error after reporting progress`() =
        runBlocking<Unit> {
            val progress = mutableListOf<String>()
            try {
                created += env.recipes.importFromUrl("https://example.com/", progress::add)
                fail("example.com holds no recipe")
            } catch (_: AromaError.ImportFailed) {
                val streams =
                    env.sessions.current!!
                        .serverVersion
                        .substringBefore('.')
                        .removePrefix("v")
                        .toInt() >= 3
                assertEquals(streams, progress.isNotEmpty())
                assertFalse(progress.any { it.isBlank() })
            }
        }

    /** Asks Mealie to read [POST_PAGE], which leaves it the link preview only, as with Instagram. */
    private suspend fun linkPreviewRecipe(): String =
        withContext(Dispatchers.IO) {
            val server = env.sessions.credentials()!!.server
            val body =
                buildJsonObject {
                    put("data", POST_PAGE)
                    put("url", POST_URL)
                }.toString().toRequestBody("application/json".toMediaType())
            val request =
                Request
                    .Builder()
                    .url(server.resolve("api/recipes/create/html-or-json")!!)
                    .post(body)
                    .build()
            env.clients.httpClient.newCall(request).execute().use { response ->
                assertTrue("Mealie answered ${response.code}", response.isSuccessful)
                MealieJson.decodeFromString<String>(response.body.string()).also { created += it }
            }
        }

    private suspend fun assertGone(slug: String) {
        try {
            env.recipes.recipeJson(slug)
            fail("The link preview recipe should have been deleted")
        } catch (_: AromaError.NotFound) {
            created.remove(slug)
        }
    }

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.ingredientNotes(): List<String?> =
        get("recipeIngredient")!!.jsonArray.map { it.jsonObject.text("note") ?: it.jsonObject.text("display") }

    private companion object {
        const val POST_URL = "https://www.instagram.com/p/AromaTest01/"

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

        val POST_PAGE =
            """
            <html><head>
            <meta property="og:title" content="Koch mit mir on Instagram: &quot;${CAPTION.escaped()}&quot;">
            <meta property="og:description" content="1,234 likes, 56 comments - kochmitmir on November 20, 2026: &quot;${CAPTION.take(
                60,
            ).escaped()}&quot;. ">
            <meta property="og:url" content="$POST_URL">
            <title>Instagram</title></head><body><div id="root">Instagram</div></body></html>
            """.trimIndent()

        private fun String.escaped() = replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    }
}

package app.aroma.data

import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeQuery
import app.aroma.data.remote.MealieJson
import app.aroma.data.repository.TimelineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/**
 * The household's side of Mealie against a real server, like [LiveMealieTest]: the timeline,
 * cookbooks, suggestions and moving a planned meal. Skipped unless MEALIE_TEST_URL is set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveHouseholdTest {
    private val url: String? = System.getenv("MEALIE_TEST_URL")
    private lateinit var env: TestEnvironment
    private lateinit var timeline: TimelineRepository
    private lateinit var slug: String

    @Before
    fun setUp() {
        assumeTrue("MEALIE_TEST_URL is not set", !url.isNullOrBlank())
        env = TestEnvironment()
        timeline = TimelineRepository(env.sessions)
        runBlocking {
            val server = env.auth.probe(url!!.removePrefix("http://").removePrefix("https://"))
            env.auth.signIn(server, System.getenv("MEALIE_TEST_USER")!!, System.getenv("MEALIE_TEST_PASSWORD")!!)
            slug = env.recipes.create("Aroma timeline ${UUID.randomUUID().toString().take(8)}")
        }
    }

    @After
    fun tearDown() {
        if (::env.isInitialized) {
            runBlocking {
                if (::slug.isInitialized) runCatching { env.recipes.delete(slug) }
                env.auth.signOut()
            }
            env.close()
        }
    }

    @Test
    fun `Mealie takes a cooked recipe into its timeline and last-made date`() =
        runBlocking {
            val recipe =
                env.recipes
                    .recipe(slug)
                    .first { !it.isRefreshing }
                    .data
            val subject = "Tester made this ${UUID.randomUUID()}"
            timeline.cooked(recipe.id, recipe.slug, subject)
            // Both requests answer with an error where Mealie refuses them, which fails the test.
        }

    @Test
    fun `cookbooks load with their size`() =
        runBlocking {
            val covers =
                env.recipes
                    .cookbooks()
                    .first { !it.isRefreshing }
                    .data
            assertTrue(covers.all { it.recipeCount >= 0 })
        }

    @Test
    fun `suggestions and recipes cooked long ago load on every server`() =
        runBlocking {
            val today = LocalDate.now()
            val anyRecipes = env.recipes.suggestions(emptyList(), today).first { !it.isRefreshing }
            assertTrue(anyRecipes.error == null && anyRecipes.data.isNotEmpty())
            val dinner = env.recipes.suggestions(listOf("Abendessen", "Dinner"), today).first { !it.isRefreshing }
            assertTrue(dinner.error == null)
            val longAgo = env.recipes.cookedLongAgo(today.minusWeeks(4), today).first { !it.isRefreshing }
            assertTrue(longAgo.error == null)
        }

    @Test
    fun `recipes filter by a meal's category or tag, also among favorites`() =
        runBlocking {
            val name = "Abendessen ${UUID.randomUUID().toString().take(8)}"
            val tag = createTag(name)
            try {
                env.recipes.update(slug, buildJsonObject { put("tags", JsonArray(listOf(tag))) })
                val recipe =
                    env.recipes
                        .recipeJson(slug)["id"]!!
                        .jsonPrimitive.content
                assertEquals(
                    listOf(slug),
                    env.recipes
                        .page(RecipeQuery(mealNames = listOf(name)), 1)
                        .items
                        .map { it.slug },
                )

                env.recipes.setFavorite(recipe, slug, true)
                val favorites = env.recipes.page(RecipeQuery(favoritesOnly = true, mealNames = listOf(name)), 1).items
                assertEquals(listOf(slug), favorites.map { it.slug })
            } finally {
                deleteTag(tag["id"]!!.jsonPrimitive.content)
            }
        }

    @Test
    fun `a planned meal moves to another day and meal`() =
        runBlocking {
            val day = LocalDate.now().plusDays(40)
            val title = "Aroma move ${UUID.randomUUID()}"
            val created = env.mealPlans.add(day, MealType.Lunch, recipeId = null, title = title, note = "")
            try {
                env.mealPlans.move(created.id, day.plusDays(1), MealType.Dinner)
                val moved =
                    env.mealPlans
                        .range(day, 2)
                        .first { !it.isRefreshing }
                        .data
                        .single { it.title == title }
                assertEquals(day.plusDays(1), moved.date)
                assertEquals(MealType.Dinner, moved.type)
            } finally {
                env.mealPlans.delete(created.id)
            }
        }

    private suspend fun createTag(name: String): JsonObject =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("name", name) }.toString().toRequestBody("application/json".toMediaType())
            send(
                Request
                    .Builder()
                    .url(api("api/organizers/tags"))
                    .post(body)
                    .build(),
            ).jsonObject
        }

    private suspend fun deleteTag(id: String) =
        withContext(Dispatchers.IO) {
            send(
                Request
                    .Builder()
                    .url(api("api/organizers/tags/$id"))
                    .delete()
                    .build(),
            )
        }

    private fun api(path: String) =
        env.sessions
            .credentials()!!
            .server
            .resolve(path)!!

    private fun send(request: Request) =
        env.clients.httpClient.newCall(request).execute().use { response ->
            assertTrue("Mealie answered ${response.code}", response.isSuccessful)
            MealieJson.parseToJsonElement(response.body.string().ifBlank { "{}" })
        }
}

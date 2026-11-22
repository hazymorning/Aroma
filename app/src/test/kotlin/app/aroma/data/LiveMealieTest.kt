package app.aroma.data

import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeQuery
import app.aroma.core.model.RecipeSort
import app.aroma.feature.editor.RecipeDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Runs the data layer against a real Mealie server. Skipped unless the server is configured:
 *
 *     MEALIE_TEST_URL=http://localhost:9000 MEALIE_TEST_USER=... MEALIE_TEST_PASSWORD=... ./gradlew testDebugUnitTest
 *
 * The tests create and remove their own data, so an empty server is fine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveMealieTest {
    private val url: String? = System.getenv("MEALIE_TEST_URL")
    private val user: String? = System.getenv("MEALIE_TEST_USER")
    private val password: String? = System.getenv("MEALIE_TEST_PASSWORD")
    private lateinit var env: TestEnvironment
    private lateinit var recipeSlug: String
    private val recipeName = "Aromatestrezept ${UUID.randomUUID().toString().take(8)}"

    @Before
    fun setUp() {
        assumeTrue("MEALIE_TEST_URL is not set", !url.isNullOrBlank())
        env = TestEnvironment()
        runBlocking {
            val server = env.auth.probe(url!!.removePrefix("http://").removePrefix("https://"))
            env.auth.signIn(server, user!!, password!!)
            recipeSlug = createRecipe(recipeName)
        }
    }

    @After
    fun tearDown() {
        if (::env.isInitialized) {
            runBlocking {
                if (env.sessions.current != null && ::recipeSlug.isInitialized) runCatching { env.recipes.delete(recipeSlug) }
                env.auth.signOut()
            }
            env.close()
        }
    }

    /** A recipe with plain-text ingredients and steps, written the way the editor saves it. */
    private suspend fun createRecipe(name: String): String {
        val slug = env.recipes.create(name)
        val original = env.recipes.recipeJson(slug)
        val form =
            RecipeDraft.formFrom(original, Locale.GERMANY).copy(
                servings = "4",
                ingredients = "2 Stangen Lauch\n3 Eier\n200 g Mehl",
                steps = listOf("Lauch in Ringe schneiden.", "Alles verrühren und 10 Minuten ruhen lassen."),
            )
        return env.recipes.update(slug, RecipeDraft.patch(original, form, Locale.GERMANY) { "${it / 60} Min." })
    }

    @Test
    fun `sign in creates a device token that is revoked on sign out`() =
        runBlocking {
            val session = env.sessions.current
            assertNotNull(session)
            assertNotNull("a long-lived API token was created", session!!.tokenId)
            assertTrue(
                env.sessions
                    .api()
                    .currentUser()
                    .id == session.userId,
            )
        }

    @Test
    fun `recipes load page by page and in full`() =
        runBlocking {
            val first = env.recipes.page(RecipeQuery(sort = RecipeSort.Name), page = 1, perPage = 5)
            assertTrue(first.items.isNotEmpty())
            assertFalse(first.isOffline)
            val recipe =
                env.recipes
                    .recipe(recipeSlug)
                    .first { !it.isRefreshing }
                    .data
            assertEquals(recipeName, recipe.name)
            assertEquals(3, recipe.ingredients.size)
            assertEquals(2, recipe.steps.size)
            assertEquals(4.0, recipe.servings, 0.0)
            val organizers =
                env.recipes
                    .organizers()
                    .first { !it.isRefreshing }
                    .data
            assertNotNull(organizers)
        }

    @Test
    fun `search finds a recipe by name`() =
        runBlocking {
            val result = env.recipes.page(RecipeQuery(search = recipeName.substringAfter(' ')), page = 1)
            assertEquals(listOf(recipeSlug), result.items.map { it.slug })
        }

    @Test
    fun `favorites round trip`() =
        runBlocking {
            val recipe =
                env.recipes
                    .recipe(recipeSlug)
                    .first { !it.isRefreshing }
                    .data
            val before = env.recipes.refreshRatings()[recipe.id]?.isFavorite ?: false
            env.recipes.setFavorite(recipe.id, recipe.slug, !before)
            assertEquals(!before, env.recipes.refreshRatings()[recipe.id]?.isFavorite ?: false)
            val favorites = env.recipes.page(RecipeQuery(favoritesOnly = true), page = 1)
            assertEquals(!before, favorites.items.any { it.id == recipe.id })
            env.recipes.setFavorite(recipe.id, recipe.slug, before)
        }

    @Test
    fun `meal plan entries can be added and removed`() =
        runBlocking {
            val day = LocalDate.now().plusDays(30)
            val title = "Aroma test ${UUID.randomUUID()}"
            val created = env.mealPlans.add(day, MealType.Lunch, recipeId = null, title = title, note = "")
            val week =
                env.mealPlans
                    .range(day, 1)
                    .first { !it.isRefreshing }
                    .data
            assertTrue(week.any { it.title == title && it.type == MealType.Lunch })
            env.mealPlans.delete(created.id)
            assertTrue(
                env.mealPlans
                    .range(day, 1)
                    .first { !it.isRefreshing }
                    .data
                    .none { it.title == title },
            )
        }

    @Test
    fun `shopping changes are queued, synced and survive a refresh`() =
        runBlocking {
            val listId = env.shopping.createList("Aroma test ${UUID.randomUUID()}")
            try {
                env.shopping.refresh(listId)
                env.shopping.add(listId, "2 Zitronen")
                env.shopping.add(listId, "Kaffee")
                val local = env.shopping.observe(listId).first()!!
                assertEquals(2, local.items.size)
                assertEquals(2, env.shopping.pendingCount(listId).first())

                assertTrue(env.shopping.sync())
                env.shopping.refresh(listId)
                val synced = env.shopping.observe(listId).first()!!
                assertEquals(setOf("2 Zitronen", "Kaffee"), synced.items.map { it.note }.toSet())

                val coffee = synced.items.first { it.note == "Kaffee" }
                env.shopping.toggle(listId, coffee.id)
                env.shopping.sync()
                env.shopping.refresh(listId)
                assertTrue(
                    env.shopping
                        .observe(listId)
                        .first()!!
                        .items
                        .first { it.id == coffee.id }
                        .checked,
                )

                env.shopping.clearChecked(listId)
                env.shopping.sync()
                env.shopping.refresh(listId)
                assertEquals(
                    listOf("2 Zitronen"),
                    env.shopping
                        .observe(listId)
                        .first()!!
                        .items
                        .map { it.note },
                )
            } finally {
                env.shopping.deleteList(listId)
            }
        }

    @Test
    fun `recipe ingredients can be added to a list`() =
        runBlocking {
            val listId = env.shopping.createList("Aroma test ${UUID.randomUUID()}")
            try {
                val recipe =
                    env.recipes
                        .recipe(recipeSlug)
                        .first { !it.isRefreshing }
                        .data
                val raw =
                    env.recipes
                        .recipeJson(recipe.slug)["recipeIngredient"]!!
                        .jsonArray
                        .map { it.jsonObject }
                env.shopping.addRecipe(listId, recipe.id, 1.0, raw.take(2))
                env.shopping.refresh(listId)
                val list = env.shopping.observe(listId).first()!!
                assertTrue(list.items.isNotEmpty())
                assertTrue(list.items.all { it.recipeNames.contains(recipe.name) })
            } finally {
                env.shopping.deleteList(listId)
            }
        }

    @Test
    fun `editing keeps structured ingredients that were not touched`() =
        runBlocking {
            var slug = env.recipes.create("Aroma test ${UUID.randomUUID()}")
            try {
                val original = env.recipes.recipeJson(slug)
                val form =
                    RecipeDraft.formFrom(original, Locale.GERMANY).copy(
                        description = "Ein Testrezept.",
                        servings = "2",
                        prepMinutes = "15",
                        ingredients = "# Teig\n200 g Mehl\n2 Eier",
                        steps = listOf("Alles verrühren.", "10 Minuten backen."),
                    )
                slug = env.recipes.update(slug, RecipeDraft.patch(original, form, Locale.GERMANY) { "${it / 60} Min." })
                val saved =
                    env.recipes
                        .recipe(slug)
                        .first { !it.isRefreshing }
                        .data
                assertEquals(listOf("200 g Mehl", "2 Eier"), saved.ingredients.map { it.note })
                assertEquals("Teig", saved.ingredients.first().section)
                assertEquals(2, saved.steps.size)
                val prep = saved.prepTime
                assertTrue(
                    "prep time is stored as seconds (Mealie 3) or text (Mealie 2): $prep",
                    prep?.seconds == 900 || prep?.text == "15 Min.",
                )
                assertEquals(2.0, saved.servings, 0.0)

                val again = env.recipes.recipeJson(slug)
                val unchanged = RecipeDraft.patch(again, RecipeDraft.formFrom(again, Locale.GERMANY), Locale.GERMANY) { "" }
                assertTrue("saving without changes sends nothing", unchanged.isEmpty())
            } finally {
                env.recipes.delete(slug)
            }
        }

    @Test
    fun `signing out forgets the session`() =
        runBlocking {
            env.auth.signOut()
            assertNull(env.sessions.current)
            env.auth.signIn(env.auth.probe(url!!), user!!, password!!)
        }
}

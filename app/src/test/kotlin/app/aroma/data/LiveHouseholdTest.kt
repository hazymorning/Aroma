package app.aroma.data

import app.aroma.core.model.MealType
import app.aroma.data.repository.TimelineRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        timeline = TimelineRepository(env.sessions, env.recipes)
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
    fun `a cooked recipe shows in the timeline with its own words`() =
        runBlocking {
            val recipe =
                env.recipes
                    .recipe(slug)
                    .first { !it.isRefreshing }
                    .data
            val subject = "Tester made this ${UUID.randomUUID()}"
            timeline.cooked(recipe.id, recipe.slug, subject)
            val event = timeline.page(1).events.first { it.subject == subject }
            assertEquals(recipe.id, event.recipe.id)
            assertEquals(recipe.name, event.recipe.name)
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
}

package app.aroma.feature.recipe

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.MealieJson
import app.aroma.data.session.Session
import app.aroma.feature.editor.RecipeReworker
import app.aroma.feature.editor.RecipeReworks
import app.aroma.testing.address
import app.aroma.testing.json
import app.aroma.testing.mealieServer
import app.aroma.testing.until
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/** Filing a recipe from its page: the kind of dish, categories and tags, saved as they are chosen. */
@RunWith(RobolectricTestRunner::class)
class RecipeFilingTest {
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val env = TestEnvironment()

    /** The recipe's categories and tags as Mealie keeps them. */
    @Volatile
    private var filed = """"recipeCategory":[],"tags":[]"""

    @Volatile
    private var takesChanges = true

    private val server =
        mealieServer { request ->
            val path = request.url.encodedPath
            val response =
                when {
                    path == "/api/recipes/pancakes" && request.method == "GET" -> {
                        json(recipe())
                    }

                    path == "/api/recipes/pancakes" && request.method == "PATCH" && takesChanges -> {
                        val patch = MealieJson.parseToJsonElement(request.body!!.utf8()).jsonObject
                        filed = """"recipeCategory":${patch["recipeCategory"]},"tags":${patch["tags"]}"""
                        json(recipe())
                    }

                    path == "/api/organizers/categories" && request.method == "GET" -> {
                        json("""{"items":[{"id":"c1","name":"Abendessen","slug":"abendessen"}]}""")
                    }

                    path == "/api/organizers/tags" && request.method == "POST" -> {
                        json("""{"id":"t1","name":"Schnell","slug":"schnell"}""")
                    }

                    path == "/api/organizers/tags" || path == "/api/organizers/tools" -> {
                        json("""{"items":[]}""")
                    }

                    else -> {
                        MockResponse.Builder().code(if (request.method == "PATCH") 500 else 404).build()
                    }
                }
            // Recorded once answered: a test that sees the request also sees what it changed.
            requests += request
            response
        }

    @Before
    fun setUp() {
        runBlocking { env.sessions.start(Session(serverUrl = server.address, token = "t", userId = "u", username = "mara")) }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `a tag named on the page is made in Mealie and offered from then on`() {
        val page = open()

        page.file(Filing(emptyList(), listOf(Organizer.new("Schnell"))))

        until {
            page
                .filing()
                .tags
                .singleOrNull()
                ?.id == "t1"
        }
        assertEquals(
            listOf("t1"),
            page.organizers.value!!
                .tags
                .map { it.id },
        )
        assertEquals(1, requests.count { it.method == "POST" })
    }

    @Test
    fun `when Mealie does not take it, the page says so and shows Mealie's filing again`() {
        val page = open()
        takesChanges = false

        page.file(Filing(emptyList(), emptyList()).toggle(Meal.Dinner, page.organizers.value!!.categories, "Abendessen"))
        assertEquals("shown at once", setOf(Meal.Dinner), page.filing().meals)
        until { requests.any { it.method == "PATCH" } && page.filing().meals.isEmpty() }

        // A page that was not listening meanwhile, as while Android made it anew, still hears of it.
        val events = CopyOnWriteArrayList<RecipeEvent>()
        page.viewModelScope.launch { page.events.collect { events += it } }
        until { events.isNotEmpty() }
        assertTrue(events.single() is RecipeEvent.Failed)
    }

    private fun open(): RecipeViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reworks = RecipeReworks(context, RecipeReworker(env.recipes, { context.resources }))
        val page =
            RecipeViewModel(
                "pancakes",
                env.recipes,
                reworks,
                env.shopping,
                env.mealPlans,
                env.sessions,
                env.settings,
                env.connection,
                SavedStateHandle(),
            )
        until { page.state.value.recipe != null }
        page.loadOrganizers()
        until {
            page.organizers.value
                ?.categories
                ?.isNotEmpty() == true
        }
        return page
    }

    private fun RecipeViewModel.filing() = state.value.recipe!!.let { Filing(it.categories, it.tags) }

    private fun recipe() = """{"id":"r1","slug":"pancakes","name":"Pancakes",$filed}"""
}

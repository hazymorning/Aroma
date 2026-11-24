package app.aroma.feature.recipe

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.MealieJson
import app.aroma.data.session.Session
import app.aroma.data.settings.SettingsRepository
import app.aroma.feature.editor.RecipeReworker
import app.aroma.feature.editor.RecipeReworks
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** Filing a recipe from its page: the kind of dish, categories and tags, saved as they are chosen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeFilingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }

    /** The recipe's categories and tags as Mealie keeps them. */
    @Volatile
    private var filed = """"recipeCategory":[],"tags":[]"""

    @Volatile
    private var takesChanges = true

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.url.encodedPath
                    return when {
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
                }
            }
        server.start()
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.url("/").toString().trimEnd('/'), token = "t", userId = "u", username = "mara"))
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `a kind of dish shows at once and is saved under the group's category of its name`() {
        val page = open()

        page.file(Filing(emptyList(), emptyList()).toggle(Meal.Dinner, page.organizers.value!!.categories, "Abendessen"))

        assertEquals(setOf(Meal.Dinner), page.filing().meals)
        until { requests.any { it.method == "PATCH" } }
        assertTrue(requests.none { it.method == "POST" })
        assertEquals(""""recipeCategory":[{"id":"c1","name":"Abendessen","slug":"abendessen"}],"tags":[]""", filed)
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
        val events = CopyOnWriteArrayList<RecipeEvent>()
        page.viewModelScope.launch { page.events.collect { events += it } }
        takesChanges = false

        page.file(Filing(emptyList(), emptyList()).toggle(Meal.Dinner, page.organizers.value!!.categories, "Abendessen"))

        until { events.isNotEmpty() && page.filing().meals.isEmpty() }
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
                settings,
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

    /** Lets the main thread run, where the view model's work goes on, until [done]. */
    private fun until(done: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(10)
        }
        fail("Gave up waiting")
    }

    private fun recipe() = """{"id":"r1","slug":"pancakes","name":"Pancakes",$filed}"""

    private fun json(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

package app.aroma.feature.recipe

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/** Putting a recipe into the household's cookbooks from its menu, and taking it out again. */
@RunWith(RobolectricTestRunner::class)
class RecipeCookbooksTest {
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val env = TestEnvironment()

    /** The recipe's categories as Mealie keeps them. */
    @Volatile
    private var categories = "[]"

    private val server =
        mealieServer { request ->
            val path = request.url.encodedPath
            val response =
                when {
                    path == "/api/recipes/pancakes" && request.method == "GET" -> {
                        json(recipe())
                    }

                    path == "/api/recipes/pancakes" && request.method == "PATCH" -> {
                        val patch = MealieJson.parseToJsonElement(request.body!!.utf8()).jsonObject
                        categories = patch["recipeCategory"].toString()
                        json(recipe())
                    }

                    path == "/api/organizers/categories" -> {
                        json(
                            """{"items":[{"id":"c-soup","name":"Suppen","slug":"suppen"},{"id":"c-bake","name":"Backen","slug":"backen"}]}""",
                        )
                    }

                    path == "/api/organizers/tags" || path == "/api/organizers/tools" -> {
                        json("""{"items":[]}""")
                    }

                    path == "/api/households/cookbooks" -> {
                        json(COOKBOOKS)
                    }

                    // Mealie alone reads the rule of the best-rated: it holds the recipe.
                    path == "/api/recipes" && request.url.queryParameter("queryFilter") != null -> {
                        val held = request.url.queryParameter("cookbook") == "b-best"
                        json("""{"page":1,"per_page":1,"total":${if (held) 1 else 0},"total_pages":1,"items":[]}""")
                    }

                    path == "/api/recipes" -> {
                        json("""{"page":1,"per_page":4,"total":0,"total_pages":1,"items":[]}""")
                    }

                    else -> {
                        MockResponse.Builder().code(404).build()
                    }
                }
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
    fun `a tap files the recipe into a cookbook of categories, and another takes it out`() {
        val page = open()
        val soups = { page.cookbooks.value!!.single { it.cookbook.id == "b-soups" } }
        assertEquals(false, soups().held)

        page.toggleCookbook(soups())

        assertEquals("shown at once", true, soups().held)
        until { filedCategories() == listOf("c-soup") }

        page.toggleCookbook(soups())

        assertEquals(false, soups().held)
        until { requests.count { it.method == "PATCH" } == 2 && filedCategories().isEmpty() }
    }

    @Test
    fun `a cookbook Mealie fills by its own rules, or with every recipe, only says whether it holds the recipe`() {
        val page = open()
        until {
            page.cookbooks.value!!
                .single { it.cookbook.id == "b-best" }
                .held == true
        }
        val best = page.cookbooks.value!!.single { it.cookbook.id == "b-best" }
        val all = page.cookbooks.value!!.single { it.cookbook.id == "b-all" }

        assertNull(best.change)
        assertEquals(true, all.held)
        assertNull(all.change)
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
        // The sheet listens while it is open.
        page.viewModelScope.launch { page.cookbooks.collect { } }
        page.loadCookbooks()
        until {
            page.cookbooks.value
                ?.firstOrNull { it.cookbook.id == "b-soups" }
                ?.change != null
        }
        return page
    }

    private fun filedCategories(): List<String> =
        MealieJson.parseToJsonElement(categories).jsonArray.map {
            it.jsonObject["id"]!!.jsonPrimitive.content
        }

    private fun recipe() = """{"id":"r1","slug":"pancakes","name":"Pancakes","recipeCategory":$categories,"tags":[]}"""

    private companion object {
        val COOKBOOKS =
            """
            {"page":1,"per_page":-1,"total":3,"total_pages":1,"items":[
              {"id":"b-soups","name":"Suppen","slug":"suppen","position":1,"queryFilterString":"recipe_category.id IN [\"c-soup\"]",
               "queryFilter":{"parts":[{"attributeName":"recipe_category.id","relationalOperator":"IN","value":["c-soup"]}]}},
              {"id":"b-best","name":"Die Besten","slug":"die-besten","position":2,"queryFilterString":"rating >= 4",
               "queryFilter":{"parts":[{"attributeName":"rating","relationalOperator":">=","value":"4"}]}},
              {"id":"b-all","name":"Alles","slug":"alles","position":3,"queryFilterString":"","queryFilter":{"parts":[]}}
            ]}
            """.trimIndent()
    }
}

package app.aroma.data

import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.core.model.MealType
import app.aroma.core.model.Organizer
import app.aroma.core.model.RecipeQuery
import app.aroma.core.model.RecipeSort
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.TimelineRepository
import app.aroma.data.session.SessionManager
import app.aroma.feature.editor.RecipeDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Aroma's data layer against a real Mealie server (see [LiveMealie]): what it writes, Mealie keeps, and what it asks for, Mealie finds. */
@RunWith(RobolectricTestRunner::class)
class LiveMealieTest {
    private lateinit var env: TestEnvironment

    /** Makes the names of what a test creates its own, on a server other runs may share. */
    private val mark = UUID.randomUUID().toString().take(8)

    @Before
    fun setUp() {
        LiveMealie.assumeConfigured()
        env = TestEnvironment()
    }

    @After
    fun tearDown() {
        if (::env.isInitialized) env.close()
    }

    @Test
    fun `a recipe saved as the editor writes it comes back, is found and cooked, and signing out revokes the token`() =
        runBlocking {
            // A phone of its own name: Mealie gives a user's tokens of one name made within a second the same
            // value, and taking this one back would leave it working as the token of the class's other sign-in.
            val auth = AuthRepository(env.clients, env.sessions, env.database, deviceName = "Test $mark")
            auth.signIn(auth.probe(LiveMealie.host), LiveMealie.user, LiveMealie.password)
            val session = env.sessions.current!!
            assertNotNull("signing in made a token for this phone", session.tokenId)
            val name = "Aromatestrezept $mark"
            var slug = env.recipes.create(name)
            try {
                val original = env.recipes.recipeJson(slug)
                val form =
                    RecipeDraft.formFrom(original, Locale.GERMANY).copy(
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
                assertEquals(name, saved.name)
                assertEquals(listOf("200 g Mehl", "2 Eier"), saved.ingredients.map { it.note })
                assertEquals("Teig", saved.ingredients.first().section)
                assertEquals(listOf("Alles verrühren.", "10 Minuten backen."), saved.steps.map { it.text })
                assertEquals(2.0, saved.servings, 0.0)
                assertTrue(
                    "Mealie keeps the prep time as seconds (3) or as text (2): ${saved.prepTime}",
                    saved.prepTime?.seconds == 900 || saved.prepTime?.text == "15 Min.",
                )
                val again = env.recipes.recipeJson(slug)
                assertTrue(
                    "saving without a change sends nothing",
                    RecipeDraft.patch(again, RecipeDraft.formFrom(again, Locale.GERMANY), Locale.GERMANY) { "" }.isEmpty(),
                )

                val first = env.recipes.page(RecipeQuery(sort = RecipeSort.Name), page = 1, perPage = 1)
                assertEquals(1, first.items.size)
                assertEquals((first.total ?: 0) > 1, first.hasMore)
                assertEquals(listOf(slug), found(RecipeQuery(search = mark)))

                // Cooked now, it is left out of the recipes cooked before yesterday and found among those cooked
                // before the day after tomorrow, with a day to spare for the server's time zone.
                TimelineRepository(env.sessions).cooked(saved.id, slug, "Tester made this")
                val today = LocalDate.now()
                assertEquals(emptyList<String>(), found(RecipeQuery(search = mark, cookedBefore = today.minusDays(1))))
                assertEquals(listOf(slug), found(RecipeQuery(search = mark, cookedBefore = today.plusDays(2))))
            } finally {
                env.recipes.delete(slug)
            }
            auth.signOut()
            val api = env.clients.create(SessionManager.baseUrl(session.serverUrl))
            val refused = runCatching { api.currentUser("Bearer ${session.token}") }.exceptionOrNull()
            assertEquals("Mealie took the token back", 401, (refused as? HttpException)?.code())
        }

    @Test
    fun `a recipe is filed under a new category, a new tag and a meal, and found by each, among favourites too`() =
        runBlocking {
            live.signIn(env)
            val slug = env.recipes.create("Aromatestrezept $mark")
            val id =
                env.recipes
                    .recipeJson(slug)["id"]!!
                    .jsonPrimitive.content
            val before = organizers()
            try {
                val category = Organizer.new("Aromatestkategorie $mark")
                val tag = Organizer.new("Aromatestschlagwort $mark")
                val saved =
                    env.recipes.file(
                        slug,
                        Filing(listOf(category), listOf(tag)).toggle(Meal.Dessert, before.categories, "Nachtisch"),
                    )
                assertTrue("Mealie made the new category and tag", (saved.categories + saved.tags).none { it.isNew })
                val recipe =
                    env.recipes
                        .recipe(slug)
                        .first { !it.isRefreshing }
                        .data
                assertEquals(setOf(Meal.Dessert), Filing(recipe.categories, recipe.tags).meals)
                assertEquals(saved.categories.map { it.id }.toSet(), recipe.categories.map { it.id }.toSet())
                assertEquals(listOf(tag.name), recipe.tags.map { it.name })

                // A meal is a category or a tag of its name.
                assertEquals(listOf(slug), found(RecipeQuery(search = mark, mealNames = Meal.Dessert.namesIn(organizers()))))
                assertEquals(listOf(slug), found(RecipeQuery(search = mark, mealNames = listOf(category.name))))
                assertEquals(listOf(slug), found(RecipeQuery(search = mark, mealNames = listOf(tag.name))))

                env.recipes.setFavorite(id, slug, true)
                assertEquals(true, env.recipes.loadRatings(fresh = true)[id]?.isFavorite)
                assertEquals(listOf(slug), found(RecipeQuery(search = mark, favoritesOnly = true)))
                assertEquals(listOf(slug), found(RecipeQuery(favoritesOnly = true, mealNames = listOf(tag.name))))
            } finally {
                // Mealie 2.0 cannot delete a recipe while it is someone's favourite.
                env.recipes.setFavorite(id, slug, false)
                env.recipes.delete(slug)
                // Only what this test made goes again.
                val after = organizers()
                (after.categories - before.categories.toSet()).forEach { delete("api/organizers/categories/${it.id}") }
                (after.tags - before.tags.toSet()).forEach { delete("api/organizers/tags/${it.id}") }
            }
        }

    @Test
    fun `a planned recipe moves to another day and meal, shows in its days and goes again`() =
        runBlocking {
            live.signIn(env)
            val slug = env.recipes.create("Aromatestrezept $mark")
            val day = LocalDate.now().plusDays(40)
            try {
                val recipe =
                    env.recipes
                        .recipe(slug)
                        .first { !it.isRefreshing }
                        .data
                val planned = env.mealPlans.add(day, MealType.Lunch, recipeId = recipe.id, title = "", note = "")
                try {
                    env.mealPlans.move(planned.id, day.plusDays(1), MealType.Dinner)
                    val moved = plannedFrom(day).single { it.id == planned.id }
                    assertEquals(day.plusDays(1), moved.date)
                    assertEquals(MealType.Dinner, moved.type)
                    assertEquals(slug, moved.recipe?.slug)
                } finally {
                    env.mealPlans.delete(planned.id)
                }
                assertTrue(plannedFrom(day).none { it.id == planned.id })
            } finally {
                env.recipes.delete(slug)
            }
        }

    @Test
    fun `a list syncs what is added, ticked off and cleared on the phone, takes a recipe's ingredients and goes again`() =
        runBlocking {
            live.signIn(env)
            val listId = env.shopping.createList("Aromatestliste $mark")
            val slug = env.recipes.create("Aromatestrezept $mark")
            try {
                assertTrue(lists().any { it.id == listId })
                env.shopping.add(listId, "2 Zitronen")
                env.shopping.add(listId, "Kaffee")
                assertEquals(2, env.shopping.pendingCount(listId).first())
                assertTrue(env.shopping.sync())
                env.shopping.refresh(listId)
                val coffee = items(listId).single { it.note == "Kaffee" }

                env.shopping.toggle(listId, coffee.id)
                assertTrue(env.shopping.sync())
                env.shopping.refresh(listId)
                assertTrue(items(listId).single { it.id == coffee.id }.checked)

                env.shopping.clearChecked(listId)
                assertTrue(env.shopping.sync())
                env.shopping.refresh(listId)
                assertEquals(listOf("2 Zitronen"), items(listId).map { it.note })

                val original = env.recipes.recipeJson(slug)
                val form = RecipeDraft.formFrom(original, Locale.GERMANY).copy(ingredients = "2 Stangen Lauch\n3 Eier\n200 g Mehl")
                env.recipes.update(slug, RecipeDraft.patch(original, form, Locale.GERMANY) { "" })
                val recipe = env.recipes.recipeJson(slug)
                val ingredients = recipe["recipeIngredient"]!!.jsonArray.map { it.jsonObject }
                env.shopping.addRecipe(listId, recipe["id"]!!.jsonPrimitive.content, 1.0, ingredients.take(2))
                env.shopping.refresh(listId)
                val name = recipe["name"]!!.jsonPrimitive.content
                assertEquals(listOf("2 Zitronen"), items(listId).filter { name !in it.recipeNames }.map { it.note })
                assertEquals(2, items(listId).count { name in it.recipeNames })
            } finally {
                env.recipes.delete(slug)
                env.shopping.deleteList(listId)
            }
            assertTrue(lists().none { it.id == listId })
        }

    /** The slugs of all recipes [query] finds. */
    private suspend fun found(query: RecipeQuery) =
        env.recipes
            .page(query, page = 1, perPage = -1)
            .items
            .map { it.slug }

    private suspend fun organizers() =
        env.recipes
            .organizers()
            .first { !it.isRefreshing }
            .data

    private suspend fun plannedFrom(day: LocalDate) =
        env.mealPlans
            .range(day, 2)
            .first { !it.isRefreshing }
            .data

    private suspend fun lists() =
        env.shopping
            .lists()
            .first { !it.isRefreshing }
            .data

    private suspend fun items(listId: String) =
        env.shopping
            .observe(listId)
            .first()!!
            .items

    private suspend fun delete(path: String) =
        withContext(Dispatchers.IO) {
            val url =
                env.sessions
                    .credentials()!!
                    .server
                    .resolve(path)!!
            env.clients.httpClient
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .delete()
                        .build(),
                ).execute()
                .use { response ->
                    assertTrue("Mealie answered ${response.code}", response.isSuccessful)
                }
        }

    companion object {
        private val live = LiveMealie()

        @JvmStatic
        @AfterClass
        fun signOut() = live.signOut()
    }
}

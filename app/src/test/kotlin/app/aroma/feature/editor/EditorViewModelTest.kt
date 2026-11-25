package app.aroma.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.data.TestEnvironment
import app.aroma.data.session.Session
import app.aroma.testing.address
import app.aroma.testing.json
import app.aroma.testing.mealieServer
import app.aroma.testing.until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorViewModelTest {
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment

    /** Mealie moves a renamed recipe to a new slug; the old one is gone. */
    @Volatile
    private var renamed = false

    @Volatile
    private var uploadsWork = false

    private val server =
        mealieServer { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                path == "/api/recipes/gnocchi" && !renamed && request.method == "GET" -> {
                    json(recipe("gnocchi", "Gnocchi"))
                }

                path == "/api/recipes/gnocchi" && !renamed && request.method == "PATCH" -> {
                    renamed = true
                    json(recipe("tomaten-gnocchi", "Tomaten-Gnocchi"))
                }

                path == "/api/organizers/categories" && request.method == "GET" -> {
                    json("""{"items":[{"id":"c1","name":"Abendessen","slug":"abendessen"}]}""")
                }

                path == "/api/organizers/categories" && request.method == "POST" -> {
                    json("""{"id":"c2","name":"Nachtisch","slug":"nachtisch"}""")
                }

                path == "/api/organizers/tags" && request.method == "POST" -> {
                    json("""{"id":"t2","name":"Schnell","slug":"schnell"}""")
                }

                path == "/api/organizers/tags" || path == "/api/organizers/tools" -> {
                    json("""{"items":[]}""")
                }

                path == "/api/recipes/tomaten-gnocchi/image" -> {
                    MockResponse.Builder().code(if (uploadsWork) 200 else 500).build()
                }

                else -> {
                    MockResponse.Builder().code(404).build()
                }
            }
        }

    @Before
    fun setUp() {
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.address, token = "t", userId = "u", username = "mara"))
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `saving again after the photo failed uploads it under the new slug and sends nothing else twice`() {
        val editor = open(SavedStateHandle())
        editor.update { it.copy(name = "Tomaten-Gnocchi") }
        editor.setImage(Uri.parse("content://photos/1"), byteArrayOf(1, 2, 3), "jpg")
        editor.save { "" }
        until { !editor.state.value.isSaving }
        assertNotNull(editor.state.value.error)

        uploadsWork = true
        editor.save { "" }
        until { !editor.state.value.isSaving }

        assertEquals("tomaten-gnocchi", runBlocking { withTimeout(1_000) { editor.saved.first() } })
        assertEquals(1, requests.count { it.method == "PATCH" })
        assertEquals(2, requests.count { it.method == "PUT" && it.url.encodedPath == "/api/recipes/tomaten-gnocchi/image" })
    }

    @Test
    fun `changes come back when Android brings the editor back after ending the app`() {
        val saved = SavedStateHandle()
        val editor = open(saved)
        editor.update { it.copy(name = "Tomaten-Gnocchi", tags = listOf(Organizer("t1", "Schnell", "schnell"))) }

        val restored = open(saved)

        assertEquals(editor.state.value.form, restored.state.value.form)
    }

    @Test
    fun `a kind of dish and new names are made in Mealie once, and what the group has is taken as it is`() {
        val editor = open(SavedStateHandle())
        until {
            editor.state.value.allCategories
                .isNotEmpty()
        }
        editor.update { form ->
            form
                .filedAs(Filing(form.categories, form.tags).toggle(Meal.Dessert, editor.state.value.allCategories, "Nachtisch"))
                .let { it.copy(categories = it.categories + Organizer.new("abendessen"), tags = listOf(Organizer.new("Schnell"))) }
        }
        editor.save { "" }
        until { !editor.state.value.isSaving }

        val made = requests.filter { it.method == "POST" }.map { it.url.encodedPath to it.body!!.utf8() }
        assertEquals(
            listOf("/api/organizers/categories" to """{"name":"Nachtisch"}""", "/api/organizers/tags" to """{"name":"Schnell"}"""),
            made,
        )
        val patch = requests.single { it.method == "PATCH" }.body!!.utf8()
        assertTrue(patch, patch.contains(""""recipeCategory":[{"id":"c2","name":"Nachtisch","slug":"nachtisch"},{"id":"c1",""""))
        assertTrue(patch, patch.contains(""""tags":[{"id":"t2","""))
        assertEquals(
            listOf("c2", "c1"),
            editor.state.value.form.categories
                .map { it.id },
        )
    }

    private fun open(saved: SavedStateHandle): EditorViewModel {
        val editor = EditorViewModel("gnocchi", env.recipes, server.address, Locale.GERMANY, saved)
        until { !editor.state.value.isLoading }
        return editor
    }

    private fun recipe(slug: String, name: String) = """{"id":"r1","slug":"$slug","name":"$name"}"""
}

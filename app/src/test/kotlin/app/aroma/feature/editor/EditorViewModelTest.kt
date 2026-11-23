package app.aroma.feature.editor

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import app.aroma.core.model.Organizer
import app.aroma.data.TestEnvironment
import app.aroma.data.session.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorViewModelTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment

    /** Mealie moves a renamed recipe to a new slug; the old one is gone. */
    @Volatile
    private var renamed = false

    @Volatile
    private var uploadsWork = false

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/recipes/gnocchi" && !renamed && request.method == "GET" -> {
                            json(recipe("gnocchi", "Gnocchi"))
                        }

                        path == "/api/recipes/gnocchi" && !renamed && request.method == "PATCH" -> {
                            renamed = true
                            json(recipe("tomaten-gnocchi", "Tomaten-Gnocchi"))
                        }

                        path == "/api/recipes/tomaten-gnocchi/image" -> {
                            MockResponse.Builder().code(if (uploadsWork) 200 else 500).build()
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
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

    private fun open(saved: SavedStateHandle): EditorViewModel {
        val editor = EditorViewModel("gnocchi", env.recipes, server.url("/").toString().trimEnd('/'), Locale.GERMANY, saved)
        until { !editor.state.value.isLoading }
        return editor
    }

    /** Lets the main thread run, where the view model's work goes on, until [done]. */
    private fun until(done: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(10)
        }
        fail("Gave up waiting")
    }

    private fun json(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun recipe(slug: String, name: String) = """{"id":"r1","slug":"$slug","name":"$name"}"""
}

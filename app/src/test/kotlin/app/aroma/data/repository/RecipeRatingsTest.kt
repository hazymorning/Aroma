package app.aroma.data.repository

import app.aroma.data.TestEnvironment
import app.aroma.data.session.Session
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeRatingsTest {
    private val server = MockWebServer()
    private val sent = CopyOnWriteArrayList<String>()
    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/users/self/ratings" -> {
                            json("""{"ratings":[{"recipeId":"r1","rating":4.0,"isFavorite":true}]}""")
                        }

                        path == "/api/users/u/ratings/gnocchi" && request.method == "POST" -> {
                            sent += request.body?.utf8().orEmpty()
                            MockResponse.Builder().code(200).build()
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

    /** Mealie ignores a rating of null, so taking a rating back sends 0, which it leaves out of the average. */
    @Test
    fun `no stars take the rating back and keep the favorite`() =
        runBlocking {
            assertEquals(
                4.0,
                env.recipes
                    .loadRatings()
                    .getValue("r1")
                    .rating,
            )
            env.recipes.rate("r1", "gnocchi", 0)
            assertEquals(listOf("""{"rating":0.0}"""), sent)
            val rating =
                env.recipes.ratings.value
                    .getValue("r1")
            assertNull(rating.rating)
            assertEquals(true, rating.isFavorite)
        }

    private fun json(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

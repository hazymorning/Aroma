package app.aroma.feature.editor

import app.aroma.data.AromaError
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import app.aroma.data.session.Session
import app.aroma.testing.address
import app.aroma.testing.eventStream
import app.aroma.testing.json
import app.aroma.testing.mealieServer
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Photos of a recipe go to Mealie's AI, as images beside the text, and nowhere else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val server = mealieServer { answer(it.also(requests::add)) }
    private val env = TestEnvironment()

    /** Recipes on the server, newest first. */
    private val listed = CopyOnWriteArrayList(listOf(summary("old-1", "aelteres-rezept")))

    /** What Mealie's AI answers: its steps and the slug of the recipe it saved, or the message of an error. */
    @Volatile
    private var answer =
        "event: progress\ndata: {\"message\":\"Lese Bilder mit KI...\"}\n\n" +
            "event: done\ndata: {\"slug\":\"kartoffelsuppe\"}\n\n"

    @Before
    fun setUp() {
        runBlocking {
            env.sessions.start(
                Session(server.address, token = "t", userId = USER, username = "mara", serverVersion = "v3.31.0"),
            )
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `photos go to Mealie's AI as JPEG images, with the text, the link and the language, and nowhere else`() =
        runBlocking<Unit> {
            val photos = listOf(photo("photo-1.jpg", 1), photo("photo-2.jpg", 2))
            val steps = mutableListOf<ImportStep>()

            // Read with AI is switched off: photos are read with it all the same.
            val request = ImportRequest(post, text = "Omas Kartoffelsuppe", useAi = false, translateTo = "de-DE", photos = photos)
            val slug = importer().import(request, onSending = {}) { steps += it }

            assertEquals("kartoffelsuppe", slug)
            assertEquals(listOf("/api/recipes/create/ai/stream"), creations())
            val parts = parts(requests.single { it.url.encodedPath == "/api/recipes/create/ai/stream" })
            assertEquals(listOf("content", "url", "translateLanguage", "images", "images"), parts.map { it.name })
            assertEquals("Omas Kartoffelsuppe", parts[0].text)
            assertEquals(post, parts[1].text)
            assertEquals("de-DE", parts[2].text)
            parts.drop(3).forEachIndexed { index, image ->
                assertEquals(photos[index].name, image.fileName)
                assertEquals("image/jpeg", image.type)
                assertArrayEquals(photos[index].readBytes(), image.bytes)
            }
            assertTrue(ImportStep.Server("Lese Bilder mit KI...") in steps)
            assertFalse("the phone does not read the post", requests.any { it.url.encodedPath.startsWith("/p/") })
            assertFalse("nothing is saved besides", requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
        }

    @Test
    fun `when Mealie finds no recipe in the photos, the import says so and saves nothing of its own`() =
        runBlocking<Unit> {
            answer = "event: error\ndata: {\"message\":\"Es wurde kein Rezept in der angegebenen Quelle gefunden\"}\n\n"
            try {
                importer().import(ImportRequest(post, text = "Kein Rezept", photos = listOf(photo("photo-1.jpg", 1))), onSending = {}) { }
                fail("The photos hold no recipe")
            } catch (failure: AromaError.ImportFailed) {
                assertEquals("Es wurde kein Rezept in der angegebenen Quelle gefunden", failure.detail)
            }
            assertEquals(listOf("/api/recipes/create/ai/stream"), creations())
            assertFalse(requests.any { it.method == "POST" && it.url.encodedPath == "/api/recipes" })
            assertFalse(requests.any { it.url.encodedPath.startsWith("/p/") })
        }

    @Test
    fun `a photo import picked up after Android ended the app waits for its recipe and uploads nothing again`() =
        runBlocking<Unit> {
            val known = setOf("old-1")
            // Mealie saved the recipe while the app was gone.
            listed.add(0, summary("id-kartoffelsuppe", "kartoffelsuppe", link = post))

            val slug =
                importer().resume(
                    ImportRequest(post, photos = listOf(photo("photo-1.jpg", 1))),
                    known,
                    before = known,
                    onSending = {},
                ) { }

            assertEquals("kartoffelsuppe", slug)
            assertEquals(emptyList<String>(), creations())
            assertFalse("nothing of a post is finished", requests.any { it.method == "PATCH" || it.method == "PUT" })
        }

    private val post get() = "http://www.instagram.com:${server.port}/p/AromaTest03/"

    private fun creations() = requests.map { it.url.encodedPath }.filter { it.startsWith("/api/recipes/create/") }

    private fun importer(): RecipeImporter {
        // Every host is the test server, so the post keeps its real address.
        val client = OkHttpClient.Builder().dns(Dns { InetAddress.getAllByName(server.hostName).toList() }).build()
        return RecipeImporter(env.recipes, SocialPosts(client) { "Test browser" }, untitled = { "Rezept" }, 2.seconds, 10.milliseconds)
    }

    private fun photo(name: String, seed: Int): File = File(folder.root, name).apply { writeBytes(ByteArray(64) { (it * seed).toByte() }) }

    private class Part(
        val name: String,
        val fileName: String?,
        val type: String?,
        val bytes: ByteArray,
    ) {
        val text get() = bytes.decodeToString()
    }

    private fun parts(request: RecordedRequest): List<Part> {
        val boundary = request.headers["Content-Type"]!!.toMediaType().parameter("boundary")!!
        return MultipartReader(Buffer().write(request.body!!), boundary).use { reader ->
            generateSequence { reader.nextPart() }
                .map { part ->
                    val disposition = part.headers["Content-Disposition"].orEmpty()
                    Part(
                        name = Regex("""\bname="([^"]*)"""").find(disposition)!!.groupValues[1],
                        fileName = Regex("""filename="([^"]*)"""").find(disposition)?.groupValues?.get(1),
                        type = part.headers["Content-Type"],
                        bytes = part.body.readByteArray(),
                    )
                }.toList()
        }
    }

    private fun answer(request: RecordedRequest): MockResponse =
        when {
            request.method == "GET" && request.url.encodedPath == "/api/recipes" -> {
                json("""{"page":1,"per_page":10,"total":${listed.size},"total_pages":1,"items":[${listed.joinToString(",")}]}""")
            }

            request.method == "POST" && request.url.encodedPath == "/api/recipes/create/ai/stream" -> {
                eventStream(answer)
            }

            else -> {
                MockResponse.Builder().code(404).build()
            }
        }

    private fun summary(id: String, slug: String, link: String? = null) =
        """{"id":"$id","slug":"$slug","name":"$slug","userId":"$USER","orgURL":${link?.let { "\"$it\"" }}}"""

    private companion object {
        const val USER = "user-1"
    }
}

package app.aroma.data.remote

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SocialPostsTest {
    private val server = MockWebServer()
    private lateinit var posts: SocialPosts

    /** What the platform answers, by path; anything else is not found. */
    private val pages = ConcurrentHashMap<String, MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    pages[request.url.encodedPath.also { requests += request }] ?: MockResponse.Builder().code(404).build()
            }
        server.start()
        // Every host is the test server, so the post keeps its real address.
        val client = OkHttpClient.Builder().dns(Dns { InetAddress.getAllByName(server.hostName).toList() }).build()
        posts = SocialPosts(client, appAgent = "Aroma/0.4.1 (Android 16)") { "Test browser (Küche)" }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `an Instagram post comes from its embed, asked for as Aroma, not as a browser`() =
        runBlocking {
            pages["/p/Gnocchi/embed/captioned/"] = html(embed("Gnocchi<br />- 500 g Gnocchi"))

            val post = posts.read(link("/p/Gnocchi/?igsh=abc"))!!

            assertEquals("Gnocchi\n- 500 g Gnocchi", post.caption)
            assertEquals("https://cdn.example/g.jpg?a=1&b=2", post.image)
            assertEquals("Aroma/0.4.1 (Android 16)", requests.single().headers["User-Agent"])
        }

    @Test
    fun `a reel, a link with the author's name and a share link all read the same embed`() =
        runBlocking {
            pages["/p/Gnocchi/embed/captioned/"] = html(embed("Gnocchi"))
            pages["/share/reel/BAxyz/"] = redirect("/reel/Gnocchi/")
            pages["/share/p/BAabc/"] = redirect("/accounts/login/?next=%2Fp%2FGnocchi%2F")

            listOf("/reel/Gnocchi/", "/kochmitmir/p/Gnocchi/", "/share/reel/BAxyz/", "/share/p/BAabc/").forEach { path ->
                assertEquals(path, "Gnocchi", posts.read(link(path))?.caption)
            }
        }

    @Test
    fun `where the embed shows no caption, the link preview is read with the phone's browser`() =
        runBlocking {
            pages["/p/Gnocchi/embed/captioned/"] = html("""<div id="root"></div>""")
            val title = "Koch mit mir on Instagram: &quot;Gnocchi&#10;- 500 g Gnocchi&quot;"
            pages["/p/Gnocchi/"] =
                page("""<meta property="og:title" content="$title"><meta property="og:image" content="https://cdn.example/g.jpg">""")

            val post = posts.read(link("/p/Gnocchi/"))!!

            assertEquals("Gnocchi\n- 500 g Gnocchi", post.caption)
            assertEquals("https://cdn.example/g.jpg", post.image)
            val preview = requests.single { it.url.encodedPath == "/p/Gnocchi/" }
            assertEquals("a device name outside ASCII is left out", "Test browser (Kche)", preview.headers["User-Agent"])
        }

    @Test
    fun `a post shown only after signing in reads as no post`() =
        runBlocking {
            pages["/p/Gnocchi/"] = redirect("/accounts/login/?next=%2Fp%2FGnocchi%2F")
            pages["/accounts/login/"] =
                page("""<meta property="og:title" content="Instagram"><meta property="og:description" content="Melde dich an">""")

            assertNull(posts.read(link("/p/Gnocchi/")))
        }

    @Test
    fun `a sign-in page shown at the post's own address reads as no post`() =
        runBlocking {
            pages["/p/Gnocchi/"] =
                page(
                    """<meta property="og:title" content="Instagram">""" +
                        """<meta property="og:description" content="Create an account or log in to Instagram.">""",
                )

            assertNull(posts.read(link("/p/Gnocchi/")))
        }

    @Test
    fun `a page without a link preview reads as no post`() =
        runBlocking {
            pages["/p/Gnocchi/"] = page("<title>Instagram</title>")

            assertNull(posts.read(link("/p/Gnocchi/")))
        }

    @Test
    fun `a photo comes with the extension Mealie stores it under, anything else is no photo`() =
        runBlocking {
            pages["/g.jpg"] =
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(byteArrayOf(1, 2, 3)))
                    .build()
            pages["/page.html"] = page("<html></html>")

            val photo = posts.photo(link("/g.jpg"))!!
            assertArrayEquals(byteArrayOf(1, 2, 3), photo.bytes)
            assertEquals("jpg", photo.extension)
            assertNull(posts.photo(link("/page.html")))
        }

    @Test
    fun `posts of the platforms that keep recipes in captions are told apart from websites`() {
        listOf(
            "https://www.instagram.com/reel/DAbc/",
            "https://instagram.com/p/DAbc/",
            "https://m.facebook.com/watch/?v=1",
            "https://fb.watch/abc/",
            "https://www.threads.net/@koch/post/abc",
            "https://vm.tiktok.com/abc/",
        ).forEach { assertTrue(it, SocialPosts.isPost(it)) }
        listOf("https://www.chefkoch.de/rezepte/1/", "https://notinstagram.com/p/x", "Gnocchi mit Tomaten").forEach {
            assertFalse(it, SocialPosts.isPost(it))
        }
    }

    private fun link(path: String) = "http://www.instagram.com:${server.port}$path"

    /** An embed page as Instagram serves it to a client that is not a browser. */
    private fun embed(caption: String) =
        """<img class="EmbeddedMediaImage" alt="" src="https://cdn.example/g.jpg?a=1&amp;b=2">""" +
            """<div class="Caption"><a class="CaptionUsername" href="/kochmitmir/">kochmitmir</a><br /><br />$caption""" +
            """<div class="CaptionComments"><a href="#">View all comments</a></div></div>"""

    private fun redirect(location: String) =
        MockResponse
            .Builder()
            .code(302)
            .addHeader("Location", location)
            .build()

    private fun html(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body("<html><body>$body</body></html>")
            .build()

    private fun page(head: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body("<html><head>$head</head><body></body></html>")
            .build()
}

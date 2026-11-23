package app.aroma.data.remote

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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

class SocialPostsTest {
    private val server = MockWebServer()
    private lateinit var posts: SocialPosts

    @Before
    fun setUp() {
        server.start()
        // Every host is the test server, so the post keeps its real address.
        val client = OkHttpClient.Builder().dns(Dns { InetAddress.getAllByName(server.hostName).toList() }).build()
        posts = SocialPosts(client) { "Test browser (Küche)" }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `a post's caption and photo come from its link preview, without the platform's framing`() =
        runBlocking {
            val title = "Koch mit mir on Instagram: &quot;Gnocchi&#10;- 500 g Gnocchi&quot;"
            server.enqueue(
                page("""<meta property="og:title" content="$title"><meta property="og:image" content="https://cdn.example/g.jpg">"""),
            )

            val post = posts.read(link("/p/Gnocchi/"))!!

            assertEquals("Gnocchi\n- 500 g Gnocchi", post.caption)
            assertEquals("https://cdn.example/g.jpg", post.image)
            assertEquals("a device name outside ASCII is left out", "Test browser (Kche)", server.takeRequest().headers["User-Agent"])
        }

    @Test
    fun `a post shown only after signing in reads as no post`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(302)
                    .addHeader("Location", "/accounts/login/?next=%2Fp%2FGnocchi%2F")
                    .build(),
            )
            server.enqueue(
                page("""<meta property="og:title" content="Instagram"><meta property="og:description" content="Melde dich an">"""),
            )

            assertNull(posts.read(link("/p/Gnocchi/")))
        }

    @Test
    fun `a page without a link preview reads as no post`() =
        runBlocking {
            server.enqueue(page("<title>Instagram</title>"))

            assertNull(posts.read(link("/p/Gnocchi/")))
        }

    @Test
    fun `a photo comes with the extension Mealie stores it under, anything else is no photo`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(byteArrayOf(1, 2, 3)))
                    .build(),
            )
            server.enqueue(page("<html></html>"))

            val photo = posts.photo(link("/g.jpg"))!!
            assertArrayEquals(byteArrayOf(1, 2, 3), photo.bytes)
            assertEquals("jpg", photo.extension)
            assertNull(posts.photo(link("/g.jpg")))
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

    private fun page(head: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body("<html><head>$head</head><body></body></html>")
            .build()
}

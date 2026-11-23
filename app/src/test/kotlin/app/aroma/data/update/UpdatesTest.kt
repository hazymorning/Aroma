package app.aroma.data.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class UpdatesTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apkBytes = ByteArray(300_000) { (it % 251).toByte() }
    private val releaseRequests = AtomicInteger()

    @Volatile private var tag = "v0.4.0"

    @Volatile private var releaseCode = 200

    @Volatile private var announcedSize = apkBytes.size.toLong()

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.url.encodedPath) {
                        "/releases/latest" -> {
                            releaseRequests.incrementAndGet()
                            MockResponse
                                .Builder()
                                .code(releaseCode)
                                .body(release())
                                .build()
                        }

                        "/download/Aroma.apk" -> {
                            MockResponse.Builder().body(Buffer().write(apkBytes)).build()
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
            }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun release(): String =
        """{"tag_name": "$tag", "name": "Aroma", "assets": [
            {"name": "notes.txt", "browser_download_url": "${server.url("/download/notes.txt")}", "size": 10},
            {"name": "Aroma.apk", "browser_download_url": "${server.url("/download/Aroma.apk")}", "size": $announcedSize}
        ]}"""

    private fun updates(isAroma: (File) -> Boolean = { true }) =
        Updates(OkHttpClient(), "0.3.0", temp.root.resolve("update"), scope, isAroma, server.url("/releases/latest"))

    private fun Updates.settled(): UpdateState =
        runBlocking {
            withTimeout(10_000) { state.first { it != UpdateState.Unknown && it != UpdateState.Checking } }
        }

    @Test
    fun findsANewerRelease() {
        val updates = updates()
        updates.checkNow()
        val found = updates.settled()
        assertEquals(UpdateState.Available("0.4.0", server.url("/download/Aroma.apk").toString(), apkBytes.size.toLong()), found)
    }

    @Test
    fun theSameOrAnOlderReleaseIsCurrent() {
        for (latest in listOf("v0.3.0", "v0.2.9", "0.3")) {
            tag = latest
            val updates = updates()
            updates.checkNow()
            assertEquals(latest, UpdateState.Current, updates.settled())
        }
    }

    @Test
    fun anUnreachableGitHubFails() {
        releaseCode = 503
        val updates = updates()
        updates.checkNow()
        assertEquals(UpdateState.Failed, updates.settled())
    }

    @Test
    fun installDownloadsTheApkAndHandsItToTheInstaller() =
        runBlocking {
            val updates = updates()
            updates.checkNow()
            val found = updates.settled() as UpdateState.Available
            updates.install(found)
            val message = withTimeout(10_000) { updates.messages.first() }
            val apk = (message as UpdateMessage.Install).apk
            assertArrayEquals(apkBytes, apk.readBytes())
            assertEquals(found, updates.state.value)

            // Tapped again after the installer was cancelled, the download is not repeated.
            val downloads = server.requestCount
            updates.install(found)
            assertTrue(withTimeout(10_000) { updates.messages.first() } is UpdateMessage.Install)
            assertEquals(downloads, server.requestCount)
        }

    @Test
    fun aDownloadCutShortIsNotInstalled() =
        runBlocking {
            announcedSize = apkBytes.size + 1L
            val updates = updates()
            updates.checkNow()
            val found = updates.settled() as UpdateState.Available
            updates.install(found)
            assertEquals(UpdateMessage.NotDownloaded(found), withTimeout(10_000) { updates.messages.first() })
            assertEquals(found, updates.state.value)
        }

    @Test
    fun anotherAppIsNotInstalled() =
        runBlocking {
            val updates = updates(isAroma = { false })
            updates.checkNow()
            val found = updates.settled() as UpdateState.Available
            updates.install(found)
            assertEquals(UpdateMessage.NotAroma("0.4.0"), withTimeout(10_000) { updates.messages.first() })
            assertTrue(
                temp.root
                    .resolve("update")
                    .listFiles()
                    .orEmpty()
                    .isEmpty(),
            )
        }

    @Test
    fun checksAtStartOnlyWhenTurnedOnAndOnlyOnce() =
        runBlocking {
            val off = updates()
            off.checkAtStart { false }
            Thread.sleep(300)
            assertEquals(0, releaseRequests.get())
            assertEquals(UpdateState.Unknown, off.state.value)

            val leftover =
                temp.root
                    .resolve("update")
                    .apply { mkdirs() }
                    .resolve("Aroma-0.3.0.apk")
                    .apply { writeText("old") }
            val on = updates()
            on.checkAtStart { true }
            on.checkAtStart { true }
            val found = withTimeout(10_000) { on.messages.first() }
            assertTrue(found is UpdateMessage.Found)
            assertEquals(1, releaseRequests.get())
            assertFalse(leftover.exists())
        }

    @Test
    fun comparesVersionsByTheirNumbers() {
        assertTrue(isNewer("0.10.0", "0.9.9"))
        assertTrue(isNewer("1.0", "0.99.1"))
        assertTrue(isNewer("0.3.1", "0.3"))
        assertFalse(isNewer("0.3.0", "0.3"))
        assertFalse(isNewer("0.2.9", "0.3.0"))
    }
}

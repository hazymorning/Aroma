package app.aroma.data.remote

import app.aroma.data.AromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ImportStreamTest {
    private fun read(stream: String, progress: MutableList<String> = mutableListOf()): String =
        runBlocking { ImportStream.slugFrom(stream.toResponseBody("text/event-stream".toMediaType()), progress::add, cancel = {}) }

    private fun failure(stream: String): AromaError.ImportFailed =
        try {
            read(stream)
            fail("The stream should have failed")
            error("unreachable")
        } catch (error: AromaError.ImportFailed) {
            error
        }

    @Test
    fun `progress messages arrive in order and the slug ends the stream`() {
        val progress = mutableListOf<String>()
        val slug =
            read(
                """
                event: progress
                data: {"message":"Webseite wird abgerufen..."}

                : keep-alive

                event: progress
                data: {"message":"Bild wird heruntergeladen..."}

                event: done
                data: {"slug":"rosmarin-cracker"}

                """.trimIndent() + "\n",
                progress,
            )
        assertEquals("rosmarin-cracker", slug)
        assertEquals(listOf("Webseite wird abgerufen...", "Bild wird heruntergeladen..."), progress)
    }

    @Test
    fun `error codes become the app's own message, sentences are shown as Mealie wrote them`() {
        val noRecipe = failure("event: error\ndata: {\"message\":\"BAD_RECIPE_DATA\"}\n\n")
        assertNull(noRecipe.detail)
        assertEquals(AromaError.ImportFailed.Reason.NoRecipe, noRecipe.reason)

        val unreachable = failure("event: error\ndata: {\"message\":\"CONNECTION_ERROR\"}\n\n")
        assertEquals(AromaError.ImportFailed.Reason.PageUnreachable, unreachable.reason)

        val sentence = failure("event: error\ndata: {\"message\":\"Es wurde kein Rezept in der angegebenen Quelle gefunden\"}\n\n")
        assertEquals("Es wurde kein Rezept in der angegebenen Quelle gefunden", sentence.detail)
    }

    @Test
    fun `cancelling ends a read waiting on Mealie, and no step is told after`() =
        runBlocking {
            val cancelled = CountDownLatch(1)
            val stalling =
                object : Source {
                    private var sent = false

                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (!sent) {
                            sent = true
                            val event = Buffer().writeUtf8("event: progress\ndata: {\"message\":\"Webseite wird abgerufen...\"}\n\n")
                            return event.size.also { sink.write(event, it) }
                        }
                        // As a socket would, the read waits until the call is cancelled.
                        cancelled.await(10, TimeUnit.SECONDS)
                        throw IOException("Canceled")
                    }

                    override fun timeout() = Timeout.NONE

                    override fun close() = Unit
                }
            val progress = CopyOnWriteArrayList<String>()
            val body = stalling.buffer().asResponseBody("text/event-stream".toMediaType())
            val import = launch(Dispatchers.Default) { ImportStream.slugFrom(body, progress::add) { cancelled.countDown() } }
            withTimeout(5_000) { while (progress.isEmpty()) delay(10) }

            withTimeout(5_000) { import.cancelAndJoin() }

            assertEquals(0L, cancelled.count)
            assertEquals(listOf("Webseite wird abgerufen..."), progress)
        }

    @Test
    fun `a stream that ends without a result was cut on the way, Mealie may still finish`() {
        try {
            read("event: progress\ndata: {\"message\":\"Webseite wird abgerufen...\"}\n\n")
            fail("The stream should have failed")
        } catch (_: AromaError.Offline) {
            // Expected: the importer then waits for the recipe.
        }
    }
}

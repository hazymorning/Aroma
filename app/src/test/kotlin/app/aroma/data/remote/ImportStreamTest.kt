package app.aroma.data.remote

import app.aroma.data.AromaError
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ImportStreamTest {
    private fun read(stream: String, progress: MutableList<String> = mutableListOf()): String =
        runBlocking { ImportStream.slugFrom(stream.toResponseBody("text/event-stream".toMediaType()), progress::add) }

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
    fun `a stream that ends without a result was cut on the way, Mealie may still finish`() {
        try {
            read("event: progress\ndata: {\"message\":\"Webseite wird abgerufen...\"}\n\n")
            fail("The stream should have failed")
        } catch (_: AromaError.Offline) {
            // Expected: the importer then waits for the recipe.
        }
    }
}

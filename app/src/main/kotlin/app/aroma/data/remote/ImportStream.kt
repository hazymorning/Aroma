package app.aroma.data.remote

import app.aroma.data.AromaError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import okio.BufferedSource
import java.io.EOFException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads the server-sent events of Mealie's streaming import endpoints: any number of
 * `progress` events with a message in the requested language, then `done` with the new
 * recipe's slug or `error` with a message or an error code.
 */
internal object ImportStream {
    private val ErrorCodes = setOf("BAD_RECIPE_DATA", "NO_RECIPE_DATA", "Recipe creation failed")
    private const val UNREACHABLE = "CONNECTION_ERROR"

    /**
     * A read waiting on the socket does not notice the import being cancelled, as on signing out, and
     * would read on, and tell each step, until a timeout. [cancel] ends the request, which ends that read.
     */
    suspend fun slugFrom(body: ResponseBody, onProgress: (String) -> Unit, cancel: () -> Unit): String =
        withContext(Dispatchers.IO) {
            body.use { response ->
                coroutineScope {
                    val reading =
                        async {
                            try {
                                read(response.source(), onProgress)
                            } catch (failure: IOException) {
                                // Ending the request fails the read; once cancelled, that is no failure of the import.
                                ensureActive()
                                throw failure
                            }
                        }
                    try {
                        reading.await()
                    } catch (cancelled: CancellationException) {
                        cancel()
                        throw cancelled
                    }
                }
            }
        }

    private fun CoroutineScope.read(source: BufferedSource, onProgress: (String) -> Unit): String {
        var event = ""
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            // A line read just as the import was cancelled tells no step any more.
            ensureActive()
            when {
                line.isEmpty() -> {
                    handle(event, data.toString(), onProgress)?.let { return it }
                    event = ""
                    data.clear()
                }

                line.startsWith("event:") -> {
                    event = line.substringAfter(':').trim()
                }

                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').removePrefix(" "))
                }
            }
        }
        // Mealie always ends with done or error; a stream that stops before was cut on the way.
        return handle(event, data.toString(), onProgress) ?: throw AromaError.Offline(EOFException("Import stream ended early"))
    }

    /** Returns the slug once the import is done, throws on an error event and returns null otherwise. */
    private fun handle(event: String, data: String, onProgress: (String) -> Unit): String? {
        if (data.isEmpty()) return null
        val payload = MealieJson.parseToJsonElement(data).jsonObject
        return when (event) {
            "progress" -> {
                payload.text("message")?.let(onProgress)
                null
            }

            "done" -> {
                payload.text("slug") ?: throw AromaError.ImportFailed()
            }

            "error" -> {
                val message = payload.text("message")
                throw when (message) {
                    UNREACHABLE -> AromaError.ImportFailed(AromaError.ImportFailed.Reason.PageUnreachable)
                    null, in ErrorCodes -> AromaError.ImportFailed()
                    else -> AromaError.ImportFailed(detail = message)
                }
            }

            else -> {
                null
            }
        }
    }

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

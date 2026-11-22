package app.aroma.data.remote

import app.aroma.data.AromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody

/**
 * Reads the server-sent events of Mealie's streaming import endpoints: any number of
 * `progress` events with a message in the requested language, then `done` with the new
 * recipe's slug or `error` with a message or an error code.
 */
internal object ImportStream {
    private val ErrorCodes = setOf("BAD_RECIPE_DATA", "NO_RECIPE_DATA", "Recipe creation failed")
    private const val UNREACHABLE = "CONNECTION_ERROR"

    suspend fun slugFrom(body: ResponseBody, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            body.use { response ->
                val source = response.source()
                var event = ""
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            handle(event, data.toString(), onProgress)?.let { return@withContext it }
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
                handle(event, data.toString(), onProgress) ?: throw AromaError.ImportFailed()
            }
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
                    UNREACHABLE -> AromaError.ImportFailed(pageUnreachable = true)
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

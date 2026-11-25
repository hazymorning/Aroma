package app.aroma.feature.editor

import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason.NoAnswer
import app.aroma.data.isConnectivity
import app.aroma.data.mayStillFinish
import app.aroma.data.repository.RecipeRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration

/**
 * The recipe Mealie saves once an import or a rework's draft is done: the newest one the user added
 * since [known] were the newest, from [source] where it is set. Mealie saves it only once it is
 * complete, photo included, so the first sign of it is final. Looked for every [checkEvery] for as
 * long as [patience] lasts.
 */
internal suspend fun RecipeRepository.awaitAdded(known: Set<String>, source: String?, patience: Duration, checkEvery: Duration): String {
    repeat((patience / checkEvery).toInt().coerceAtLeast(1)) {
        delay(checkEvery)
        val added =
            try {
                addedSince(known, source)
            } catch (error: AromaError) {
                // A phone between networks answers again shortly; anything else will not change.
                if (!error.isConnectivity && !error.mayStillFinish()) throw error
                null
            }
        if (added != null) return added
    }
    throw AromaError.ImportFailed(NoAnswer)
}

/** Deletes a recipe nobody wants; failing here leaves it for the user to delete. */
internal suspend fun RecipeRepository.deleteQuietly(slug: String) {
    try {
        delete(slug)
    } catch (_: AromaError) {
        // See above.
    }
}

/** The objects of a recipe's list, such as its ingredients or steps. */
internal fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

/** A text of a recipe, where it says something. */
internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

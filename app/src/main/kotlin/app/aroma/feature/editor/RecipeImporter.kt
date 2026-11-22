package app.aroma.feature.editor

import app.aroma.core.format.SocialCaption
import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason.NoAnswer
import app.aroma.data.isConnectivity
import app.aroma.data.repository.RecipeRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the user asked for on the import screen. */
data class ImportRequest(
    val url: String,
    val text: String = "",
    val useAi: Boolean = false,
    val translateTo: String? = null,
)

/** How far an import has come, shown while it runs. */
sealed interface ImportStep {
    /** Mealie's own words for what it is doing, already in the app's language. */
    data class Server(val message: String) : ImportStep

    /** Waiting for a server that does not report its progress. */
    data object Waiting : ImportStep

    /** Mealie's AI turns a social media post into a recipe. */
    data object Rebuilding : ImportStep

    /** Aroma sorts a social media post into ingredients and steps itself. */
    data object Tidying : ImportStep

    /** The connection dropped while Mealie was still working; Aroma waits for the recipe to appear. */
    data object StillWorking : ImportStep
}

/**
 * Imports recipes and repairs what Mealie makes of social media posts. Mealie reads Instagram
 * and similar posts only through their link preview: the whole caption becomes the title and the
 * ingredients and steps are placeholders. Aroma rebuilds such a recipe from the caption, with
 * Mealie's AI when it may use it, otherwise by reading the caption's structure itself.
 *
 * [untitled] names a recipe whose caption starts straight with its ingredients. [patience] is how
 * long Aroma waits for a recipe after the connection dropped, checking every [checkEvery].
 */
class RecipeImporter(
    private val recipes: RecipeRepository,
    private val untitled: String,
    private val patience: Duration = 5.minutes,
    private val checkEvery: Duration = 5.seconds,
) {
    suspend fun import(request: ImportRequest, onStep: (ImportStep) -> Unit): String {
        onStep(ImportStep.Waiting)
        val report: (String) -> Unit = { onStep(ImportStep.Server(it)) }
        // Mealie's AI cannot read a post from its link, so posts always start with the regular import.
        val withAi = request.useAi && !request.url.isPost() && (request.text.isNotBlank() || request.translateTo != null)
        val withText = request.useAi && request.text.isNotBlank()
        val readWithAi = suspend { onServer(onStep) { recipes.importWithAi(request.url, request.text, request.translateTo, report) } }
        val readLink = suspend { onServer(onStep) { recipes.importFromUrl(request.url, report) } }
        val slug =
            if (withAi) {
                try {
                    readWithAi()
                } catch (failure: AromaError.ImportFailed) {
                    // Only a translation was asked for: an untranslated recipe beats no recipe.
                    if (failure.reason == NoAnswer || request.text.isNotBlank() || request.url.isBlank()) throw failure
                    readLink()
                }
            } else {
                try {
                    readLink()
                } catch (failure: AromaError.ImportFailed) {
                    // A post Mealie cannot load at all can still be read from the text pasted with it.
                    if (failure.reason == NoAnswer || !withText) throw failure
                    readWithAi()
                }
            }
        return repairLinkPreview(slug, request, onStep)
    }

    /**
     * Runs one import on the server. An AI provider can keep Mealie busy for minutes, and a
     * reverse proxy or a mobile network may drop the request meanwhile, while Mealie finishes and
     * saves the recipe anyway. Aroma then waits for that recipe to appear instead of reporting an
     * error and leaving the recipe behind unnoticed.
     */
    private suspend fun onServer(onStep: (ImportStep) -> Unit, import: suspend () -> String): String {
        val known =
            try {
                recipes.newestRecipeIds()
            } catch (_: AromaError) {
                // Not even this answered: report the import's own error rather than wait.
                null
            }
        return try {
            import()
        } catch (failure: AromaError) {
            if (known == null || !failure.mayStillFinish()) throw failure
            onStep(ImportStep.StillWorking)
            awaitNewRecipe(known)
        }
    }

    /** Mealie saves a recipe only once it is complete, photo included, so the first sign of it is final. */
    private suspend fun awaitNewRecipe(known: Set<String>): String {
        repeat((patience / checkEvery).toInt().coerceAtLeast(1)) {
            delay(checkEvery)
            val added =
                try {
                    recipes.addedSince(known)
                } catch (error: AromaError) {
                    // A phone between networks answers again shortly; anything else will not change.
                    if (!error.isConnectivity && !error.mayStillFinish()) throw error
                    null
                }
            if (added != null) return added
        }
        throw AromaError.ImportFailed(NoAnswer)
    }

    /**
     * Whether the request reached Mealie before it failed. A connection that could not even be
     * opened never started an import.
     */
    private fun AromaError.mayStillFinish(): Boolean =
        when (this) {
            is AromaError.Offline -> cause !is ConnectException && cause !is NoRouteToHostException
            is AromaError.Server -> code in PROXY_GAVE_UP
            else -> false
        }

    /** Returns [slug] unchanged unless Mealie could only read the link preview; then the rebuilt recipe's slug. */
    internal suspend fun repairLinkPreview(slug: String, request: ImportRequest, onStep: (ImportStep) -> Unit): String {
        val recipe =
            try {
                recipes.recipeJson(slug)
            } catch (_: AromaError) {
                // The recipe exists; opening it beats an error, and its screen copes with being offline.
                return slug
            }
        if (!recipe.isLinkPreviewOnly()) return slug
        val caption = SocialCaption.extract(recipe.text("name").orEmpty(), recipe.text("description").orEmpty())
        val source = recipe.text("orgURL") ?: request.url
        if (request.useAi) {
            onStep(ImportStep.Rebuilding)
            val rebuilt =
                try {
                    // The text alone: with the link, Mealie would load the post a second time.
                    onServer(onStep) {
                        recipes.importWithAi(url = null, text = request.text.ifBlank { caption }, translateTo = request.translateTo) {
                            onStep(ImportStep.Server(it))
                        }
                    }
                } catch (failure: AromaError) {
                    // Mealie may still be writing the recipe; sorting the caption now would add a second one.
                    if (failure is AromaError.ImportFailed && failure.reason == NoAnswer) throw failure
                    null
                }
            if (rebuilt != null) {
                val linked =
                    try {
                        recipes.update(rebuilt, buildJsonObject { put("orgURL", source) })
                    } catch (_: AromaError) {
                        rebuilt
                    }
                copyPhoto(from = slug, to = linked)
                deleteQuietly(slug)
                return linked
            }
        }
        onStep(ImportStep.Tidying)
        return tidy(slug, caption, source)
    }

    /**
     * Writes the caption's own structure into a new recipe, through the same patch the editor
     * saves. The preview itself is not renamed: Mealie 2 keeps a folder per slug, and a slug made
     * of a whole caption is longer than file systems allow, so renaming it fails on the server.
     * If anything goes wrong, the preview stays as Mealie imported it.
     */
    private suspend fun tidy(preview: String, caption: String, source: String): String {
        val parsed = SocialCaption.parse(caption)
        val locale = Locale.getDefault()
        val slug =
            try {
                recipes.create(parsed.name.ifBlank { untitled })
            } catch (_: AromaError) {
                return preview
            }
        val tidied =
            try {
                val fresh = recipes.recipeJson(slug)
                val form =
                    RecipeDraft.formFrom(fresh, locale).copy(
                        description = parsed.description,
                        ingredients = parsed.ingredients.joinToString("\n"),
                        steps = parsed.steps.ifEmpty { listOf("") },
                        servings = parsed.servings?.let(RecipeDraft::formatNumber).orEmpty(),
                    )
                val extras =
                    buildJsonObject {
                        if (source.isNotBlank()) put("orgURL", source)
                        if (parsed.notes.isNotEmpty()) put("notes", JsonArray(parsed.notes.map(::note)))
                    }
                recipes.update(slug, JsonObject(RecipeDraft.patch(fresh, form, locale) { "" } + extras))
            } catch (_: AromaError) {
                deleteQuietly(slug)
                return preview
            }
        copyPhoto(from = preview, to = tidied)
        deleteQuietly(preview)
        return tidied
    }

    private fun note(text: String): JsonObject =
        buildJsonObject {
            put("title", "")
            put("text", text)
        }

    /** Cleanup after the new recipe exists; failing here must not lose it. */
    private suspend fun deleteQuietly(slug: String) {
        try {
            recipes.delete(slug)
        } catch (_: AromaError) {
            // Leaves a duplicate the user can delete, rather than losing the import.
        }
    }

    /**
     * The rebuilt recipe comes from the caption only, so the post's photo comes over from the
     * first import. Read again here: Mealie may have saved that photo only after the recipe.
     */
    private suspend fun copyPhoto(from: String, to: String) {
        try {
            val preview = recipes.recipeJson(from)
            val id = preview.text("id") ?: return
            if (preview.text("image") == null) return
            if (recipes.recipeJson(to).text("image") != null) return
            val photo = recipes.imageBytes(id) ?: return
            recipes.uploadImage(to, photo, "webp")
        } catch (_: AromaError) {
            // The recipe is complete without its photo; it can be added in the editor.
        }
    }

    /** Social media posts, which Mealie reads only through their link preview. */
    private fun String.isPost(): Boolean {
        val host = toHttpUrlOrNull()?.host?.removePrefix("www.")?.removePrefix("m.") ?: return false
        return POST_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun JsonObject.isLinkPreviewOnly(): Boolean {
        val ingredients = list("recipeIngredient").map { it.text("note") ?: it.text("display") }
        val steps = list("recipeInstructions").map { it.text("text") }
        return ingredients == listOf(PLACEHOLDER_INGREDIENTS) && steps == listOf(PLACEHOLDER_STEPS)
    }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        /** What Mealie (2.0 to 3.x) writes when it could only read the link preview. */
        const val PLACEHOLDER_INGREDIENTS = "Could not detect ingredients"
        const val PLACEHOLDER_STEPS = "Could not detect instructions"

        /** Bad gateway, unavailable and timeouts of reverse proxies, including Cloudflare's 52x. */
        val PROXY_GAVE_UP = setOf(502, 503, 504, 520, 522, 524)

        val POST_HOSTS = listOf("instagram.com", "facebook.com", "threads.net", "threads.com")
    }
}

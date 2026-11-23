package app.aroma.feature.editor

import app.aroma.core.format.SocialCaption
import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason.NoAnswer
import app.aroma.data.AromaError.ImportFailed.Reason.PostUnreadable
import app.aroma.data.isConnectivity
import app.aroma.data.remote.SocialPosts
import app.aroma.data.repository.RecipeRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the user asked for on the import screen: a link, a text or both. */
data class ImportRequest(
    val url: String,
    val text: String = "",
    val useAi: Boolean = false,
    val translateTo: String? = null,
)

/** One line of an import's checklist. */
sealed interface ImportStep {
    /** Aroma reads a post's caption and photo on the phone. */
    data object ReadingPost : ImportStep

    /** Mealie works on the recipe; until it reports more, or on servers that do not report at all. */
    data object Waiting : ImportStep

    /** Mealie's own words for what it is doing, already in the app's language. */
    data class Server(val message: String) : ImportStep

    /** Aroma saves the recipe it read from a text itself. */
    data object Saving : ImportStep

    /** The connection dropped while Mealie was working; Aroma waits for the recipe to appear. */
    data object StillWorking : ImportStep

    /** Aroma adds the post's photo to the new recipe. */
    data object AddingPhoto : ImportStep
}

/**
 * Imports recipes so that Mealie saves each one once, complete. Websites go to Mealie's importer.
 * A social media post is read on the phone first: Mealie sees no more of it than its link
 * preview, which it would save as a recipe without ingredients or steps. Mealie's AI, or Aroma
 * itself without AI, then makes the recipe from the post's text, and Aroma adds the post's link
 * and photo.
 *
 * [untitled] names a recipe whose text starts straight with its ingredients. [patience] is how
 * long Aroma waits for a recipe after the connection dropped, checking every [checkEvery].
 */
class RecipeImporter(
    private val recipes: RecipeRepository,
    private val posts: SocialPosts,
    private val untitled: () -> String,
    private val patience: Duration = 5.minutes,
    private val checkEvery: Duration = 5.seconds,
) {
    /**
     * Imports one recipe and returns its slug. [known] are the newest recipes from before the
     * import: a recipe Mealie saves after the connection dropped is the one not among them.
     */
    suspend fun import(request: ImportRequest, known: Set<String>, onStep: (ImportStep) -> Unit): String {
        val isPost = SocialPosts.isPost(request.url)
        if (isPost) onStep(ImportStep.ReadingPost)
        val post = if (isPost) posts.read(request.url) else null
        val text = request.text.ifBlank { post?.caption.orEmpty() }
        val slug =
            when {
                request.useAi -> withAi(request, text, isPost, known, onStep)
                text.isNotBlank() -> fromText(text, request.url, onStep)
                isPost -> throw AromaError.ImportFailed(PostUnreadable)
                else -> fromPage(request.url, known, onStep)
            }
        if (isPost) complete(slug, request.url, post?.image, onStep)
        return slug
    }

    /**
     * Picks up an import the app was ended in the middle of: its request may have reached Mealie
     * already, so it is not sent again; Aroma waits for its recipe instead.
     */
    suspend fun resume(request: ImportRequest, known: Set<String>, onStep: (ImportStep) -> Unit): String {
        onStep(ImportStep.StillWorking)
        val slug = awaitNewRecipe(known, request.url.ifBlank { null })
        if (SocialPosts.isPost(request.url)) complete(slug, request.url, posts.read(request.url)?.image, onStep)
        return slug
    }

    /**
     * Lets Mealie's AI build the recipe. A post goes as its text, which spares Mealie a page it
     * cannot read; its link goes along only when the text holds no recipe, so that Mealie can
     * listen to the video. Should the AI fail on a website, its regular import still gets the
     * recipe, untranslated.
     */
    private suspend fun withAi(
        request: ImportRequest,
        text: String,
        isPost: Boolean,
        known: Set<String>,
        onStep: (ImportStep) -> Unit,
    ): String {
        val link = request.url.takeIf { it.isNotBlank() && !(isPost && SocialCaption.parse(text).isRecipe) }
        return try {
            onServer(onStep, known, source = link) {
                recipes.importWithAi(link, text, request.translateTo) { onStep(ImportStep.Server(it)) }
            }
        } catch (failure: AromaError.ImportFailed) {
            when {
                failure.reason == NoAnswer -> throw failure
                isPost && text.isBlank() -> throw AromaError.ImportFailed(PostUnreadable)
                !isPost && link != null && text.isBlank() -> fromPage(link, known, onStep)
                else -> throw failure
            }
        }
    }

    /** Mealie's regular import. A page without recipe data would leave a recipe without ingredients or steps; it goes again. */
    private suspend fun fromPage(url: String, known: Set<String>, onStep: (ImportStep) -> Unit): String {
        val slug = onServer(onStep, known, source = url) { recipes.importFromUrl(url) { onStep(ImportStep.Server(it)) } }
        val recipe =
            try {
                recipes.recipeJson(slug)
            } catch (_: AromaError) {
                // The recipe exists; opening it beats an error, and its screen copes with being offline.
                return slug
            }
        if (!recipe.isLinkPreviewOnly()) return slug
        deleteQuietly(slug)
        throw AromaError.ImportFailed()
    }

    /** Saves the recipe Aroma reads from [text] itself, with [source] as its link, in one go. */
    private suspend fun fromText(text: String, source: String, onStep: (ImportStep) -> Unit): String {
        val parsed = SocialCaption.parse(text)
        if (parsed.ingredients.isEmpty() && parsed.steps.isEmpty()) throw AromaError.ImportFailed()
        onStep(ImportStep.Saving)
        val locale = Locale.getDefault()
        val slug = recipes.create(parsed.name.ifBlank { untitled() })
        return try {
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
        } catch (failure: AromaError) {
            // A recipe with nothing but a name helps nobody.
            deleteQuietly(slug)
            throw failure
        }
    }

    /** Adds what Mealie did not take from the post: its link and its photo. The recipe is complete without them. */
    private suspend fun complete(slug: String, url: String, image: String?, onStep: (ImportStep) -> Unit) {
        try {
            val recipe = recipes.recipeJson(slug)
            if (recipe.text("orgURL") == null) recipes.update(slug, buildJsonObject { put("orgURL", url) })
            if (recipe.text("image") != null || image == null) return
            onStep(ImportStep.AddingPhoto)
            val photo = posts.photo(image) ?: return
            recipes.uploadImage(slug, photo.bytes, photo.extension)
        } catch (_: AromaError) {
            // Link and photo can still be added in the editor.
        }
    }

    /**
     * Runs one import on the server. An AI provider can keep Mealie busy for minutes, and a
     * reverse proxy or a mobile network may drop the request meanwhile, while Mealie finishes and
     * saves the recipe anyway. Aroma then waits for that recipe instead of reporting an error and
     * leaving the recipe behind unnoticed.
     */
    private suspend fun onServer(onStep: (ImportStep) -> Unit, known: Set<String>, source: String?, import: suspend () -> String): String {
        onStep(ImportStep.Waiting)
        return try {
            import()
        } catch (failure: AromaError) {
            if (!failure.mayStillFinish()) throw failure
            onStep(ImportStep.StillWorking)
            awaitNewRecipe(known, source)
        }
    }

    /** Mealie saves a recipe only once it is complete, photo included, so the first sign of it is final. */
    private suspend fun awaitNewRecipe(known: Set<String>, source: String?): String {
        repeat((patience / checkEvery).toInt().coerceAtLeast(1)) {
            delay(checkEvery)
            val added =
                try {
                    recipes.addedSince(known, source)
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

    private fun note(text: String): JsonObject =
        buildJsonObject {
            put("title", "")
            put("text", text)
        }

    /** Cleanup of a recipe nobody wants; failing here leaves a duplicate the user can delete. */
    private suspend fun deleteQuietly(slug: String) {
        try {
            recipes.delete(slug)
        } catch (_: AromaError) {
            // See above.
        }
    }

    /** What Mealie (2.0 to 3.x) saves when it could read no more of a page than its link preview. */
    private fun JsonObject.isLinkPreviewOnly(): Boolean {
        val ingredients = list("recipeIngredient").map { it.text("note") ?: it.text("display") }
        val steps = list("recipeInstructions").map { it.text("text") }
        return ingredients == listOf(PLACEHOLDER_INGREDIENTS) && steps == listOf(PLACEHOLDER_STEPS)
    }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val PLACEHOLDER_INGREDIENTS = "Could not detect ingredients"
        const val PLACEHOLDER_STEPS = "Could not detect instructions"

        /** Bad gateway, unavailable and timeouts of reverse proxies, including Cloudflare's 52x. */
        val PROXY_GAVE_UP = setOf(502, 503, 504, 520, 522, 524)
    }
}

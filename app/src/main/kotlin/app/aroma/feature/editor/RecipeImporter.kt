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
 * Imports recipes so that Mealie saves each one once, complete. Websites go to Mealie's importer,
 * which reads their recipe data directly.
 *
 * A social media post shows its recipe only in its caption. Aroma reads the post on the phone
 * first; where the platform shows the phone just its sign-in page, Mealie reads the post from the
 * server, as it reads any link, and saves its link preview: the caption as the name, and the
 * photo. Either way the caption then becomes a proper recipe, the quickest way that does it
 * justice: a caption that lists its ingredients and steps Aroma sorts itself, at once, while
 * Mealie's AI, which asks its model several times in a row, reads a caption without that order and
 * translates. It gets the text alone, never the link, which shows it the same sign-in page.
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
     * Imports one recipe and returns its slug. Right before a request that creates a recipe goes
     * out, [onSending] gets the newest recipes: the recipe the request makes is the one not among
     * them, should the connection drop, or Android end the app, before the answer arrives.
     */
    suspend fun import(request: ImportRequest, onSending: suspend (Set<String>) -> Unit, onStep: (ImportStep) -> Unit): String {
        val sending = Sending(onStep, onSending)
        if (!SocialPosts.isPost(request.url)) return sending.fromWebsite(request)
        onStep(ImportStep.ReadingPost)
        val post = posts.read(request.url)
        val caption = request.text.ifBlank { post?.caption.orEmpty() }
        // Text pasted with the link, or the caption the phone read, makes the recipe, should it hold one.
        if (caption.isNotBlank()) {
            val slug = sending.fromCaption(caption, request, preview = null)
            if (slug != null) {
                complete(slug, request.url, post?.image, onStep)
                return slug
            }
        }
        // Mealie reads the post itself: from its video where the server transcribes one, otherwise
        // from its link preview, which also brings the photo the phone could not read.
        val preview =
            try {
                sending.fromLink(request.url)
            } catch (failure: AromaError.ImportFailed) {
                // Mealie may still be saving the post; a recipe of Aroma's own would end up beside it.
                if (failure.reason == NoAnswer) throw failure
                if (caption.isBlank()) throw AromaError.ImportFailed(PostUnreadable)
                // Neither Aroma nor the AI found a recipe in the caption; kept whole as the recipe's text, it beats none.
                return sending.fromText(caption, request.url, from = null).also { slug ->
                    complete(slug, request.url, post?.image, onStep)
                }
            }
        return sending.fromPreview(preview, request, caption, image = post?.image)
    }

    /**
     * Picks up an import the app was ended in the middle of: its request may have reached Mealie
     * already, so it is not sent again; Aroma waits for its recipe instead, and finishes a post's
     * link preview as the import would have. [known] are the newest recipes from right before the
     * last request that creates one, [before] from right before the first.
     */
    suspend fun resume(
        request: ImportRequest,
        known: Set<String>,
        before: Set<String>,
        onSending: suspend (Set<String>) -> Unit,
        onStep: (ImportStep) -> Unit,
    ): String {
        onStep(ImportStep.StillWorking)
        val slug = awaitNewRecipe(known, request.url.ifBlank { null })
        if (!SocialPosts.isPost(request.url)) return slug
        val sending = Sending(onStep, onSending)
        if (recipes.recipeJson(slug).isLinkPreviewOnly()) {
            return sending.fromPreview(slug, request, request.text, image = null)
        }
        // A finished recipe of a post leaves nothing of its link preview behind; [before] the import's
        // first request, any preview was someone else's, or the user's own from earlier.
        recipes
            .addedFrom(request.url, since = before)
            .filter { it != slug && recipes.recipeJson(it).isLinkPreviewOnly() }
            .forEach { preview -> sending.adoptPhoto(from = preview, to = slug) }
        complete(slug, request.url, posts.read(request.url)?.image, onStep)
        return slug
    }

    /** The requests of one import that create a recipe, each preceded by [onSending]. */
    private inner class Sending(private val onStep: (ImportStep) -> Unit, private val onSending: suspend (Set<String>) -> Unit) {
        /**
         * A website: Mealie's regular import, or its AI where text came with the link or the recipe
         * is to be translated. Pasted text that lists its ingredients and steps Aroma reads itself.
         * Should the AI fail, Aroma reads the text itself, and the link still has Mealie's regular
         * import, untranslated. Without the AI, pasted text is read by Aroma.
         */
        suspend fun fromWebsite(request: ImportRequest): String {
            if (request.translateTo == null && readsItself(request.text)) return fromText(request.text, request.url, from = null)
            val withAi = request.useAi && (request.text.isNotBlank() || request.translateTo != null) && recipes.canImportWithAi()
            if (!withAi) return if (request.text.isNotBlank()) fromText(request.text, request.url, from = null) else fromPage(request.url)
            return try {
                onServer(source = request.url.ifBlank { null }) {
                    recipes.importWithAi(request.url.ifBlank { null }, request.text, request.translateTo) { onStep(ImportStep.Server(it)) }
                }
            } catch (failure: AromaError.ImportFailed) {
                val parsed = SocialCaption.parse(request.text)
                when {
                    failure.reason == NoAnswer -> throw failure
                    parsed.ingredients.isNotEmpty() || parsed.steps.isNotEmpty() -> fromText(request.text, request.url, from = null)
                    request.url.isNotBlank() -> fromPage(request.url)
                    else -> throw failure
                }
            }
        }

        /** Mealie's regular import. A page without recipe data would leave a recipe without ingredients or steps; it goes again. */
        suspend fun fromPage(url: String): String {
            val slug = fromLink(url)
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

        /** Mealie reads [url] from the server and saves what it finds, if only the link preview. */
        suspend fun fromLink(url: String): String = onServer(source = url) { recipes.importFromUrl(url) { onStep(ImportStep.Server(it)) } }

        /**
         * Turns what Mealie saved for a post into the recipe the post holds. Where Mealie read the
         * recipe itself, from the post's video for instance, it is kept as it is. Otherwise Mealie
         * saved the link preview, whose caption becomes the recipe, unless the phone read one
         * already, which had its chance and is kept as the recipe's text. A sign-in page in its
         * place goes, and the recipe takes the photo the phone read, [image], instead of its logo.
         */
        suspend fun fromPreview(preview: String, request: ImportRequest, caption: String, image: String?): String {
            val recipe = recipes.recipeJson(preview)
            if (!recipe.isLinkPreviewOnly()) return preview
            val title = recipe.text("name").orEmpty()
            val description = recipe.text("description").orEmpty()
            val signIn = SocialCaption.isSignInPage(title, description)
            val text = caption.ifBlank { if (signIn) "" else SocialCaption.extract(title, description) }
            val into = preview.takeUnless { signIn }
            if (into == null || text.isBlank()) deleteQuietly(preview)
            if (text.isBlank()) throw AromaError.ImportFailed(PostUnreadable)
            val slug = (if (caption.isBlank()) fromCaption(text, request, into) else null) ?: fromText(text, request.url, from = into)
            // A recipe of its own, where the sign-in page went, still needs the post's link and photo.
            if (into == null) complete(slug, request.url, image, onStep)
            return slug
        }

        /**
         * The recipe [text] holds, made the quickest way that does it justice: a text that lists its
         * ingredients and steps Aroma reads itself, in a moment; one without that order, or one to be
         * translated, Mealie's AI reads, where it may be used. The link [preview], where there is
         * one, gives the recipe its photo. Null where neither finds a recipe in the text.
         */
        suspend fun fromCaption(text: String, request: ImportRequest, preview: String?): String? {
            if (request.translateTo == null && readsItself(text)) return fromText(text, request.url, from = preview)
            withAi(text, request, preview)?.let { return it }
            return if (holdsRecipe(text)) fromText(text, request.url, from = preview) else null
        }

        /**
         * Lets Mealie's AI read a post's [caption] as text, never from the link, which would show
         * it the platform's sign-in page. The post's link [preview], where Mealie saved one, gives
         * the recipe its photo and then goes. Null where the AI may not be used or found nothing.
         */
        suspend fun withAi(caption: String, request: ImportRequest, preview: String?): String? {
            if (!request.useAi || !recipes.canImportWithAi()) return null
            val built =
                try {
                    onServer(source = null) {
                        recipes.importWithAi(
                            url = null,
                            text = caption,
                            translateTo = request.translateTo,
                        ) { onStep(ImportStep.Server(it)) }
                    }
                } catch (failure: AromaError.ImportFailed) {
                    // Mealie may still be working on it; a recipe of Aroma's own could end up beside its.
                    if (failure.reason == NoAnswer) throw failure
                    // The AI found no recipe in the caption: Aroma reads it itself.
                    return null
                }
            if (preview != null) {
                adoptPhoto(from = preview, to = built)
                complete(built, request.url, image = null, onStep)
            }
            return built
        }

        /**
         * Saves the recipe Aroma reads from [text], with [source] as its link, as a new recipe, so
         * Mealie numbers its name where another recipe has it already. The link preview [from],
         * where there is one, gives it its photo and then goes. A caption without ingredients or
         * steps is kept whole as the description, which beats no recipe.
         */
        suspend fun fromText(text: String, source: String, from: String?): String {
            val parsed = SocialCaption.parse(text)
            val structured = parsed.ingredients.isNotEmpty() || parsed.steps.isNotEmpty()
            onStep(ImportStep.Saving)
            onSending(recipes.newestRecipeIds())
            val slug = recipes.create(parsed.name.ifBlank { untitled() })
            val locale = Locale.getDefault()
            val saved =
                try {
                    val fresh = recipes.recipeJson(slug)
                    val form =
                        RecipeDraft.formFrom(fresh, locale).copy(
                            description = if (structured) parsed.description else text.trim(),
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
                    // A new recipe with nothing but a name helps nobody; a link preview stays as Mealie saved it.
                    deleteQuietly(slug)
                    throw failure
                }
            if (from != null) adoptPhoto(from = from, to = saved)
            return saved
        }

        /**
         * Gives [to] the photo of the link preview [from], then removes the preview, so the post
         * leaves one recipe behind. The recipe is complete without the photo, should it not come.
         */
        suspend fun adoptPhoto(from: String, to: String) {
            onStep(ImportStep.AddingPhoto)
            try {
                val id = recipes.recipeJson(from).text("id")
                val photo = id?.let { recipes.photo(it) }
                if (photo != null) recipes.uploadImage(to, photo, "webp")
            } catch (_: AromaError) {
                // See above.
            }
            deleteQuietly(from)
        }

        /**
         * Runs one import on the server. An AI provider can keep Mealie busy for minutes, and a
         * reverse proxy or a mobile network may drop the request meanwhile, while Mealie finishes
         * and saves the recipe anyway. Aroma then waits for that recipe, told apart by the newest
         * recipes from right before the request, instead of reporting an error and leaving the
         * recipe behind unnoticed.
         */
        private suspend fun onServer(source: String?, import: suspend () -> String): String {
            onStep(ImportStep.Waiting)
            val known = recipes.newestRecipeIds()
            onSending(known)
            return try {
                import()
            } catch (failure: AromaError) {
                if (!failure.mayStillFinish()) throw failure
                onStep(ImportStep.StillWorking)
                awaitNewRecipe(known, source)
            }
        }
    }

    /**
     * Adds what Mealie did not take from the post: its link, and the photo the phone read with
     * the caption, also over an image of Mealie's own. Without the link Mealie had no photo to
     * take, yet 3.23 to 3.25 set an image anyway, one without a file. The recipe is complete
     * without them; both can be added later.
     */
    private suspend fun complete(slug: String, url: String, image: String?, onStep: (ImportStep) -> Unit) {
        try {
            if (recipes.recipeJson(slug).text("orgURL") == null) recipes.update(slug, buildJsonObject { put("orgURL", url) })
        } catch (_: AromaError) {
            // See above.
        }
        if (image == null) return
        onStep(ImportStep.AddingPhoto)
        try {
            val photo = posts.photo(image) ?: return
            recipes.uploadImage(slug, photo.bytes, photo.extension)
        } catch (_: AromaError) {
            // See above.
        }
    }

    /** Mealie saves an import's recipe only once it is complete, photo included, so the first sign of it is final. */
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

    /** Whether Aroma finds ingredients or steps in [text]. */
    private fun holdsRecipe(text: String): Boolean = SocialCaption.parse(text).let { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }

    /** Whether Aroma finds both ingredients and steps in [text], all it needs to make the recipe without the AI. */
    private fun readsItself(text: String): Boolean = SocialCaption.parse(text).let { it.ingredients.isNotEmpty() && it.steps.isNotEmpty() }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val PLACEHOLDER_INGREDIENTS = "Could not detect ingredients"
        const val PLACEHOLDER_STEPS = "Could not detect instructions"

        /** Bad gateway, unavailable and timeouts of reverse proxies, including Cloudflare's 52x. */
        val PROXY_GAVE_UP = setOf(502, 503, 504, 520, 522, 524)
    }
}

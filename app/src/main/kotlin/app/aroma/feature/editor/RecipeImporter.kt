package app.aroma.feature.editor

import app.aroma.core.format.SocialCaption
import app.aroma.core.format.withoutPictographs
import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason.NoAnswer
import app.aroma.data.AromaError.ImportFailed.Reason.PostUnreadable
import app.aroma.data.isConnectivity
import app.aroma.data.mayStillFinish
import app.aroma.data.remote.Photo
import app.aroma.data.remote.SocialPost
import app.aroma.data.remote.SocialPosts
import app.aroma.data.repository.RecipeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What the user asked for on the import screen: a link, a text, photos or several of them. The
 * [photos] are JPEGs in the app's files, which only Mealie's AI reads.
 */
data class ImportRequest(
    val url: String,
    val text: String = "",
    val useAi: Boolean = false,
    val translateTo: String? = null,
    val photos: List<File> = emptyList(),
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
 * photo. Either way the caption then becomes a proper recipe: Mealie's AI reads it as text, as
 * the group's prompts ask (never from the link, which shows the AI the same sign-in page), or,
 * without the AI, Aroma sorts it itself.
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
    suspend fun import(request: ImportRequest, onSending: suspend (Set<String>) -> Unit, onStep: (ImportStep) -> Unit): String =
        finish(importing(request, onSending, onStep), request.url)

    private suspend fun importing(request: ImportRequest, onSending: suspend (Set<String>) -> Unit, onStep: (ImportStep) -> Unit): String {
        val sending = Sending(onStep, onSending)
        if (request.photos.isNotEmpty()) return sending.fromPhotos(request)
        if (!SocialPosts.isPost(request.url)) return sending.fromWebsite(request)
        onStep(ImportStep.ReadingPost)
        val post = posts.read(request.url)
        // The post's photo loads while Mealie works on the recipe.
        return coroutineScope { sending.fromPost(request, post, post?.image?.let { image -> async { posts.photo(image) } }) }
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
    ): String = finish(resuming(request, known, before, onSending, onStep), request.url)

    private suspend fun resuming(
        request: ImportRequest,
        known: Set<String>,
        before: Set<String>,
        onSending: suspend (Set<String>) -> Unit,
        onStep: (ImportStep) -> Unit,
    ): String {
        onStep(ImportStep.StillWorking)
        val slug = awaitNewRecipe(known, request.url.ifBlank { null })
        // Photos go to Mealie's AI alone, so nothing of a post is left to finish.
        if (request.photos.isNotEmpty() || !SocialPosts.isPost(request.url)) return slug
        val sending = Sending(onStep, onSending)
        if (recipes.recipeJson(slug).isLinkPreviewOnly()) return sending.fromPreview(slug, request, request.text, photo = null)
        // A finished recipe of a post leaves nothing of its link preview behind; [before] the import's
        // first request, any preview was someone else's, or the user's own from earlier.
        recipes
            .addedFrom(request.url, since = before)
            .filter { it != slug && recipes.recipeJson(it).isLinkPreviewOnly() }
            .forEach { preview -> sending.adoptPhoto(from = preview, to = slug) }
        val image = posts.read(request.url)?.image
        sending.addPhoto(slug, image?.let { CompletableDeferred(posts.photo(it)) })
        return slug
    }

    /** The requests of one import that create a recipe, each preceded by [onSending]. */
    private inner class Sending(private val onStep: (ImportStep) -> Unit, private val onSending: suspend (Set<String>) -> Unit) {
        /**
         * A post: the text pasted with its link or the caption the phone read makes the recipe,
         * should it hold one; otherwise Mealie reads the post itself, from its video where the
         * server transcribes one, else from its link preview, which also brings the photo the
         * phone could not read. [photo] is the post's photo, loading meanwhile.
         */
        suspend fun fromPost(request: ImportRequest, post: SocialPost?, photo: Deferred<Photo?>?): String {
            val caption = request.text.ifBlank { post?.caption.orEmpty() }
            if (caption.isNotBlank()) {
                fromCaption(caption, request, preview = null)?.let { slug ->
                    return slug.also { addPhoto(it, photo) }
                }
            }
            val preview =
                try {
                    fromLink(request.url)
                } catch (failure: AromaError.ImportFailed) {
                    // Mealie may still be saving the post; a recipe of Aroma's own would end up beside it.
                    if (failure.reason == NoAnswer) throw failure
                    if (caption.isBlank()) throw AromaError.ImportFailed(PostUnreadable)
                    // Neither the AI nor Aroma found a recipe in the caption; kept whole as the recipe's text, it beats none.
                    return fromText(caption, request.url, from = null).also { addPhoto(it, photo) }
                }
            return fromPreview(preview, request, caption, photo)
        }

        /** Photos, which only Mealie's AI can read: with the text and the link that came with them, and nothing to fall back on. */
        suspend fun fromPhotos(request: ImportRequest): String {
            val url = request.url.ifBlank { null }
            return onServer(source = url) {
                recipes.importWithAi(url, request.text, request.translateTo, request.photos) { onStep(ImportStep.Server(it)) }
            }
        }

        /**
         * A website: Mealie's regular import, or its AI where text came with the link or the recipe
         * is to be translated. Should the AI fail, Aroma reads the text itself, and the link still
         * has Mealie's regular import, untranslated. Without the AI, pasted text is read by Aroma.
         */
        suspend fun fromWebsite(request: ImportRequest): String {
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
         * place goes, and the recipe takes the photo the phone read, [photo], instead of its logo.
         */
        suspend fun fromPreview(preview: String, request: ImportRequest, caption: String, photo: Deferred<Photo?>?): String {
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
            // A recipe of its own, where the sign-in page went, still needs the post's photo.
            if (into == null) addPhoto(slug, photo)
            return slug
        }

        /**
         * The recipe [text] holds: read by Mealie's AI where it may be used, which follows the
         * group's own prompts, otherwise, or where the AI finds none, sorted by Aroma. The link
         * [preview], where there is one, gives the recipe its photo. Null where neither finds a
         * recipe in the text.
         */
        suspend fun fromCaption(text: String, request: ImportRequest, preview: String?): String? {
            withAi(text, request, preview)?.let { return it }
            return if (holdsRecipe(text)) fromText(text, request.url, from = preview) else null
        }

        /** Gives the recipe the post's [photo], should it have come; the recipe is complete without it. */
        suspend fun addPhoto(slug: String, photo: Deferred<Photo?>?) {
            if (photo == null) return
            onStep(ImportStep.AddingPhoto)
            val image = photo.await() ?: return
            try {
                recipes.uploadImage(slug, image.bytes, image.extension)
            } catch (_: AromaError) {
                // See above.
            }
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
            if (preview != null) adoptPhoto(from = preview, to = built)
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
                            description = if (structured) parsed.description else text.withoutPictographs().trim(),
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

    /**
     * The last touch of every import, in one request: the [source] link, which Mealie keeps for a
     * page but not for a text, and no emoji, which Mealie's AI, a page or a video brings along and
     * Aroma's own reading leaves out: an amount behind one is not found to scale. The recipe is
     * complete without either and stays as it is where it cannot be changed.
     */
    private suspend fun finish(slug: String, source: String): String {
        val recipe =
            try {
                recipes.recipeJson(slug)
            } catch (_: AromaError) {
                return slug
            }
        val changes =
            buildMap<String, JsonElement> {
                if (source.isNotBlank() && recipe.text("orgURL") == null) put("orgURL", JsonPrimitive(source))
                for (key in listOf("name", "description")) {
                    val text = recipe.text(key) ?: continue
                    val clean = text.withoutPictographs()
                    if (clean != text && clean.isNotBlank()) put(key, JsonPrimitive(clean))
                }
                for ((key, fields) in CleanedLists) cleaned(recipe.list(key), fields)?.let { put(key, it) }
            }
        if (changes.isEmpty()) return slug
        return try {
            recipes.update(slug, JsonObject(changes))
        } catch (_: AromaError) {
            // A name another recipe has already: the rest is still worth cleaning.
            try {
                recipes.update(slug, JsonObject(changes - "name"))
            } catch (_: AromaError) {
                slug
            }
        }
    }

    /** [items] with their text [fields] free of emoji, or null where none had any. */
    private fun cleaned(items: List<JsonObject>, fields: Set<String>): JsonArray? {
        var changed = false
        val result =
            items.map { item ->
                JsonObject(
                    item.mapValues { (field, value) ->
                        val text = (value as? JsonPrimitive)?.takeIf { field in fields && it.isString }?.content ?: return@mapValues value
                        val clean = text.withoutPictographs()
                        if (clean == text) value else JsonPrimitive(clean).also { changed = true }
                    },
                )
            }
        return if (changed) JsonArray(result) else null
    }

    /** Whether Aroma finds ingredients or steps in [text]. */
    private fun holdsRecipe(text: String): Boolean = SocialCaption.parse(text).let { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        /** The lists of a recipe whose words a reader sees, and the fields of their items that hold them. */
        val CleanedLists =
            mapOf(
                "recipeIngredient" to setOf("title", "note", "display", "originalText"),
                "recipeInstructions" to setOf("title", "text"),
                "notes" to setOf("title", "text"),
            )

        const val PLACEHOLDER_INGREDIENTS = "Could not detect ingredients"
        const val PLACEHOLDER_STEPS = "Could not detect instructions"
    }
}

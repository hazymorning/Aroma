package app.aroma.feature.editor

import app.aroma.core.format.SocialCaption
import app.aroma.data.AromaError
import app.aroma.data.repository.RecipeRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale

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
}

/**
 * Imports recipes and repairs what Mealie makes of social media posts. Mealie reads Instagram
 * and similar posts only through their link preview: the whole caption becomes the title and the
 * ingredients and steps are placeholders. Aroma rebuilds such a recipe from the caption, with
 * Mealie's AI when it may use it, otherwise by reading the caption's structure itself.
 *
 * [untitled] names a recipe whose caption starts straight with its ingredients.
 */
class RecipeImporter(private val recipes: RecipeRepository, private val untitled: String) {
    suspend fun import(request: ImportRequest, onStep: (ImportStep) -> Unit): String {
        onStep(ImportStep.Waiting)
        val report: (String) -> Unit = { onStep(ImportStep.Server(it)) }
        val withAi = request.useAi && (request.text.isNotBlank() || request.translateTo != null)
        val slug =
            if (withAi) {
                try {
                    recipes.importWithAi(request.url, request.text, request.translateTo, report)
                } catch (failure: AromaError.ImportFailed) {
                    // Only a translation was asked for: an untranslated recipe beats no recipe.
                    if (request.text.isNotBlank() || request.url.isBlank()) throw failure
                    recipes.importFromUrl(request.url, report)
                }
            } else {
                recipes.importFromUrl(request.url, report)
            }
        return repairLinkPreview(slug, request, onStep)
    }

    /** Returns [slug] unchanged unless Mealie could only read the link preview; then the rebuilt recipe's slug. */
    internal suspend fun repairLinkPreview(slug: String, request: ImportRequest, onStep: (ImportStep) -> Unit): String {
        val recipe = recipes.recipeJson(slug)
        if (!recipe.isLinkPreviewOnly()) return slug
        val caption = SocialCaption.extract(recipe.text("name").orEmpty(), recipe.text("description").orEmpty())
        if (request.useAi) {
            onStep(ImportStep.Rebuilding)
            val source = recipe.text("orgURL") ?: request.url
            val rebuilt =
                try {
                    recipes.importWithAi(source, caption, request.translateTo) { onStep(ImportStep.Server(it)) }
                } catch (_: AromaError) {
                    null
                }
            if (rebuilt != null) {
                copyPhoto(from = recipe, to = rebuilt)
                deleteQuietly(slug)
                return rebuilt
            }
        }
        onStep(ImportStep.Tidying)
        return tidy(slug, recipe, caption)
    }

    /**
     * Writes the caption's own structure into a new recipe, through the same patch the editor
     * saves. The preview itself is not renamed: Mealie 2 keeps a folder per slug, and a slug made
     * of a whole caption is longer than file systems allow, so renaming it fails on the server.
     * If anything goes wrong, the preview stays as Mealie imported it.
     */
    private suspend fun tidy(preview: String, recipe: JsonObject, caption: String): String {
        val parsed = SocialCaption.parse(caption)
        val locale = Locale.getDefault()
        val slug = recipes.create(parsed.name.ifBlank { untitled })
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
                        recipe.text("orgURL")?.let { put("orgURL", it) }
                        if (parsed.notes.isNotEmpty()) put("notes", JsonArray(parsed.notes.map(::note)))
                    }
                recipes.update(slug, JsonObject(RecipeDraft.patch(fresh, form, locale) { "" } + extras))
            } catch (_: AromaError) {
                deleteQuietly(slug)
                return preview
            }
        copyPhoto(from = recipe, to = tidied)
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

    /** The AI builds its recipe from text only, so the post's photo comes over from the first import. */
    private suspend fun copyPhoto(from: JsonObject, to: String) {
        val id = from.text("id") ?: return
        if (from.text("image") == null) return
        try {
            if (recipes.recipeJson(to).text("image") != null) return
            val photo = recipes.imageBytes(id) ?: return
            recipes.uploadImage(to, photo, "webp")
        } catch (_: AromaError) {
            // The recipe is complete without its photo; it can be added in the editor.
        }
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
    }
}

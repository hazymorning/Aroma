package app.aroma.feature.editor

import android.content.res.Resources
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.withoutPictographs
import app.aroma.core.model.Ingredient
import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason.NoAnswer
import app.aroma.data.isConnectivity
import app.aroma.data.mayStillFinish
import app.aroma.data.remote.MealieJson
import app.aroma.data.remote.dto.IngredientDto
import app.aroma.data.remote.toModel
import app.aroma.data.repository.RecipeRepository
import app.aroma.ui.components.formatDurationPlain
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Lets Mealie's AI go over a recipe that is there already: order its ingredients, steps and sections,
 * link the ingredients to the steps that use them, file it under tags and categories, and translate it
 * on request. Mealie's AI only makes new recipes, so it gets this one as plain text and makes a draft
 * of it. The recipe takes the draft's content in one change and keeps all that is its own, such as its
 * photo, rating, link, comments and timeline; the draft goes again, whatever happens once it exists.
 *
 * [resources] give the words the text is written with, in the app's language. [patience] is how long
 * Aroma waits for the draft after the connection dropped, checking every [checkEvery].
 */
class RecipeReworker(
    private val recipes: RecipeRepository,
    private val resources: () -> Resources,
    private val patience: Duration = 5.minutes,
    private val checkEvery: Duration = 5.seconds,
) {
    /**
     * Reworks the recipe with [recipeId], translated into [translateTo] where it is set. Right before
     * the request that makes the draft goes out, [onSending] gets the newest recipes: the draft is the
     * one not among them, should the connection drop, or Android end the app, before the answer arrives.
     */
    suspend fun rework(
        recipeId: String,
        translateTo: String?,
        onSending: suspend (Set<String>) -> Unit,
        onStep: (ImportStep) -> Unit,
    ): ReworkOutcome.Reworked {
        onStep(ImportStep.Waiting)
        val text = text(recipes.recipeJson(recipeId))
        val known = recipes.newestRecipeIds()
        onSending(known)
        val draft =
            try {
                recipes.importWithAi(url = null, text = text, translateTo = translateTo) { onStep(ImportStep.Server(it)) }
            } catch (failure: AromaError) {
                if (!failure.mayStillFinish()) throw failure
                onStep(ImportStep.StillWorking)
                awaitDraft(known)
            }
        return adopt(recipeId, draft, translated = translateTo != null)
    }

    /**
     * Picks up a rework the app was ended in the middle of. Its request may have reached Mealie
     * already, so it is not sent again: Aroma waits for the draft it makes, the recipe that is not
     * among [known], the newest recipes from right before the request.
     */
    suspend fun resume(recipeId: String, translated: Boolean, known: Set<String>, onStep: (ImportStep) -> Unit): ReworkOutcome.Reworked {
        onStep(ImportStep.StillWorking)
        return adopt(recipeId, awaitDraft(known), translated)
    }

    /** Gives the recipe the content of the [draft] and removes the draft; should that fail, the recipe stays as it was. */
    private suspend fun adopt(recipeId: String, draft: String, translated: Boolean): ReworkOutcome.Reworked {
        val (recipe, patch) =
            try {
                val recipe = recipes.recipeJson(recipeId)
                recipe to patch(recipe, recipes.recipeJson(draft), translated)
            } catch (failure: AromaError) {
                deleteQuietly(draft)
                throw failure
            }
        // The draft goes first: the recipe may take its name, which Mealie gives one recipe only.
        recipes.delete(draft)
        val slug = recipes.update(recipe.text("slug") ?: recipeId, patch)
        return ReworkOutcome.Reworked(slug, undo = JsonObject(patch.keys.associateWith { recipe[it] ?: JsonNull }))
    }

    /**
     * What the [recipe] takes from the AI's [draft]: the description, yield, times, ingredients, steps
     * with the ingredients they use, and notes, each where the draft has some, and the tags and
     * categories beside its own; the name only when [translated]. All else stays the recipe's own.
     */
    private fun patch(recipe: JsonObject, draft: JsonObject, translated: Boolean): JsonObject =
        buildJsonObject {
            // Mealie gives a name to one recipe only: where the name stayed this recipe's, the draft
            // has it with a number, and this recipe's slug with that number.
            val numbered = recipe.text("slug")?.let { slug -> draft.text("slug")?.matches(Regex("${Regex.escape(slug)}-\\d+")) } == true
            val name = draft.text("name")?.takeIf { translated && !numbered && it != recipe.text("name") }
            name?.let { put("name", JsonPrimitive(it)) }
            for (key in TAKEN) draft[key]?.takeIf { it.saysSomething() && it != recipe[key] }?.let { put(key, it) }
            for (key in ORGANIZERS) joined(recipe.list(key), draft.list(key))?.let { put(key, it) }
        }

    /** The recipe's [own] tags or categories with those of the draft it has not, by id; null where the draft adds none. */
    private fun joined(own: List<JsonObject>, draft: List<JsonObject>): JsonArray? {
        val ids = own.mapTo(mutableSetOf()) { it.text("id") }
        val added = draft.filter { it.text("id") !in ids }
        return if (added.isEmpty()) null else JsonArray(own + added)
    }

    /**
     * The recipe as plain text, as a cook writes one down: name, description, yield and times, then
     * ingredients and steps under the headings of their sections, then notes. Without emoji, and
     * without "<" and ">", for which Mealie would take it for a web page and have its AI read it first.
     */
    private fun text(recipe: JsonObject): String {
        val words = resources()
        val locale = Locale.getDefault()
        val ingredients =
            recipe.list("recipeIngredient").flatMap { raw ->
                val ingredient = MealieJson.decodeFromJsonElement(IngredientDto.serializer(), raw).toModel()
                listOfNotNull(ingredient.section?.let { "\n${oneLine(it)}" }, "- ${line(ingredient, locale)}")
            }
        val steps =
            recipe
                .list("recipeInstructions")
                .filter { it.text("text") != null }
                .flatMapIndexed { index, step ->
                    val text = listOfNotNull(step.text("summary"), step.text("text")).joinToString(": ")
                    listOfNotNull(step.text("title")?.let { "\n${oneLine(it)}" }, "${index + 1}. ${oneLine(text)}")
                }
        val notes =
            recipe
                .list("notes")
                .map { note -> listOfNotNull(note.text("title")?.let(::oneLine), note.text("text")?.let(::untagged)).joinToString(": ") }
                .filter { it.isNotBlank() }
                .mapIndexed { index, note -> if (index == 0) note else "\n$note" }
        val blocks =
            listOf(
                listOfNotNull(recipe.text("name")?.let(::oneLine)),
                listOfNotNull(recipe.text("description")?.let(::untagged)),
                listOf(yieldOf(recipe, words), timesOf(recipe, words)).filter { it.isNotEmpty() },
                headed(words.getString(R.string.recipe_ingredients), ingredients),
                headed(words.getString(R.string.recipe_steps), steps),
                headed(words.getString(R.string.recipe_notes), notes),
            )
        // Emoji go too, as Aroma takes them out of every recipe Mealie's AI makes in an import.
        return blocks
            .filter { it.isNotEmpty() }
            .joinToString("\n\n") { it.joinToString("\n") }
            .withoutPictographs()
            .replace('<', '‹')
            .replace('>', '›')
    }

    /** An ingredient on one line, as the recipe page lists it: amount, unit and food, then the note after a comma. */
    private fun line(ingredient: Ingredient, locale: Locale): String {
        val line = IngredientFormatter.format(ingredient, 1.0, locale)
        val named = listOf(line.measureText(), line.text).filter { it.isNotBlank() }.joinToString(" ")
        return oneLine(listOfNotNull(named.ifEmpty { null }, line.note).joinToString(", "))
    }

    /** How many servings the recipe makes and what else it yields, such as "1 loaf". */
    private fun yieldOf(recipe: JsonObject, words: Resources): String {
        val servings =
            recipe.number("recipeServings")?.takeIf { it > 0 }?.let { count ->
                words.getQuantityString(R.plurals.servings, count.roundToInt(), RecipeDraft.formatNumber(count))
            }
        val made =
            recipe.text("recipeYield")?.let { what ->
                listOfNotNull(
                    recipe.number("recipeYieldQuantity")?.takeIf { it > 0 }?.let(RecipeDraft::formatNumber),
                    what,
                ).joinToString(" ")
            }
        return listOfNotNull(servings, made).joinToString(" · ")
    }

    /** Prep, cook and total time, each with its name: Mealie 3's seconds where it has them, its text otherwise. */
    private fun timesOf(recipe: JsonObject, words: Resources): String =
        TIMES
            .mapNotNull { (label, key) ->
                val time =
                    recipe.number("${key}Seconds")?.takeIf { it > 0 }?.let { words.formatDurationPlain(it.roundToInt()) }
                        ?: recipe.text(key)
                time?.let { "${words.getString(label)}: $it" }
            }.joinToString(" · ")

    /** The draft Mealie saves once it is done: the newest recipe the user added since [known] were the newest. */
    private suspend fun awaitDraft(known: Set<String>): String {
        repeat((patience / checkEvery).toInt().coerceAtLeast(1)) {
            delay(checkEvery)
            val added =
                try {
                    recipes.addedSince(known, source = null)
                } catch (error: AromaError) {
                    // A phone between networks answers again shortly; anything else will not change.
                    if (!error.isConnectivity && !error.mayStillFinish()) throw error
                    null
                }
            if (added != null) return added
        }
        throw AromaError.ImportFailed(NoAnswer)
    }

    /** Removes a draft nobody wants; failing here leaves it for the user to delete. */
    private suspend fun deleteQuietly(slug: String) {
        try {
            recipes.delete(slug)
        } catch (_: AromaError) {
            // See above.
        }
    }

    private companion object {
        /** The fields the recipe takes from the draft wherever the draft fills them. */
        val TAKEN =
            listOf(
                "description",
                "recipeServings",
                "recipeYieldQuantity",
                "recipeYield",
                "prepTime",
                "prepTimeSeconds",
                "performTime",
                "performTimeSeconds",
                "totalTime",
                "totalTimeSeconds",
                "recipeIngredient",
                "recipeInstructions",
                "notes",
            )

        /** The organizers the recipe keeps and adds the draft's to. */
        val ORGANIZERS = listOf("tags", "recipeCategory")

        /** Mealie's times, with the names the recipe page gives them. */
        val TIMES =
            listOf(
                R.string.recipe_prep_time to "prepTime",
                R.string.recipe_cook_time to "performTime",
                R.string.recipe_total_time to "totalTime",
            )
    }
}

/** An HTML tag, which has a letter or a slash right after its "<", unlike "< 5 min". */
private val Tag = Regex("""</?[A-Za-z][^<>]*>""")

/** The tags that end a line: a break, and the end of a paragraph, a block or an item of a list. */
private val LineEnd = Regex("""<br\s*/?>|</(p|div|li)>""", RegexOption.IGNORE_CASE)

/**
 * [text] without the HTML some pages and editors leave in a recipe; a tag that ended a line still
 * does. Any other "<" or ">" becomes the quote it looks like once the whole text is put together.
 */
private fun untagged(text: String): String = text.replace(LineEnd, "\n").replace(Tag, "").trim()

/** [text] on one line, as an item of a list needs it. */
private fun oneLine(text: String): String =
    untagged(text)
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")

/** [lines] under their [heading], or nothing at all without lines. */
private fun headed(heading: String, lines: List<String>): List<String> = if (lines.isEmpty()) emptyList() else listOf(heading) + lines

/** Whether a value of a recipe says anything: Mealie leaves a field out as null, blank, empty or zero. */
private fun JsonElement.saysSomething(): Boolean =
    when (this) {
        JsonNull -> false
        is JsonPrimitive -> if (isString) content.isNotBlank() else doubleOrNull != 0.0
        is JsonArray -> isNotEmpty()
        is JsonObject -> isNotEmpty()
    }

private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

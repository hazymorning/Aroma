package app.aroma.feature.editor

import app.aroma.core.format.DurationDetector
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.model.Organizer
import app.aroma.data.remote.MealieJson
import app.aroma.data.remote.dto.IngredientDto
import app.aroma.data.remote.dto.OrganizerDto
import app.aroma.data.remote.servingsFrom
import app.aroma.data.remote.toModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.Locale
import java.util.UUID

/** What the editor shows: plain text that is easy to change on a phone. */
data class EditorForm(
    val name: String = "",
    val description: String = "",
    val servings: String = "",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val totalMinutes: String = "",
    val ingredients: String = "",
    val steps: List<String> = listOf(""),
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
)

/**
 * Converts between Mealie's recipe JSON and [EditorForm]. Saving produces a patch with only
 * the changed fields; ingredients and steps whose text did not change keep their original
 * objects, so parsed amounts, references and unknown fields survive the round trip.
 */
object RecipeDraft {
    private const val SECTION_PREFIX = "# "

    fun formFrom(recipe: JsonObject, locale: Locale): EditorForm {
        val ingredientLines =
            buildList {
                recipe.ingredients().forEach { raw ->
                    val ingredient = raw.toIngredient()
                    ingredient.section?.let { add(SECTION_PREFIX + it) }
                    add(lineOf(raw, locale))
                }
            }
        val steps = recipe.instructions().map { it.string("text").orEmpty() }
        return EditorForm(
            name = recipe.string("name").orEmpty(),
            description = recipe.string("description").orEmpty(),
            servings =
                servingsFrom(
                    recipe.number("recipeServings"),
                    recipe.string("recipeYield"),
                ).takeIf { it > 0 }?.let(::formatNumber).orEmpty(),
            prepMinutes = minutes(recipe, "prepTime"),
            cookMinutes = minutes(recipe, "performTime"),
            totalMinutes = minutes(recipe, "totalTime"),
            ingredients = ingredientLines.joinToString("\n"),
            steps = steps.ifEmpty { listOf("") },
            categories = recipe.organizers("recipeCategory"),
            tags = recipe.organizers("tags"),
        )
    }

    fun patch(original: JsonObject, form: EditorForm, locale: Locale, durationText: (Int) -> String): JsonObject {
        val before = formFrom(original, locale)
        return buildJsonObject {
            if (form.name.trim() != before.name) put("name", form.name.trim())
            if (form.description.trim() != before.description) put("description", form.description.trim())
            if (form.servings.trim() != before.servings) {
                val servings =
                    form.servings
                        .trim()
                        .replace(',', '.')
                        .toDoubleOrNull() ?: 0.0
                if ("recipeServings" in original) put("recipeServings", servings) else put("recipeYield", form.servings.trim())
            }
            putTime("prepTime", form.prepMinutes, before.prepMinutes, durationText)
            putTime("performTime", form.cookMinutes, before.cookMinutes, durationText)
            putTime("totalTime", form.totalMinutes, before.totalMinutes, durationText)
            if (form.ingredients.trim() != before.ingredients.trim()) {
                put("recipeIngredient", JsonArray(ingredients(original, form.ingredients, locale)))
            }
            val steps = form.steps.map { it.trim() }.filter { it.isNotEmpty() }
            if (steps != before.steps.map { it.trim() }.filter { it.isNotEmpty() }) {
                put("recipeInstructions", JsonArray(instructions(original, steps)))
            }
            if (form.categories.map { it.id } != before.categories.map { it.id }) {
                put("recipeCategory", organizerArray(form.categories))
            }
            if (form.tags.map { it.id } != before.tags.map { it.id }) put("tags", organizerArray(form.tags))
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putTime(
        field: String,
        value: String,
        before: String,
        durationText: (Int) -> String,
    ) {
        if (value.trim() == before) return
        val seconds =
            value
                .trim()
                .toIntOrNull()
                ?.takeIf { it > 0 }
                ?.times(60)
        put(field, seconds?.let(durationText)?.let(::JsonPrimitive) ?: JsonNull)
        put("${field}Seconds", seconds?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun ingredients(original: JsonObject, text: String, locale: Locale): List<JsonObject> {
        val unused =
            original
                .ingredients()
                .groupBy { lineOf(it, locale) }
                .mapValues { it.value.toMutableList() }
                .toMutableMap()
        var section: String? = null
        val result = mutableListOf<JsonObject>()
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            if (line.startsWith("#")) {
                section = line.trimStart('#').trim().ifEmpty { null }
                return@forEach
            }
            val reused = unused[line]?.removeFirstOrNull()
            val item =
                reused ?: buildJsonObject {
                    put("referenceId", UUID.randomUUID().toString())
                    put("quantity", 0)
                    put("unit", JsonNull)
                    put("food", JsonNull)
                    put("note", line)
                    put("display", line)
                    put("originalText", line)
                }
            result += JsonObject(item + ("title" to (section?.let(::JsonPrimitive) ?: JsonNull)))
            section = null
        }
        return result
    }

    private fun instructions(original: JsonObject, steps: List<String>): List<JsonObject> {
        val unused =
            original
                .instructions()
                .groupBy {
                    it
                        .string(
                            "text",
                        ).orEmpty()
                        .trim()
                }.mapValues { it.value.toMutableList() }
                .toMutableMap()
        return steps.map { text ->
            unused[text]?.removeFirstOrNull() ?: buildJsonObject {
                put("id", UUID.randomUUID().toString())
                put("title", "")
                put("text", text)
                putJsonArray("ingredientReferences") {}
            }
        }
    }

    private fun organizerArray(items: List<Organizer>): JsonElement =
        MealieJson.encodeToJsonElement(ListSerializer(OrganizerDto.serializer()), items.map { OrganizerDto(it.id, it.name, it.slug) })

    private fun lineOf(raw: JsonObject, locale: Locale): String = IngredientFormatter.format(raw.toIngredient(), 1.0, locale).toPlainText()

    private fun minutes(recipe: JsonObject, field: String): String {
        val seconds = recipe.int("${field}Seconds") ?: recipe.string(field)?.let { DurationDetector.detect(it).sumOf { d -> d.seconds } }
        return seconds?.takeIf { it > 0 }?.let { (it / 60).toString() }.orEmpty()
    }

    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun JsonObject.toIngredient() = MealieJson.decodeFromJsonElement(IngredientDto.serializer(), this).toModel()

    private fun JsonObject.ingredients(): List<JsonObject> = (this["recipeIngredient"] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.instructions(): List<JsonObject> = (this["recipeInstructions"] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.organizers(field: String): List<Organizer> =
        (this[field] as? JsonArray)?.map { MealieJson.decodeFromJsonElement(OrganizerDto.serializer(), it).toModel() }.orEmpty()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}

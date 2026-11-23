package app.aroma.data.remote

import app.aroma.core.model.Cookbook
import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import app.aroma.core.model.LinkedRecipe
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.core.model.NutritionKind
import app.aroma.core.model.NutritionValue
import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeNote
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime
import app.aroma.core.model.Step
import app.aroma.core.model.TimelineEvent
import app.aroma.core.model.User
import app.aroma.data.remote.dto.CookbookDto
import app.aroma.data.remote.dto.FoodDto
import app.aroma.data.remote.dto.IngredientDto
import app.aroma.data.remote.dto.MealPlanEntryDto
import app.aroma.data.remote.dto.NutritionDto
import app.aroma.data.remote.dto.OrganizerDto
import app.aroma.data.remote.dto.RecipeDto
import app.aroma.data.remote.dto.RecipeSummaryDto
import app.aroma.data.remote.dto.TimelineEventDto
import app.aroma.data.remote.dto.UnitDto
import app.aroma.data.remote.dto.UserDto
import java.time.Instant
import java.time.LocalDate

private fun time(seconds: Int?, text: String?): RecipeTime? =
    RecipeTime(seconds?.takeIf { it > 0 }, text?.takeIf { it.isNotBlank() }).takeUnless { it.isEmpty }

private fun image(serverUrl: String, recipeId: String, key: String?): RecipeImage? =
    key?.takeIf { it.isNotBlank() && it != "false" }?.let { RecipeImage(serverUrl, recipeId, it) }

/** Mealie 2.0 has no servings field; the count is the leading number of the yield text there. */
internal fun servingsFrom(servings: Double?, yieldText: String?): Double =
    servings ?: yieldText?.trim()?.let {
        LeadingNumber
            .find(it)
            ?.value
            ?.replace(',', '.')
            ?.toDoubleOrNull()
    } ?: 0.0

private val LeadingNumber = Regex("""^\d+(?:[.,]\d+)?""")

private fun date(value: String?): LocalDate? = value?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Mealie writes some timestamps without a zone; they are in UTC. */
private fun instant(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else "${it}Z") }.getOrNull() }

fun OrganizerDto.toModel() = Organizer(id = id, name = name, slug = slug)

fun RecipeSummaryDto.toModel(serverUrl: String) =
    RecipeSummary(
        id = id,
        slug = slug,
        name = name,
        description = description.orEmpty(),
        image = image(serverUrl, id, image),
        totalTime = time(totalTimeSeconds, totalTime),
        prepTime = time(prepTimeSeconds, prepTime),
        cookTime = time(performTimeSeconds, performTime ?: cookTime),
        servings = servingsFrom(recipeServings, recipeYield),
        rating = rating,
        categories = recipeCategory.map { it.toModel() },
        tags = tags.map { it.toModel() },
    )

fun RecipeDto.toModel(serverUrl: String) =
    Recipe(
        id = id,
        slug = slug,
        name = name,
        description = description.orEmpty(),
        image = image(serverUrl, id, image),
        totalTime = time(totalTimeSeconds, totalTime),
        prepTime = time(prepTimeSeconds, prepTime),
        cookTime = time(performTimeSeconds, performTime ?: cookTime),
        servings = servingsFrom(recipeServings, recipeYield),
        rating = rating,
        categories = recipeCategory.map { it.toModel() },
        tags = tags.map { it.toModel() },
        tools = tools.map { it.toModel() },
        sourceUrl = orgURL?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
        ingredients = recipeIngredient.map { it.toModel() },
        steps =
            recipeInstructions.orEmpty().mapNotNull { step ->
                step.text?.takeIf { it.isNotBlank() }?.let { text ->
                    Step(
                        id = step.id,
                        title = step.title?.takeIf { it.isNotBlank() },
                        text = text.trim(),
                        ingredientReferences = step.ingredientReferences.mapNotNull { it.referenceId },
                        durationSeconds = step.durationSeconds,
                    )
                }
            },
        notes =
            notes.orEmpty().mapNotNull { note ->
                val text = note.text.orEmpty().trim()
                if (text.isEmpty() && note.title.isNullOrBlank()) null else RecipeNote(note.title.orEmpty().trim(), text)
            },
        nutrition = if (settings?.showNutrition == true) nutrition?.toModel().orEmpty() else emptyList(),
    )

fun IngredientDto.toModel() =
    Ingredient(
        referenceId = referenceId,
        section = title?.takeIf { it.isNotBlank() },
        quantity = quantity ?: 0.0,
        unit = unit?.toModel(),
        food = food?.toModel(),
        note = note.orEmpty(),
        display = display.orEmpty(),
        linkedRecipe = referencedRecipe?.slug?.let { LinkedRecipe(it, referencedRecipe.name.orEmpty()) },
    )

fun UnitDto.toModel() =
    IngredientUnit(
        id = id,
        name = name,
        pluralName = pluralName,
        abbreviation = abbreviation.orEmpty(),
        pluralAbbreviation = pluralAbbreviation,
        useAbbreviation = useAbbreviation,
        useFraction = fraction,
        standardQuantity = standardQuantity,
        standardUnit = standardUnit,
    )

fun FoodDto.toModel() = IngredientFood(id = id, name = name, pluralName = pluralName)

private fun NutritionDto.toModel(): List<NutritionValue> =
    listOf(
        NutritionKind.Calories to calories,
        NutritionKind.Protein to proteinContent,
        NutritionKind.Fat to fatContent,
        NutritionKind.SaturatedFat to saturatedFatContent,
        NutritionKind.Carbohydrates to carbohydrateContent,
        NutritionKind.Sugar to sugarContent,
        NutritionKind.Fiber to fiberContent,
        NutritionKind.Sodium to sodiumContent,
        NutritionKind.Cholesterol to cholesterolContent,
    ).mapNotNull { (kind, value) -> value?.takeIf { it.isNotBlank() }?.let { NutritionValue(kind, it) } }

/** Null for an event without a time, which Mealie does not write. */
fun TimelineEventDto.toModel(recipe: RecipeSummary): TimelineEvent? =
    instant(timestamp)?.let { time ->
        TimelineEvent(id = id, recipe = recipe, subject = subject, message = eventMessage?.takeIf { it.isNotBlank() }, time = time)
    }

fun CookbookDto.toModel() = Cookbook(id = id, slug = slug ?: id, name = name, description = description.orEmpty())

fun MealPlanEntryDto.toModel(serverUrl: String): MealPlanEntry? {
    val day = date(date) ?: return null
    return MealPlanEntry(
        id = id,
        date = day,
        type = MealType.fromApi(entryType),
        title = title.orEmpty(),
        note = text.orEmpty(),
        recipe = recipe?.toModel(serverUrl),
    )
}

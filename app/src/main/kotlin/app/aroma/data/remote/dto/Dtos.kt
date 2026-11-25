package app.aroma.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Wire formats of the Mealie API (v2 and v3). Fields of responses have defaults where servers may
 * leave them out; MealieJson ignores fields Aroma does not know. Fields of requests have none,
 * because MealieJson does not send a value equal to its default.
 */

@Serializable
data class AboutDto(val version: String = "", val allowPasswordLogin: Boolean = true)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String = "",
    val fullName: String? = null,
    val email: String = "",
    val groupSlug: String? = null,
)

@Serializable
data class ApiTokenRequest(val name: String)

@Serializable
data class ApiTokenDto(val id: Int, val name: String = "", val token: String)

@Serializable
data class PageDto<T>(
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val items: List<T> = emptyList(),
)

@Serializable
data class OrganizerDto(val id: String, val name: String = "", val slug: String = "")

/** A new category or tag: Mealie gives it its id and slug. */
@Serializable
data class CreateOrganizerDto(val name: String)

@Serializable
data class RecipeSummaryDto(
    val id: String,
    val slug: String,
    val userId: String? = null,
    val orgURL: String? = null,
    val name: String = "",
    val description: String? = null,
    val image: String? = null,
    val recipeServings: Double? = null,
    val recipeYield: String? = null,
    val totalTime: String? = null,
    val prepTime: String? = null,
    val performTime: String? = null,
    val cookTime: String? = null,
    val totalTimeSeconds: Int? = null,
    val prepTimeSeconds: Int? = null,
    val performTimeSeconds: Int? = null,
    val rating: Double? = null,
    val recipeCategory: List<OrganizerDto> = emptyList(),
    val tags: List<OrganizerDto> = emptyList(),
    /** Kept with the offline copy, like the two times below, so the copy filters and sorts as Mealie does. */
    val tools: List<OrganizerDto> = emptyList(),
    val dateAdded: String? = null,
    val createdAt: String? = null,
    val lastMade: String? = null,
    /** When the recipe last changed, to refresh offline copies only when needed. */
    val updatedAt: String? = null,
)

@Serializable
data class RecipeDto(
    val id: String,
    val slug: String,
    val name: String = "",
    val description: String? = null,
    val image: String? = null,
    val recipeServings: Double? = null,
    val recipeYield: String? = null,
    val totalTime: String? = null,
    val prepTime: String? = null,
    val performTime: String? = null,
    val cookTime: String? = null,
    val totalTimeSeconds: Int? = null,
    val prepTimeSeconds: Int? = null,
    val performTimeSeconds: Int? = null,
    val rating: Double? = null,
    val recipeCategory: List<OrganizerDto> = emptyList(),
    val tags: List<OrganizerDto> = emptyList(),
    val tools: List<OrganizerDto> = emptyList(),
    val orgURL: String? = null,
    /** When the recipe last changed, to refresh offline copies only when needed. */
    val updatedAt: String? = null,
    val recipeIngredient: List<IngredientDto> = emptyList(),
    val recipeInstructions: List<StepDto>? = null,
    val notes: List<NoteDto>? = null,
    val nutrition: NutritionDto? = null,
    val settings: RecipeSettingsDto? = null,
)

@Serializable
data class IngredientDto(
    val quantity: Double? = null,
    val unit: UnitDto? = null,
    val food: FoodDto? = null,
    val note: String? = null,
    val display: String? = null,
    val title: String? = null,
    val originalText: String? = null,
    val referenceId: String? = null,
    val referencedRecipe: LinkedRecipeDto? = null,
)

@Serializable
data class UnitDto(
    val id: String? = null,
    val name: String = "",
    val pluralName: String? = null,
    val abbreviation: String? = null,
    val pluralAbbreviation: String? = null,
    val useAbbreviation: Boolean = false,
    val fraction: Boolean = true,
    /** Sent by Mealie 3.13 and newer. */
    val standardQuantity: Double? = null,
    val standardUnit: String? = null,
)

@Serializable
data class FoodDto(
    val id: String? = null,
    val name: String = "",
    val pluralName: String? = null,
    val label: LabelDto? = null,
)

@Serializable
data class LinkedRecipeDto(val slug: String? = null, val name: String? = null)

@Serializable
data class StepDto(
    val id: String? = null,
    val title: String? = null,
    val text: String? = null,
    val ingredientReferences: List<IngredientReferenceDto> = emptyList(),
    val durationSeconds: Int? = null,
)

@Serializable
data class IngredientReferenceDto(val referenceId: String? = null)

@Serializable
data class NoteDto(val title: String? = null, val text: String? = null)

@Serializable
data class NutritionDto(
    val calories: String? = null,
    val proteinContent: String? = null,
    val fatContent: String? = null,
    val saturatedFatContent: String? = null,
    val carbohydrateContent: String? = null,
    val sugarContent: String? = null,
    val fiberContent: String? = null,
    val sodiumContent: String? = null,
    val cholesterolContent: String? = null,
)

@Serializable
data class RecipeSettingsDto(val showNutrition: Boolean = false)

@Serializable
data class RatingsDto(val ratings: List<RatingDto> = emptyList())

@Serializable
data class RatingDto(val recipeId: String, val rating: Double? = null, val isFavorite: Boolean = false)

@Serializable
data class RatingUpdateDto(val rating: Double? = null, val isFavorite: Boolean? = null)

@Serializable
data class ImportRecipeDto(val url: String, val includeTags: Boolean)

/** Only what Aroma needs from the group: whether an AI provider is set up, and one for images (Mealie 3 and newer). */
@Serializable
data class GroupDto(val aiProviderSettings: AiProviderSettingsDto? = null)

@Serializable
data class AiProviderSettingsDto(val aiEnabled: Boolean = false, val imageProviderEnabled: Boolean = false)

@Serializable
data class CreateRecipeDto(val name: String)

@Serializable
data class LastMadeDto(val timestamp: String)

@Serializable
data class CreateTimelineEventDto(
    val recipeId: String,
    val subject: String,
    val eventType: String,
    val timestamp: String,
)

@Serializable
data class CookbookDto(
    val id: String,
    val name: String = "",
    val slug: String? = null,
    val description: String? = null,
    val position: Int = 0,
)

@Serializable
data class MealPlanEntryDto(
    val id: Int,
    val date: String,
    val entryType: String? = null,
    val title: String? = null,
    val text: String? = null,
    val recipeId: String? = null,
    val recipe: RecipeSummaryDto? = null,
)

@Serializable
data class CreateMealPlanDto(
    val date: String,
    val entryType: String,
    val title: String,
    val text: String,
    val recipeId: String?,
)

@Serializable
data class ShoppingListSummaryDto(val id: String, val name: String? = null)

@Serializable
data class LabelDto(val id: String, val name: String = "", val color: String? = null)

@Serializable
data class LabelSettingDto(val labelId: String? = null, val position: Int = 0, val label: LabelDto? = null)

@Serializable
data class ShoppingListDto(
    val id: String,
    val name: String? = null,
    val listItems: List<ShoppingItemDto> = emptyList(),
    val labelSettings: List<LabelSettingDto> = emptyList(),
    val recipeReferences: List<ListRecipeReferenceDto> = emptyList(),
)

@Serializable
data class ShoppingItemDto(
    val id: String,
    val quantity: Double = 0.0,
    val unit: UnitDto? = null,
    val food: FoodDto? = null,
    val note: String? = null,
    val checked: Boolean = false,
    val position: Int = 0,
    val label: LabelDto? = null,
    val recipeReferences: List<ItemRecipeReferenceDto> = emptyList(),
)

@Serializable
data class ItemRecipeReferenceDto(val recipeId: String? = null)

@Serializable
data class ListRecipeReferenceDto(val recipeId: String, val recipe: RecipeNameDto? = null)

@Serializable
data class RecipeNameDto(val name: String? = null)

@Serializable
data class CreateShoppingListDto(val name: String)

@Serializable
data class AddRecipeToListDto(val recipeIncrementQuantity: Double, val recipeIngredients: List<JsonObject>?)

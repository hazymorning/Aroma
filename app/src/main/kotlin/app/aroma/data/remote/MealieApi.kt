package app.aroma.data.remote

import app.aroma.data.remote.dto.AboutDto
import app.aroma.data.remote.dto.AddRecipeToListDto
import app.aroma.data.remote.dto.ApiTokenDto
import app.aroma.data.remote.dto.ApiTokenRequest
import app.aroma.data.remote.dto.CookbookDto
import app.aroma.data.remote.dto.CreateMealPlanDto
import app.aroma.data.remote.dto.CreateOrganizerDto
import app.aroma.data.remote.dto.CreateRecipeDto
import app.aroma.data.remote.dto.CreateShoppingListDto
import app.aroma.data.remote.dto.CreateTimelineEventDto
import app.aroma.data.remote.dto.GroupDto
import app.aroma.data.remote.dto.ImportRecipeDto
import app.aroma.data.remote.dto.LastMadeDto
import app.aroma.data.remote.dto.MealPlanEntryDto
import app.aroma.data.remote.dto.OrganizerDto
import app.aroma.data.remote.dto.PageDto
import app.aroma.data.remote.dto.RatingUpdateDto
import app.aroma.data.remote.dto.RatingsDto
import app.aroma.data.remote.dto.RecipeDto
import app.aroma.data.remote.dto.RecipeSummaryDto
import app.aroma.data.remote.dto.ShoppingListSummaryDto
import app.aroma.data.remote.dto.TokenDto
import app.aroma.data.remote.dto.UserDto
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** The parts of the Mealie API Aroma uses. Paths are relative so servers below a sub path work. */
interface MealieApi {
    @GET("api/app/about")
    suspend fun about(): AboutDto

    @FormUrlEncoded
    @POST("api/auth/token")
    suspend fun signIn(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("remember_me") rememberMe: Boolean = true,
    ): TokenDto

    @GET("api/users/self")
    suspend fun currentUser(
        @Header("Authorization") authorization: String? = null,
    ): UserDto

    @POST("api/users/api-tokens")
    suspend fun createApiToken(
        @Body body: ApiTokenRequest,
        @Header("Authorization") authorization: String,
    ): ApiTokenDto

    @DELETE("api/users/api-tokens/{id}")
    suspend fun deleteApiToken(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
    )

    @GET("api/recipes")
    suspend fun recipes(
        @Query("page") page: Int,
        @Query("perPage") perPage: Int,
        @Query("orderBy") orderBy: String? = null,
        @Query("orderDirection") orderDirection: String? = null,
        @Query("search") search: String? = null,
        @Query("categories") categories: List<String>? = null,
        @Query("tags") tags: List<String>? = null,
        @Query("tools") tools: List<String>? = null,
        @Query("cookbook") cookbook: String? = null,
        @Query("queryFilter") queryFilter: String? = null,
        @Query("paginationSeed") paginationSeed: String? = null,
    ): PageDto<RecipeSummaryDto>

    @GET("api/recipes/{slug}")
    suspend fun recipe(
        @Path("slug") slug: String,
    ): RecipeDto

    @GET("api/recipes/{slug}")
    suspend fun recipeJson(
        @Path("slug") slug: String,
    ): JsonObject

    @POST("api/recipes")
    suspend fun createRecipe(
        @Body body: CreateRecipeDto,
    ): String

    @PATCH("api/recipes/{slug}")
    suspend fun patchRecipe(
        @Path("slug") slug: String,
        @Body body: JsonObject,
    ): RecipeDto

    @DELETE("api/recipes/{slug}")
    suspend fun deleteRecipe(
        @Path("slug") slug: String,
    )

    @POST("api/recipes/create/url")
    suspend fun importRecipe(
        @Body body: ImportRecipeDto,
    ): String

    /** Same as [importRecipe], reporting each step as a server-sent event (Mealie 3.13 and newer). */
    @Streaming
    @POST("api/recipes/create/url/stream")
    suspend fun importRecipeStream(
        @Body body: ImportRecipeDto,
    ): ResponseBody

    /** Builds a recipe from a link, text or both with the group's AI provider (Mealie 3.23 and newer). */
    @Multipart
    @POST("api/recipes/create/ai")
    suspend fun importRecipeWithAi(
        @Part parts: List<MultipartBody.Part>,
    ): String

    @Multipart
    @Streaming
    @POST("api/recipes/create/ai/stream")
    suspend fun importRecipeWithAiStream(
        @Part parts: List<MultipartBody.Part>,
    ): ResponseBody

    @GET("api/groups/self")
    suspend fun group(): GroupDto

    /** A recipe's photo in the format Mealie keeps it in. */
    @Streaming
    @GET("api/media/recipes/{id}/images/original.webp")
    suspend fun recipePhoto(
        @Path("id") id: String,
    ): ResponseBody

    @Multipart
    @PUT("api/recipes/{slug}/image")
    suspend fun uploadImage(
        @Path("slug") slug: String,
        @Part image: MultipartBody.Part,
        @Part extension: MultipartBody.Part,
    )

    @PATCH("api/recipes/{slug}/last-made")
    suspend fun markMade(
        @Path("slug") slug: String,
        @Body body: LastMadeDto,
    )

    @POST("api/recipes/timeline/events")
    suspend fun createTimelineEvent(
        @Body body: CreateTimelineEventDto,
    )

    @GET("api/users/self/ratings")
    suspend fun ratings(): RatingsDto

    @POST("api/users/{userId}/ratings/{slug}")
    suspend fun setRating(
        @Path("userId") userId: String,
        @Path("slug") slug: String,
        @Body body: RatingUpdateDto,
    )

    @GET("api/organizers/categories")
    suspend fun categories(
        @Query("perPage") perPage: Int = -1,
    ): PageDto<OrganizerDto>

    @POST("api/organizers/categories")
    suspend fun createCategory(
        @Body body: CreateOrganizerDto,
    ): OrganizerDto

    @GET("api/organizers/tags")
    suspend fun tags(
        @Query("perPage") perPage: Int = -1,
    ): PageDto<OrganizerDto>

    @POST("api/organizers/tags")
    suspend fun createTag(
        @Body body: CreateOrganizerDto,
    ): OrganizerDto

    @GET("api/organizers/tools")
    suspend fun tools(
        @Query("perPage") perPage: Int = -1,
    ): PageDto<OrganizerDto>

    @GET("api/households/cookbooks")
    suspend fun cookbooks(
        @Query("perPage") perPage: Int = -1,
    ): PageDto<CookbookDto>

    @GET("api/households/mealplans")
    suspend fun mealPlans(
        @Query("start_date") start: String,
        @Query("end_date") end: String,
        @Query("perPage") perPage: Int = -1,
        @Query("orderBy") orderBy: String = "date",
        @Query("orderDirection") orderDirection: String = "asc",
    ): PageDto<MealPlanEntryDto>

    @POST("api/households/mealplans")
    suspend fun createMealPlanEntry(
        @Body body: CreateMealPlanDto,
    ): MealPlanEntryDto

    @DELETE("api/households/mealplans/{id}")
    suspend fun deleteMealPlanEntry(
        @Path("id") id: Int,
    )

    @GET("api/households/mealplans/{id}")
    suspend fun mealPlanEntryJson(
        @Path("id") id: Int,
    ): JsonObject

    @PUT("api/households/mealplans/{id}")
    suspend fun updateMealPlanEntry(
        @Path("id") id: Int,
        @Body body: JsonObject,
    )

    @GET("api/households/shopping/lists")
    suspend fun shoppingLists(
        @Query("perPage") perPage: Int = -1,
        @Query("orderBy") orderBy: String = "created_at",
        @Query("orderDirection") orderDirection: String = "asc",
    ): PageDto<ShoppingListSummaryDto>

    @GET("api/households/shopping/lists/{id}")
    suspend fun shoppingList(
        @Path("id") id: String,
    ): JsonObject

    @POST("api/households/shopping/lists")
    suspend fun createShoppingList(
        @Body body: CreateShoppingListDto,
    ): ShoppingListSummaryDto

    @PUT("api/households/shopping/lists/{id}")
    suspend fun updateShoppingList(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): JsonObject

    @DELETE("api/households/shopping/lists/{id}")
    suspend fun deleteShoppingList(
        @Path("id") id: String,
    )

    /** Single-recipe endpoint: Mealie 2.0 has no bulk variant, newer versions support both. */
    @POST("api/households/shopping/lists/{id}/recipe/{recipeId}")
    suspend fun addRecipeToList(
        @Path("id") listId: String,
        @Path("recipeId") recipeId: String,
        @Body body: AddRecipeToListDto,
    ): JsonObject

    @POST("api/households/shopping/items")
    suspend fun createShoppingItem(
        @Body body: JsonObject,
    ): JsonObject

    @PUT("api/households/shopping/items/{id}")
    suspend fun updateShoppingItem(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): JsonObject

    @DELETE("api/households/shopping/items/{id}")
    suspend fun deleteShoppingItem(
        @Path("id") id: String,
    )
}

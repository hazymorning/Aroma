package app.aroma.core.model

data class ShoppingListInfo(val id: String, val name: String)

data class ShoppingLabel(val id: String, val name: String)

data class ShoppingItem(
    val id: String,
    val listId: String,
    val quantity: Double,
    val unit: IngredientUnit?,
    val food: IngredientFood?,
    val note: String,
    val checked: Boolean,
    val position: Int,
    val label: ShoppingLabel?,
    val recipeNames: List<String>,
)

data class ShoppingList(
    val id: String,
    val name: String,
    val items: List<ShoppingItem>,
    val labelOrder: List<String>,
)

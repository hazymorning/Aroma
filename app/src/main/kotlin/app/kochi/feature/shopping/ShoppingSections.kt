package app.kochi.feature.shopping

import app.kochi.core.model.ShoppingItem
import app.kochi.core.model.ShoppingLabel
import app.kochi.core.model.ShoppingList

/** Open items grouped by label, in the order chosen in Mealie; unlabeled items come last. */
data class ShoppingSection(val label: ShoppingLabel?, val items: List<ShoppingItem>)

data class ShoppingGroups(val sections: List<ShoppingSection>, val checked: List<ShoppingItem>)

fun ShoppingList.grouped(): ShoppingGroups {
    val open = items.filterNot { it.checked }
    val order = labelOrder.withIndex().associate { (index, id) -> id to index }
    val sections =
        open
            .groupBy { it.label?.id }
            .map { (_, groupItems) -> ShoppingSection(groupItems.first().label, groupItems.sortedBy { it.position }) }
            .sortedWith(
                compareBy<ShoppingSection> { it.label == null }
                    .thenBy { section -> section.label?.let { order[it.id] } ?: Int.MAX_VALUE }
                    .thenBy { it.label?.name?.lowercase() },
            )
    val checked = items.filter { it.checked }.sortedBy { it.position }
    return ShoppingGroups(sections, checked)
}

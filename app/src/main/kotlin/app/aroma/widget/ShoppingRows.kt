package app.aroma.widget

import androidx.compose.ui.unit.DpSize
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.feature.shopping.grouped
import app.aroma.feature.shopping.itemLine
import java.util.Locale

/** A row of the shopping widget's list. */
internal sealed interface ShoppingRow {
    /** Stable across changes to the list, so a tap on a row the launcher still shows reaches that row. */
    val id: Long

    /** An aisle's name: one of Mealie's labels, or none for the items without one. */
    data class Aisle(val label: ShoppingLabel?) : ShoppingRow {
        override val id: Long get() = "aisle:${label?.id}".hashCode().toLong()
    }

    data class Item(val item: ShoppingItem) : ShoppingRow {
        override val id: Long get() = item.id.hashCode().toLong()
    }
}

/**
 * The open items under their aisles, grouped as the app's list groups them; items checked a moment
 * ago ([lingering]) keep their place among them, as they are.
 */
internal fun shoppingRows(list: ShoppingList, lingering: Set<String>): List<ShoppingRow> {
    val items = list.items.associateBy { it.id }
    val shown = list.copy(items = list.items.map { if (it.id in lingering) it.copy(checked = false) else it })
    return shown.grouped().sections.flatMap { section ->
        listOf(ShoppingRow.Aisle(section.label)) + section.items.map { ShoppingRow.Item(items.getValue(it.id)) }
    }
}

/** The list a widget shows: the one chosen for it, else the one open in the app, else the first. */
internal fun pickList(lists: List<ShoppingListInfo>, chosen: String?, openInApp: String?): ShoppingListInfo? =
    lists.firstOrNull { it.id == chosen } ?: lists.firstOrNull { it.id == openInApp } ?: lists.firstOrNull()

/** The item in the app's words for it, in one line: "500 g flour, sifted". */
internal fun ShoppingItem.text(locale: Locale): String {
    val line = itemLine(this, locale)
    val words = listOf(line.measureText(), line.text).filter { it.isNotBlank() }.joinToString(" ")
    return line.note?.let { "$words, $it" } ?: words
}

/** How the shopping widget arranges itself at a size. */
internal enum class ShoppingLayout {
    /** One cell: how many items are left. */
    Count,

    /** One row: the list's name, how many are left, and "+". */
    Bar,

    /** The items to check off under a header. */
    List,

    /** Tall: the items under the names of their aisles. */
    Aisles,
    ;

    companion object {
        /** The sizes laid out for; the launcher shows the closest that fits. */
        val sizes: Set<DpSize> =
            setOf(
                DpSize(WidgetSizes.Smallest, WidgetSizes.Smallest),
                DpSize(WidgetSizes.Narrow, WidgetSizes.Smallest),
                DpSize(WidgetSizes.Narrow, WidgetSizes.Low),
                DpSize(WidgetSizes.Narrow, WidgetSizes.Tall),
            )

        fun at(size: DpSize): ShoppingLayout =
            when {
                size.width < WidgetSizes.Narrow -> Count
                size.height < WidgetSizes.Low -> Bar
                size.height >= WidgetSizes.Tall -> Aisles
                else -> List
            }
    }
}

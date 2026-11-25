package app.aroma.widget

import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ShoppingRowsTest {
    private val produce = ShoppingLabel("produce", "Produce")
    private val dairy = ShoppingLabel("dairy", "Dairy")
    private val bakery = ShoppingLabel("bakery", "Bakery")
    private val household = ShoppingLabel("household", "Household")

    private fun item(id: String, label: ShoppingLabel?, position: Int, checked: Boolean = false) =
        ShoppingItem(id, "list", 0.0, null, null, id, checked, position, label, emptyList())

    private fun list(vararg items: ShoppingItem) =
        ShoppingList("list", "Weekly shop", items.toList(), labelOrder = listOf("produce", "dairy"))

    /** The rows as the widget reads, aisles marked with "#". */
    private fun List<ShoppingRow>.names() =
        joinToString(" ") { row ->
            when (row) {
                is ShoppingRow.Aisle -> "# ${row.label?.name ?: "Other"}"
                is ShoppingRow.Item -> row.item.id
            }
        }

    @Test
    fun `aisles follow the label order set in Mealie, then by name, with unlabelled items last, each in its position`() {
        val rows =
            shoppingRows(
                list(
                    item("towels", null, 0),
                    item("bread", bakery, 1),
                    item("soap", household, 2),
                    item("milk", dairy, 3),
                    item("lemons", produce, 7),
                    item("apples", produce, 4),
                    item("leeks", produce, 5),
                ),
                lingering = emptySet(),
            )

        assertEquals("# Produce apples leeks lemons # Dairy milk # Bakery bread # Household soap # Other towels", rows.names())
    }

    @Test
    fun `a checked item stays where it was while it lingers, then leaves`() {
        val shopping = list(item("apples", produce, 0), item("lemons", produce, 1, checked = true), item("milk", dairy, 2, checked = true))

        assertEquals("# Produce apples lemons", shoppingRows(shopping, lingering = setOf("lemons")).names())
        assertEquals("# Produce apples", shoppingRows(shopping, lingering = emptySet()).names())
    }

    @Test
    fun `a widget shows its own list, else the one open in the app, else the first`() {
        val lists =
            listOf(ShoppingListInfo("weekly", "Weekly shop"), ShoppingListInfo("party", "Party"), ShoppingListInfo("garden", "Garden"))

        assertEquals("garden", pickList(lists, chosen = "garden", openInApp = "party")?.id)
        assertEquals("party", pickList(lists, chosen = "deleted", openInApp = "party")?.id)
        assertEquals("weekly", pickList(lists, chosen = null, openInApp = null)?.id)
        assertNull(pickList(emptyList(), chosen = "garden", openInApp = "party"))
    }

    @Test
    fun `an item reads as in the app's list`() {
        val gram = IngredientUnit("g", "gram", "grams", "g", null, useAbbreviation = true, useFraction = false)
        val flour = IngredientFood("flour", "flour", null)
        val parsed = item("flour", null, 0).copy(quantity = 500.0, unit = gram, food = flour, note = "sifted")

        assertEquals("500 g flour, sifted", parsed.text(Locale.ENGLISH))
        assertEquals("2 lemons", item("2 lemons", null, 0).text(Locale.ENGLISH))
    }
}

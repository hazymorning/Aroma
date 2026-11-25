package app.aroma.feature.shopping

import app.aroma.core.model.ShoppingItem
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestApp::class)
class ShoppingScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie

    @Test
    fun `two quick ticks on an item send nothing`() {
        val list = mealie.addList("Wocheneinkauf", "Milch", "Eier")
        val shopping = open()
        val (milk, eggs) = shopping.items()

        shopping.toggle(milk)
        shopping.toggle(milk)
        // A tick on the eggs goes out after anything the milk would have sent.
        shopping.toggle(eggs)
        screen.advanceBy(SETTLE_MILLIS)
        screen.until { mealie.items(list).named("Eier").checked }

        assertEquals(listOf(eggs.id), mealie.requests.filter { it.method == "PUT" }.map { it.path.substringAfterLast('/') })
        assertFalse(mealie.items(list).named("Milch").checked)
        assertFalse(shopping.state.value.isChecked(shopping.items().first { it.id == milk.id }))
    }

    @Test
    fun `an item ticked while Mealie is out of reach stays ticked and waits to be sent`() {
        val list = mealie.addList("Wocheneinkauf", "Milch")
        val shopping = open()
        screen.loseNetwork()
        val asked = mealie.requests.size

        shopping.toggle(shopping.items().single())
        screen.advanceBy(SETTLE_MILLIS)
        screen.awaitState(shopping.state) { it.pendingChanges == 1 && it.settlingChecks.isEmpty() }
        assertTrue(shopping.items().single().checked)
        assertEquals("Mealie was not asked", asked, mealie.requests.size)

        screen.regainNetwork()
        screen.awaitState(shopping.state) { it.pendingChanges == 0 && !it.isOffline }
        assertTrue(mealie.items(list).single().checked)
        assertTrue(shopping.items().single().checked)
    }

    @Test
    fun `an item deleted and brought back is on the list once, and once on Mealie after it was sent`() {
        val list = mealie.addList("Wocheneinkauf", "Milch", "Eier")
        val shopping = open()
        screen.loseNetwork()
        val messages = screen.collect(shopping.events)

        shopping.delete(shopping.items().first())
        screen.until { messages.isNotEmpty() }
        shopping.undoDelete(messages.single() as ShoppingEvent.Deleted)
        screen.awaitState(shopping.state) { it.pendingChanges == 2 && it.list?.items?.size == 2 }
        assertEquals(listOf("Eier", "Milch"), shopping.items().map { it.note }.sorted())

        screen.regainNetwork()
        screen.awaitState(shopping.state) { it.pendingChanges == 0 && !it.isOffline }
        assertEquals(listOf("Eier", "Milch"), mealie.items(list).map { it.note }.sorted())
        assertEquals(listOf("Eier", "Milch"), shopping.items().map { it.note }.sorted())
    }

    @Test
    fun `deleting an item says so even to a page that starts listening only afterwards`() {
        val list = mealie.addList("Wocheneinkauf", "Milch", "Eier")
        val shopping = open()
        val milk = shopping.items().first()

        shopping.delete(milk)
        screen.until { mealie.items(list).size == 1 }
        val messages = screen.collect(shopping.events)
        screen.until { messages.isNotEmpty() }

        val deleted = messages.single() as ShoppingEvent.Deleted
        assertEquals(list to milk.id, deleted.listId to deleted.item.id)
    }

    private fun open(): ShoppingViewModel {
        val shopping = screen.shopping()
        screen.awaitState(shopping.state) { it.list?.items?.isNotEmpty() == true }
        return shopping
    }

    private fun ShoppingViewModel.items(): List<ShoppingItem> = state.value.list?.items ?: emptyList()

    private fun List<JsonObject>.named(note: String) = single { it.note == note }

    private val JsonObject.id get() = getValue("id").jsonPrimitive.content

    private val JsonObject.note get() = getValue("note").jsonPrimitive.content

    private val JsonObject.checked get() = getValue("checked").jsonPrimitive.boolean

    private companion object {
        /** How long a ticked row stays in place before it moves, and its change goes out. */
        const val SETTLE_MILLIS = 450L
    }
}

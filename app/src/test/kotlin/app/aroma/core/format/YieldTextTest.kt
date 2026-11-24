package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YieldTextTest {
    @Test
    fun `a count and what it counts`() {
        assertEquals(YieldText("", 1.0, null, "Blech"), YieldText.parse("1 Blech"))
        assertEquals(YieldText("", 4.0, null, "Portionen"), YieldText.parse(" 4 Portionen "))
    }

    @Test
    fun `what stands before the count stays`() {
        assertEquals(YieldText("ca.", 24.0, null, "Stück"), YieldText.parse("ca. 24 Stück"))
    }

    @Test
    fun `ranges and decimal commas`() {
        assertEquals(YieldText("", 4.0, 6.0, "Portionen"), YieldText.parse("4–6 Portionen"))
        assertEquals(YieldText("", 1.5, null, "l"), YieldText.parse("1,5 l"))
    }

    @Test
    fun `what it counts alone, where Mealie keeps the count apart`() {
        assertEquals(YieldText("", null, null, "Stück"), YieldText.parse("Stück"))
    }

    @Test
    fun `nothing for nothing`() {
        assertNull(YieldText.parse(null))
        assertNull(YieldText.parse("  "))
    }
}

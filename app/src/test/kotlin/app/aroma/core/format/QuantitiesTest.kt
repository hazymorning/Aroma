package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class QuantitiesTest {
    private val german = Locale.GERMANY
    private val english = Locale.US

    private fun plain(value: Double, fraction: Boolean = true, locale: Locale = german) =
        Quantities.display(value, fraction, locale)?.toPlainText()

    @Test
    fun `whole numbers stay whole`() {
        assertEquals("2", plain(2.0))
        assertEquals("200", plain(200.0))
    }

    @Test
    fun `common fractions become vulgar fractions`() {
        assertEquals("½", plain(0.5))
        assertEquals("2 ½", plain(2.5))
        assertEquals("⅓", plain(1.0 / 3))
        assertEquals("1 ⅔", plain(5.0 / 3))
        assertEquals("⅛", plain(0.125))
    }

    @Test
    fun `fraction keeps numerator and denominator for rich rendering`() {
        assertEquals(DisplayQuantity.Fraction(2, 2, 3), Quantities.display(8.0 / 3, true, german))
    }

    @Test
    fun `values close to a whole number snap to it`() {
        assertEquals("3", plain(2.99))
        assertEquals("3", plain(3.01))
    }

    @Test
    fun `large amounts are rounded instead of showing odd fractions`() {
        assertEquals("133", plain(400.0 / 3))
        assertEquals("12 ½", plain(12.5))
        assertEquals("13", plain(12.8))
    }

    @Test
    fun `decimals respect the locale when fractions are disabled`() {
        assertEquals("1,5", plain(1.5, fraction = false))
        assertEquals("1.5", plain(1.5, fraction = false, locale = english))
        assertEquals("0,33", plain(1.0 / 3, fraction = false))
        assertEquals("12,5", plain(12.5, fraction = false))
    }

    @Test
    fun `unusual remainders fall back to decimals`() {
        assertEquals("1,45", plain(1.45))
    }

    @Test
    fun `zero and invalid values produce nothing`() {
        assertNull(Quantities.display(0.0, true, german))
        assertNull(Quantities.display(-1.0, true, german))
        assertNull(Quantities.display(Double.NaN, true, german))
    }
}

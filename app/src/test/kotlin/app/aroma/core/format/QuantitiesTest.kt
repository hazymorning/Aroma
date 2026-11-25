package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class QuantitiesTest {
    private val german = Locale.GERMANY

    private fun plain(value: Double, fraction: Boolean = true, locale: Locale = german) =
        Quantities.display(value, fraction, locale)?.toPlainText()

    @Test
    fun `amounts read as a cook writes them`() =
        listOf(
            2.0 to "2",
            200.0 to "200",
            0.5 to "½",
            2.5 to "2 ½",
            1.0 / 3 to "⅓",
            5.0 / 3 to "1 ⅔",
            0.125 to "⅛",
            // Close to a whole number is that number.
            2.99 to "3",
            3.01 to "3",
            // Large amounts are rounded instead of showing odd fractions.
            400.0 / 3 to "133",
            12.5 to "12 ½",
            12.8 to "13",
            // A remainder no common fraction fits stays a decimal, and a pinch scaled down is still there.
            1.45 to "1,45",
            0.02 to "0,02",
        ).forEach { (value, shown) -> assertEquals("$value", shown, plain(value)) }

    @Test
    fun `decimals follow the locale where fractions are off`() {
        assertEquals("1,5", plain(1.5, fraction = false))
        assertEquals("1.5", plain(1.5, fraction = false, locale = Locale.US))
        assertEquals("0,33", plain(1.0 / 3, fraction = false))
        assertEquals("12,5", plain(12.5, fraction = false))
        assertEquals("0,01", plain(0.004, fraction = false))
    }

    @Test
    fun `no amount or an invalid one shows nothing`() =
        listOf(0.0, -1.0, Double.NaN).forEach { assertNull("$it", Quantities.display(it, true, german)) }
}

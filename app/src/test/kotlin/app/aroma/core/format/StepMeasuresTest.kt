package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StepMeasuresTest {
    private fun metric(text: String, locale: Locale = Locale.US) = StepMeasures.convert(text, MeasureSystem.Metric, locale)

    private fun us(text: String, locale: Locale = Locale.GERMANY) = StepMeasures.convert(text, MeasureSystem.US, locale)

    @Test
    fun `oven temperatures round to 5 °C`() =
        listOf(
            "Preheat the oven to 350°F." to "Preheat the oven to 175 °C.",
            "Bake at 375 °F for 20 minutes." to "Bake at 190 °C for 20 minutes.",
            "Roast at 425° F until golden." to "Roast at 220 °C until golden.",
            "Bake at 400F." to "Bake at 205 °C.",
            "Bake at 325 F." to "Bake at 165 °C.",
            "Heat the oven to 450 degrees F." to "Heat the oven to 230 °C.",
            "Heat the oven to 300 degrees Fahrenheit." to "Heat the oven to 150 °C.",
            "Bake at 350℉." to "Bake at 175 °C.",
            "Bake at 350º F." to "Bake at 175 °C.",
            "Heat the oil to 350–375 °F." to "Heat the oil to 175–190 °C.",
            "Heat the oil to 350 to 375°F." to "Heat the oil to 175–190 °C.",
        ).forEach { (written, converted) -> assertEquals(converted, metric(written)) }

    @Test
    fun `oven temperatures round to 25 °F`() =
        listOf(
            "Den Backofen auf 180 °C Ober-/Unterhitze vorheizen." to "Den Backofen auf 350 °F Ober-/Unterhitze vorheizen.",
            "Bei 200 Grad 20 Minuten backen." to "Bei 400 °F 20 Minuten backen.",
            "Bei 160 Grad Umluft backen." to "Bei 325 °F Umluft backen.",
            "Bei 220 Grad Celsius backen." to "Bei 425 °F backen.",
            "Auf 170°C stellen." to "Auf 350 °F stellen.",
            "Bake at 180C/160C fan/gas 4." to "Bake at 350 °F/325 °F fan/gas 4.",
            "Bake at 200 °C (180 °C fan)." to "Bake at 400 °F (350 °F fan).",
            "Heat to 180–190 ℃." to "Heat to 350–375 °F.",
            "Auf 180°C-200°C erhitzen." to "Auf 350 °F-400 °F erhitzen.",
        ).forEach { (written, converted) -> assertEquals(converted, us(written)) }

    @Test
    fun `turning by degrees is an angle, not a temperature`() =
        listOf(
            "Das Blech nach 10 Minuten um 180 Grad drehen.",
            "Den Teig um 90 Grad drehen und erneut falten.",
            "Die Form 180 Grad wenden.",
        ).forEach { assertEquals(it, us(it)) }

    @Test
    fun `a temperature stays one when the dish is turned later`() =
        assertEquals(
            "Bei 400 °F unter gelegentlichem Wenden 40 Minuten garen.",
            us("Bei 200 Grad unter gelegentlichem Wenden 40 Minuten garen."),
        )

    @Test
    fun `temperatures below an oven's keep single degrees`() {
        assertEquals("Cook to 63 °C.", metric("Cook to 145°F."))
        assertEquals("Cook until it reads 74 °C.", metric("Cook until it reads 165 °F."))
        assertEquals("Boil to 113 °C (soft ball).", metric("Boil to 235 °F (soft ball)."))
        assertEquals("Freeze at -18 °C.", metric("Freeze at 0 °F."))
        assertEquals("Kerntemperatur 145 °F", us("Kerntemperatur 63 °C"))
        assertEquals("Bei 145 °F garen.", us("Bei 62,8 °C garen."))
        assertEquals("Bei 212 °F trocknen.", us("Bei 100 Grad trocknen."))
    }

    @Test
    fun `steps that give both systems stay as written`() =
        listOf(
            "Preheat the oven to 180 °C / 350 °F.",
            "Preheat oven to 350 degrees F (175 degrees C).",
            "Bake at 180°C/350°F/gas mark 4.",
            "Bake at 220 °C (425 °F), then lower to 180 °C.",
            "Use a 23 cm (9 inch) springform.",
        ).forEach {
            assertEquals(it, metric(it))
            assertEquals(it, us(it))
        }

    @Test
    fun `uncertain matches stay as written`() =
        listOf(
            "Preheat the oven to 350 degrees.",
            "Preheat the oven to 350°.",
            "Den Ofen auf 180° vorheizen.",
            "Add 2 c flour and 1 C sugar.",
            "Bake at gas mark 4.",
            "Freeze at -18 °C.",
            "Bake 25-30 minutes.",
            "See https://example.com/bake-at-350f for the photos.",
            "Pour into a 9x13 pan.",
        ).forEach {
            assertEquals(it, metric(it))
            assertEquals(it, us(it))
        }

    @Test
    fun `pan sizes in inches become the standard metric pans`() =
        listOf(
            "Grease the 8-inch square pan." to "Grease the 20 cm square pan.",
            "Grease a 9-inch springform pan." to "Grease a 23 cm springform pan.",
            "Pour into a 9 x 13-inch baking dish." to "Pour into a 23 x 33 cm baking dish.",
            "Use a 9x5 inch loaf pan." to "Use a 23x13 cm loaf pan.",
            "Use a 9- by 13-inch pan." to "Use a 23- by 33 cm pan.",
            "Use a 10-in. skillet." to "Use a 25 cm skillet.",
            "Use a 9″ pie plate." to "Use a 23 cm pie plate.",
        ).forEach { (written, converted) -> assertEquals(converted, metric(written)) }

    @Test
    fun `metric pans become the nearest inch pan`() =
        listOf(
            "Eine 26 cm Springform fetten." to "Eine 10 inch Springform fetten.",
            "Eine 23 cm Springform fetten." to "Eine 9 inch Springform fetten.",
            "In eine Form (20 x 30 cm) geben." to "In eine Form (8 x 12 inch) geben.",
            "In eine 23 x 33 cm Form geben." to "In eine 9 x 13 inch Form geben.",
            "Eine Kastenform von 23 x 13 cm." to "Eine Kastenform von 9 x 5 inch.",
        ).forEach { (written, converted) -> assertEquals(converted, us(written)) }

    @Test
    fun `small lengths keep a size cooks can cut`() {
        assertEquals("Cut into 2.5 cm cubes.", metric("Cut into 1-inch cubes."))
        assertEquals("In 2,5 cm große Würfel schneiden.", metric("In 1 inch große Würfel schneiden.", Locale.GERMANY))
        assertEquals("Cut into 1.5 cm pieces.", metric("Cut into 1/2-inch pieces."))
        assertEquals("Roll out 6 mm thick.", metric("Roll out 1/4 inch thick."))
        assertEquals("Slice 3 mm thin.", metric("Slice ⅛-inch thin."))
        assertEquals("Cut into 2.5–5 cm pieces.", metric("Cut into 1–2 inch pieces."))
        assertEquals("In ¾ inch große Würfel schneiden.", us("In 2 cm große Würfel schneiden."))
        assertEquals("½ inch dicke Scheiben", us("1 cm dicke Scheiben"))
        assertEquals("¼ inch dicke Scheiben", us("5 mm dicke Scheiben"))
        assertEquals("In ¾-1 ⅛ inch Stücke schneiden.", us("In 2-3 cm Stücke schneiden."))
    }

    @Test
    fun `temperatures and lengths convert in one step`() =
        assertEquals("Cut into 2.5 cm cubes and roast at 220 °C.", metric("Cut into 1-inch cubes and roast at 425°F."))

    @Test
    fun `text in the target system or as written stays`() {
        assertEquals("Bake at 180 °C in a 23 cm pan.", metric("Bake at 180 °C in a 23 cm pan."))
        assertEquals("Bake at 350 °F in a 9-inch pan.", us("Bake at 350 °F in a 9-inch pan."))
        assertEquals("Bake at 350 °F.", StepMeasures.convert("Bake at 350 °F.", MeasureSystem.AsWritten, Locale.US))
    }
}

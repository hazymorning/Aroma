package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PictographsTest {
    private fun assertLeaves(cases: List<Pair<String, String>>) =
        cases.forEach { (text, left) -> assertEquals(text, left, text.withoutPictographs()) }

    @Test
    fun `an emoji before an ingredient goes with its space`() =
        assertLeaves(listOf("🌾 500 g Mehl" to "500 g Mehl", "  ▪️ ⏲️ 500 g Mehl" to "500 g Mehl"))

    @Test
    fun `emoji built of several code points go whole`() =
        assertLeaves(
            listOf(
                "👩‍🍳 Lena kocht" to "Lena kocht",
                "Daumen 👍🏽 hoch" to "Daumen hoch",
                "Pasta 🇮🇹 aus Italien" to "Pasta aus Italien",
                "🏴󠁧󠁢󠁳󠁣󠁴󠁿 Shortbread" to "Shortbread",
                "Mit Liebe ❤︎" to "Mit Liebe",
            ),
        )

    @Test
    fun `keycaps keep their digit`() =
        assertLeaves(
            listOf(
                "1️⃣ Gnocchi kochen" to "1 Gnocchi kochen",
                "1️⃣5️⃣ Minuten backen" to "15 Minuten backen",
                "Schritt 2️⃣: Sauce" to "Schritt 2: Sauce",
            ),
        )

    @Test
    fun `emoji within the text leave one space, none before punctuation, and lines keep their breaks`() =
        assertLeaves(
            listOf(
                "Tomaten-Gnocchi 🍅 in nur 15 Minuten" to "Tomaten-Gnocchi in nur 15 Minuten",
                "Tomaten-Gnocchi 🍅!" to "Tomaten-Gnocchi!",
                "Gnocchi (🌱 vegan)" to "Gnocchi (vegan)",
                // Without a space beside it, an emoji leaves none, as in scripts written without spaces.
                "美味しい🍜ラーメン" to "美味しいラーメン",
                "Zutaten 👇:\n✨✨✨\n🌾 500 g Mehl 🌾\r\n🧈 300 g Butter" to "Zutaten:\n\n500 g Mehl\r\n300 g Butter",
            ),
        )

    @Test
    fun `text without emoji stays as it is`() =
        assertLeaves(
            listOf(
                "1 ½ TL Salz",
                "Bei 180 °C backen",
                "2–3 Eier",
                "• 200 g Zucker",
                "- 1 Prise Salz",
                "Eine Form von 20 × 30 cm",
                "„Omas“ Apfelkuchen mit ‚Streuseln‘ und “Sahne”",
                "Борщ со сметаной",
                "حمص بالطحينة",
                "麻婆豆腐",
                // Devanagari joins letters with the zero width joiner too.
                "क्‍ष",
                "500 g  Mehl,\n\tgesiebt ",
            ).map { it to it },
        )
}

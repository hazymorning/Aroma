package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PictographsTest {
    @Test
    fun `an emoji before an ingredient goes with its space`() {
        assertEquals("500 g Mehl", "🌾 500 g Mehl".withoutPictographs())
        assertEquals("300 g weiche Butter", "🧈 300 g weiche Butter".withoutPictographs())
        assertEquals("150 g Zucker", "🤍 150 g Zucker".withoutPictographs())
        assertEquals("4 Eigelb", "🥚 4 Eigelb".withoutPictographs())
        assertEquals("2 TL Vanilleextrakt", "🌼 2 TL Vanilleextrakt".withoutPictographs())
        assertEquals("1 Prise Salz", "🧂 1 Prise Salz".withoutPictographs())
        assertEquals("350 g Lemon Curd", "🍋 350 g Lemon Curd".withoutPictographs())
        assertEquals("500 g Mehl", "  ▪️ ⏲️ 500 g Mehl".withoutPictographs())
    }

    @Test
    fun `emoji built of several code points go whole`() {
        // Joined by zero width joiners, with a skin tone, and both.
        assertEquals("Lena kocht", "👩‍🍳 Lena kocht".withoutPictographs())
        assertEquals("Daumen hoch", "Daumen 👍🏽 hoch".withoutPictographs())
        assertEquals("Lena kocht", "Lena 🧑🏽‍🍳 kocht".withoutPictographs())
        assertEquals("Scharf!", "Scharf ❤️‍🔥!".withoutPictographs())
        // Flags of two letters, and Scotland's of a black flag and tags.
        assertEquals("Pasta aus Italien", "Pasta 🇮🇹 aus Italien".withoutPictographs())
        assertEquals("Rezept aus Deutschland", "Rezept aus Deutschland 🇩🇪".withoutPictographs())
        assertEquals("Shortbread", "🏴󠁧󠁢󠁳󠁣󠁴󠁿 Shortbread".withoutPictographs())
        // With the emoji presentation selector and with the text one.
        assertEquals("Mit Liebe", "Mit Liebe ❤️".withoutPictographs())
        assertEquals("Mit Liebe", "Mit Liebe ❤\uFE0E".withoutPictographs())
    }

    @Test
    fun `keycaps keep their digit`() {
        assertEquals("1 Gnocchi kochen", "1️⃣ Gnocchi kochen".withoutPictographs())
        assertEquals("15 Minuten backen", "1️⃣5️⃣ Minuten backen".withoutPictographs())
        assertEquals("Schritt 2: Sauce", "Schritt 2️⃣: Sauce".withoutPictographs())
    }

    @Test
    fun `emoji within or after the text leave one space and none before punctuation`() {
        assertEquals("Tomaten-Gnocchi in nur 15 Minuten", "Tomaten-Gnocchi 🍅 in nur 15 Minuten".withoutPictographs())
        assertEquals("Tomaten-Gnocchi!", "Tomaten-Gnocchi 🍅!".withoutPictographs())
        assertEquals("Perfekt für den Feierabend", "Perfekt für den Feierabend 🙌 🙌 ".withoutPictographs())
        assertEquals("Salz, Pfeffer und Chili", "Salz 🧂, Pfeffer und Chili🌶️".withoutPictographs())
        assertEquals("Gnocchi (vegan)", "Gnocchi (🌱 vegan)".withoutPictographs())
        // Without a space beside it, an emoji leaves none, as in scripts written without spaces.
        assertEquals("美味しいラーメン", "美味しい🍜ラーメン".withoutPictographs())
    }

    @Test
    fun `lines keep their breaks and a line of only emoji becomes empty`() {
        assertEquals("", "🍋 🍋🍋".withoutPictographs())
        assertEquals("", "👩‍🍳".withoutPictographs())
        assertEquals("Zutaten:\n\n500 g Mehl\r\n300 g Butter", "Zutaten 👇:\n✨✨✨\n🌾 500 g Mehl 🌾\r\n🧈 300 g Butter".withoutPictographs())
    }

    @Test
    fun `text without emoji stays as it is`() {
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
            "क्\u200Dष",
            "500 g  Mehl,\n\tgesiebt ",
        ).forEach { assertEquals(it, it.withoutPictographs()) }
    }
}

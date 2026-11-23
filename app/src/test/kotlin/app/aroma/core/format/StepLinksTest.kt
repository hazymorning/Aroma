package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class StepLinksTest {
    @Test
    fun `each step of an English recipe uses what it names`() {
        val ingredients =
            listOf(
                "2 medium eggplants, sliced into rounds",
                "250–300 g soft white cheese (ricotta, cottage cheese, or feta)",
                "4–5 sun-dried tomatoes, finely chopped",
                "2 garlic cloves, minced",
                "A handful of fresh dill",
                "A handful of fresh cilantro",
                "50 g walnuts, chopped",
                "Olive oil for frying",
                "Salt and black pepper to taste",
                "Chili flakes, for serving",
            )
        val steps =
            listOf(
                "Slice the eggplants into rounds. Salt them lightly and let them sit for 10–15 minutes, then pat dry.",
                "In a bowl, mix the white cheese, chopped sun-dried tomatoes, garlic, some of the herbs, salt, and pepper.",
                "Heat olive oil in a pan and fry the eggplant slices on both sides until golden and soft. Drain on paper towels.",
                "Spoon the cheese filling onto each slice and fold in half.",
                "Press the folded eggplants into a mix of chopped dill, cilantro, and walnuts so they stick.",
                "Arrange on a plate and sprinkle with chili flakes. Serve warm or at room temperature.",
            )
        assertEquals(
            listOf(
                listOf(0, 8),
                // "Some of the herbs" names no ingredient of its own; dill and cilantro come in step 5.
                listOf(1, 2, 3, 8),
                // "Paper" is not "pepper".
                listOf(0, 7),
                listOf(1),
                listOf(0, 4, 5, 6),
                listOf(9),
            ),
            StepLinks.of(ingredients, steps),
        )
    }

    @Test
    fun `German endings and compounds count, describing words do not`() {
        val ingredients =
            listOf(
                "2 Zwiebeln, fein gehackt",
                "3 Zehen Knoblauch",
                "4 Eier",
                "200 g Sahne",
                "250 g Kirschtomaten",
                "1 Bund frische Petersilie",
                "Salz und Pfeffer",
            )
        val steps =
            listOf(
                "Die Zwiebel und die Knoblauchzehen in Öl andünsten.",
                "Die Tomaten halbieren und dazugeben.",
                "Eier mit der Sahne verquirlen, mit Salz und Pfeffer würzen.",
                "Mit frischer Petersilie bestreuen.",
            )
        assertEquals(
            listOf(listOf(0, 1), listOf(4), listOf(2, 3, 6), listOf(5)),
            StepLinks.of(ingredients, steps),
        )
    }

    @Test
    fun `parts of words and describing words make no link`() {
        val ingredients = listOf("1 EL Öl", "1 Ei", "500 g Teig", "2 EL Puderzucker", "100 g Zucker", "1 Prise Salz")
        val steps =
            listOf(
                "Das Wasser zum Kochen bringen und den Teigschaber bereitlegen.",
                "Eis in eine Schale füllen.",
                "Mit Puderzucker bestäuben.",
                "Frisch servieren.",
            )
        assertEquals(listOf(emptyList(), emptyList(), listOf(3), emptyList<Int>()), StepLinks.of(ingredients, steps))
    }

    @Test
    fun `nothing to link links nothing`() {
        assertEquals(emptyList<List<Int>>(), StepLinks.of(listOf("1 Ei"), emptyList()))
        assertEquals(listOf(emptyList<Int>()), StepLinks.of(emptyList(), listOf("Alles verrühren.")))
        assertEquals(listOf(emptyList<Int>()), StepLinks.of(listOf("🥚🥚", ""), listOf("Alles verrühren.")))
    }
}

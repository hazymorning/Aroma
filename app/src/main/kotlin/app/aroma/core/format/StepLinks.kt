package app.aroma.core.format

import java.util.Locale

/**
 * Which ingredients each step of a recipe uses, read from the words that name them. An ingredient
 * is named by its line without the amount, the unit, what follows a comma or stands in brackets,
 * and describing words such as "fresh" or "gehackt"; a step uses it where it names it as a word,
 * whatever its ending ("Zwiebel", "Zwiebeln"), or inside a German compound ("Knoblauchzehe").
 * Where in doubt, a step gets no link rather than a wrong one.
 */
object StepLinks {
    /** For each of [steps], the indexes into [ingredients] it mentions, in ingredient order. */
    fun of(ingredients: List<String>, steps: List<String>): List<List<Int>> {
        val names = ingredients.map(::nameWords)
        val known = names.flatten().map(::stem).toSet()
        return steps.map { step ->
            val words = words(step)
            val stems = words.map(::stem).toSet()
            // A word that names an ingredient exactly is not read as part of another one's compound.
            val loose = words.filter { stem(it) !in known }
            names.indices.filter { index -> names[index].any { name -> stem(name) in stems || loose.any { compound(it, name) } } }
        }
    }

    /** The words that name the food of an ingredient line. */
    private fun nameWords(line: String): List<String> {
        val text = line.withoutPictographs()
        val rest = LeadingQuantityParser.parse(text)?.rest ?: text
        return words(rest.substringBefore(',').substringBefore('('))
            .filter { it.length >= MIN_WORD && it !in IngredientFormatter.KNOWN_UNITS && stem(it) !in Fillers }
    }

    /** A German compound and the food it is built on, either way round: "knoblauchzehe" and "knoblauch", "tomaten" and "kirschtomaten". */
    private fun compound(word: String, name: String): Boolean =
        word != name &&
            minOf(word.length, name.length) >= MIN_COMPOUND &&
            (word.startsWith(name) || word.endsWith(name) || name.endsWith(word))

    private fun words(text: String): List<String> = Word.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()

    /**
     * The word without its plural or inflected endings, at most two of them, so that "eggplants",
     * "Zwiebeln" and "geriebener" meet "eggplant", "Zwiebel" and "gerieben". Both sides of a
     * comparison go through it, so a stem only has to be the same, not a word.
     */
    private fun stem(word: String): String {
        if (word.endsWith("ies") && word.length - 3 >= MIN_STEM) return word.dropLast(3) + "y"
        var stem = word
        repeat(2) {
            val ending = Endings.firstOrNull { stem.endsWith(it) && stem.length - it.length >= MIN_STEM } ?: return stem
            stem = stem.dropLast(ending.length)
        }
        return stem
    }

    private val Word = Regex("""\p{L}+""")

    /** Longest first, so "es" goes before "s". */
    private val Endings = listOf("es", "en", "er", "e", "s", "n")

    private const val MIN_WORD = 3
    private const val MIN_STEM = 3

    /** The least length of both words where one is read as part of the other. */
    private const val MIN_COMPOUND = 5

    /** Words that describe an ingredient rather than name it, in English and German, compared by their stems. */
    private val Fillers =
        listOf(
            "and",
            "or",
            "of",
            "the",
            "for",
            "with",
            "into",
            "about",
            "some",
            "few",
            "plus",
            "more",
            "extra",
            "optional",
            "fresh",
            "freshly",
            "dried",
            "chopped",
            "finely",
            "roughly",
            "sliced",
            "diced",
            "minced",
            "grated",
            "ground",
            "peeled",
            "large",
            "medium",
            "small",
            "whole",
            "soft",
            "hard",
            "cold",
            "warm",
            "hot",
            "room",
            "temperature",
            "taste",
            "serving",
            "frying",
            "piece",
            "round",
            "red",
            "green",
            "yellow",
            "white",
            "black",
            "und",
            "oder",
            "mit",
            "für",
            "zum",
            "zur",
            "vom",
            "etwa",
            "etwas",
            "einige",
            "wenig",
            "nach",
            "geschmack",
            "frisch",
            "gehackt",
            "fein",
            "grob",
            "gerieben",
            "gewürfelt",
            "geschält",
            "gepresst",
            "gemahlen",
            "getrocknet",
            "klein",
            "groß",
            "mittelgroß",
            "weich",
            "kalt",
            "zimmerwarm",
            "rot",
            "grün",
            "gelb",
            "weiß",
            "schwarz",
            "bio",
        ).map(::stem).toSet()
}

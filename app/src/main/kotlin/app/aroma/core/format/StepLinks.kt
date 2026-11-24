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
        return steps.map { step -> mentions(names, step).map { it.ingredient }.distinct().sorted() }
    }

    /** How a word of a step names a word of an ingredient. */
    enum class Match {
        /** The same word, whatever its ending: "Zwiebeln" for "Zwiebel". */
        Same,

        /** The food more broadly, a word the ingredient's ends with: "Spinat" for "Blattspinat". */
        Broader,

        /** A word built on the ingredient's, for a part of it, something made of it or a kind of it: "Knoblauchzehen", "Hefewasser". */
        Built,
    }

    /** A word of a step, at [range], that names the [name]th name word of ingredient [ingredient]. */
    class Mention(
        val ingredient: Int,
        val name: Int,
        val range: IntRange,
        val match: Match,
    )

    /** Every word of [step] that names one of the ingredients whose name words are [names], in the order of the step. */
    fun mentions(names: List<List<String>>, step: String): List<Mention> {
        val known = names.flatten().map(::stem).toSet()
        return Word
            .findAll(step)
            .flatMap { found ->
                val word = found.value.lowercase(Locale.ROOT)
                val stem = stem(word)
                names.flatMapIndexed { ingredient, words ->
                    words.mapIndexedNotNull { index, name ->
                        val match =
                            when {
                                stem(name) == stem -> Match.Same

                                // A word that names an ingredient exactly is not read as part of another one's compound.
                                stem in known -> null

                                else -> compound(word, name)
                            }
                        match?.let { Mention(ingredient, index, found.range, it) }
                    }
                }
            }.toList()
    }

    /** The words that name the food of an ingredient line, in lower case. */
    fun nameWords(line: String): List<String> = words(namePart(line)).filter(::names)

    /** The part of an ingredient line that names its food: without its amount, what follows a comma or stands in brackets. */
    private fun namePart(line: String): String {
        val text = line.withoutPictographs()
        val rest = LeadingQuantityParser.parse(text)?.rest ?: text
        return rest.substringBefore(',').substringBefore('(')
    }

    /** Whether [word], in lower case, names food rather than measuring or describing it. */
    fun names(word: String): Boolean = word.length >= MIN_WORD && word !in IngredientFormatter.KNOWN_UNITS && !describes(word)

    /** Whether [word], in lower case, describes an ingredient rather than names it, as "fresh" and "gehackt" do. */
    fun describes(word: String): Boolean = stem(word) in Fillers

    /**
     * A German compound and the food it is built on, either way round: "knoblauchzehe" and "knoblauch", "tomaten" and
     * "kirschtomaten", "spinat" and "blattspinat".
     */
    private fun compound(word: String, name: String): Match? =
        when {
            word == name || minOf(word.length, name.length) < MIN_COMPOUND -> null
            name.endsWith(word) -> Match.Broader
            word.startsWith(name) || word.endsWith(name) -> Match.Built
            else -> null
        }

    private fun words(text: String): List<String> = Word.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()

    /**
     * The word without its plural or inflected endings, at most two of them, so that "eggplants",
     * "Zwiebeln" and "geriebener" meet "eggplant", "Zwiebel" and "gerieben". Both sides of a
     * comparison go through it, so a stem only has to be the same, not a word.
     */
    fun stem(word: String): String {
        if (word.endsWith("ies") && word.length - 3 >= MIN_STEM) return word.dropLast(3) + "y"
        var stem = word
        repeat(2) {
            val ending = Endings.firstOrNull { stem.endsWith(it) && stem.length - it.length >= MIN_STEM } ?: return stem
            stem = stem.dropLast(ending.length)
        }
        return stem
    }

    val Word = Regex("""\p{L}+""")

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
            "without",
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
            "ohne",
            "für",
            "aus",
            "von",
            "zum",
            "zur",
            "vom",
            "der",
            "die",
            "das",
            "den",
            "dem",
            "des",
            "ein",
            "einem",
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

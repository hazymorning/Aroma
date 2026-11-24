package app.aroma.core.format

import app.aroma.core.model.Ingredient
import app.aroma.core.model.Recipe
import java.util.Locale

/**
 * An amount to write into a step: the step's text from [start] to [end] gives way to the amount and unit of
 * [line], ingredient [ingredient] as the list shows it, followed by [then].
 */
data class StepAmount(
    val start: Int,
    val end: Int,
    val ingredient: Int,
    val line: IngredientLine,
    val then: String = " ",
)

/** The amounts to write into the recipe's steps, given the text of its [steps] as the page shows them; see [StepAmounts]. */
fun Recipe.stepAmounts(steps: List<String>, scale: Double, locale: Locale, system: MeasureSystem): List<List<StepAmount>> =
    StepAmounts.of(ingredients, steps, linkedIngredients(), scale, locale, system)

/**
 * Writes the amounts of the ingredient list into the steps, so that "Couscous kochen" reads "75 g Couscous
 * kochen". A step names an ingredient where [StepLinks] finds it, and an amount goes only where it is surely
 * all of the ingredient, because a wrong amount is worse than none:
 *
 * - At the first place the recipe names the ingredient, and only if no other step names it again, since its
 *   amount may then be split between them. A later name for what the first step made of it does not count:
 *   "Paneerwürfel" after "Paneer in Würfel schneiden".
 * - Nowhere if the recipe gives an amount or a share of the ingredient itself: "200 g Mehl", "2 EL Öl",
 *   "etwas Butter", "die Hälfte der Butter", "the remaining butter".
 * - Only where an amount reads as part of the sentence: at its start, after a comma, "und", "mit" and the
 *   like, or in place of "the" or a German "den", "die", "das", "dem" or "der" right before the noun. After
 *   another word, which may be an adjective, a measure or a number, it would not, and a German adjective
 *   after an article would keep an ending that no longer fits: "die Zwiebel" reads "1 Zwiebel", while "die
 *   gehackten Walnüsse" stays.
 * - Only where the step names the food the amount measures: all of "olive oil", not the "lemon" of "lemon
 *   juice"; and a count only its own noun, in the same number, so "2 Eier" fits "Eier" but not "Ei", and "2
 *   Knoblauchzehen" not "Knoblauch".
 *
 * German capitalises its nouns, which tells a German recipe from an English one and, within German, a noun
 * from the adjectives and verbs around it; English steps lean on a few small words instead.
 */
object StepAmounts {
    /**
     * For each of [steps], the amounts to write into it, in the order of its text. [uses] holds the ingredients
     * Mealie links to each step, or is null where the recipe links none and any step may name any ingredient.
     */
    fun of(
        ingredients: List<Ingredient>,
        steps: List<String>,
        uses: List<List<Int>>?,
        scale: Double,
        locale: Locale,
        system: MeasureSystem,
    ): List<List<StepAmount>> {
        val foods = ingredients.map { Food(it, scale, locale, system) }
        val texts = steps.map(::StepText)
        val german = isGerman(texts, locale)
        val names = foods.map { it.names }
        val namings =
            texts.flatMapIndexed { step, text ->
                text.namings(step, foods, names, uses?.let { it.getOrNull(step).orEmpty() }, german)
            }
        val amounts = namings.groupBy { it.ingredient }.values.mapNotNull { amount(it, texts) }
        return steps.indices.map { step -> amounts.filter { it.first == step }.map { it.second }.sortedBy { it.start } }
    }

    /** [text] with [amounts] written in, as plain text. */
    fun write(text: String, amounts: List<StepAmount>): String =
        buildString {
            var position = 0
            amounts.sortedBy { it.start }.forEach {
                append(text, position, it.start).append(it.line.measureText()).append(it.then)
                position = it.end
            }
            append(text, position, text.length)
        }

    /** The step that first names an ingredient, with the amount to write there, if that is surely all of it. */
    private fun amount(namings: List<Naming>, texts: List<StepText>): Pair<Int, StepAmount>? {
        val ordered = namings.sortedWith(compareBy({ it.step }, { it.start }))
        val first = ordered.first()
        if (!first.plain || ordered.any { it.share }) return null
        // Named again in the same step, it is the same; in another, only as what the first step made of it.
        val made = texts[first.step].stems
        val once = ordered.drop(1).all { later -> later.step == first.step || (!later.plain && later.others.any { it in made }) }
        return if (once) first.amount?.let { first.step to it } else null
    }

    /**
     * Whether the steps are German, where nouns start with a capital: two words and one in six within sentences
     * do. With too few words to tell, as in "Couscous kochen.", the app's language decides.
     */
    private fun isGerman(texts: List<StepText>, locale: Locale): Boolean {
        val inside = texts.flatMap { text -> text.words.filter { it.value.length > 1 && !text.opensSentence(it) } }
        if (inside.size < EVIDENCE) return locale.language == Locale.GERMAN.language
        val capitals = inside.count { it.value.first().isUpperCase() }
        return capitals >= 2 && capitals * CAPITALS_SHARE >= inside.size
    }
}

/**
 * A place where a step names an ingredient, from [start] to [end]. A [plain] naming names the food itself, any
 * other something made of it, by a name whose other parts' stems are [others]. [share] tells that the step gives
 * an amount or a share of the ingredient there, and [amount] is the amount that fits there, if one does.
 */
private class Naming(
    val ingredient: Int,
    val step: Int,
    val start: Int,
    val end: Int,
    val plain: Boolean,
    val others: Set<String> = emptySet(),
    val share: Boolean = false,
    val amount: StepAmount? = null,
)

/** What stands before a naming, and whether an amount may go there. */
private sealed interface Lead {
    /** The amount goes right before the naming. */
    data object Open : Lead

    /** The amount takes the place of the article at [start]. */
    data class Article(val start: Int) : Lead

    /** The step gives an amount or a share of the ingredient. */
    data object Share : Lead

    /** An amount would not read as part of the sentence. */
    data object Closed : Lead
}

/** What follows a naming. */
private sealed interface Follow {
    /** The sentence goes on. */
    data object Open : Follow

    /** The step gives an amount right after: "Mehl (200 g)". */
    data object Share : Follow

    /** The naming is part of a longer name ending in [word]: "Gurken-Slaw", "lemon juice". */
    data class Longer(val word: String) : Follow
}

/** What stands next to a place in a step. */
private sealed interface Side {
    /** The start or end of the text or of a sentence. */
    data object Edge : Side

    /** Punctuation within a sentence, such as a comma or a bracket. */
    data object Mark : Side

    /** A number, as the "2" of "2 EL Öl", or a unit ending in a dot. */
    data object Number : Side

    /** A word joined by a hyphen, as the "Slaw" of "Gurken-Slaw". */
    class Joined(val word: MatchResult) : Side

    /** A word with only spaces between. */
    class Word(val word: MatchResult) : Side
}

/** An ingredient as the list shows it, read for where its amount may go. */
private class Food(
    ingredient: Ingredient,
    scale: Double,
    locale: Locale,
    system: MeasureSystem,
) {
    val line = IngredientFormatter.format(ingredient, scale, locale, system)

    /** The line's name, before a comma or a bracket: "frische Hefe" of "frische Hefe (= ca. 21 g)". */
    private val part =
        line.text
            .withoutPictographs()
            .substringBefore(',')
            .substringBefore('(')
    private val words: List<MatchResult> = StepLinks.Word.findAll(part).toList()
    private val keys: List<String> = words.map { it.value.lowercase(Locale.ROOT) }
    private val nameAt: List<Int> = keys.indices.filter { index -> StepLinks.names(keys[index]) }

    /** The words that name the food, as [StepLinks] reads them. */
    val names: List<String> = nameAt.map { index -> keys[index] }

    /** The first name words in a row, which name what the amount measures: "olive oil", "Lemon Curd", "stückige Tomaten". */
    private val run: IntRange? =
        nameAt.firstOrNull()?.let { first ->
            var last = first
            while (last + 1 in nameAt &&
                part.substring(words[last].range.last + 1, words[last + 1].range.first).all { it in JOINING }
            ) {
                last++
            }
            first..last
        }

    /** Whether the amount counts the food, as in "2 Eier", rather than measuring it in a unit. */
    private val counts = line.unit.isNullOrBlank()

    /** Whether a count stays one or more than one as scaled: "1 Zwiebel" made two would read "2 Zwiebel". */
    private val keepsNumber = written(ingredient).let { (it > 1) == (it * scale > 1) }

    /** Whether the line writes a word capitalised, by the word's stem. */
    private val capitals: Map<String, Boolean> =
        buildMap {
            StepLinks.Word.findAll(listOfNotNull(line.text, line.note).joinToString(" ")).forEach {
                putIfAbsent(StepLinks.stem(it.value.lowercase(Locale.ROOT)), it.value.first().isUpperCase())
            }
        }

    /** Whether the line writes [word] capitalised, or null where it does not have it. */
    fun capitalised(word: String): Boolean? = capitals[StepLinks.stem(word.lowercase(Locale.ROOT))]

    /** Whether [word], right before a naming, still belongs to it as a word of the line: "weiße" of "Weiße Schokolade". */
    fun describes(word: String): Boolean {
        val key = word.lowercase(Locale.ROOT)
        return key.length >= MIN_WORD && capitalised(key) != null && !isUnit(key) && key !in ARTICLES && key !in JOINERS && !gives(key)
    }

    /**
     * Whether the step's words [named], each with what it names, name all that the amount measures. A count needs
     * its noun, in the same number, and nothing after it in the line: "2 Eier" fits "Eier" and "Eiern", not "Ei".
     */
    fun fits(named: List<Pair<String, StepLinks.Mention>>): Boolean {
        val run = run ?: return false
        if (line.amount == null || part.substring(0, words[run.first].range.first).any(::isNumber)) return false
        val covered = named.map { nameAt[it.second.name] }.toSet()
        if (!counts) return run.last in covered
        val noun = keys[run.last]
        return keepsNumber &&
            run.all { it in covered } &&
            (run.last + 1 until words.size).none { StepLinks.names(keys[it]) || isUnit(keys[it]) } &&
            part.substring(words[run.last].range.last + 1).none(::isNumber) &&
            named.any { (word, mention) ->
                nameAt[mention.name] == run.last && (mention.match == StepLinks.Match.Broader || word == noun || word == noun + "n")
            }
    }

    /** The amount as the recipe gives it, before scaling. */
    private fun written(ingredient: Ingredient): Double =
        if (ingredient.isParsed) {
            ingredient.quantity
        } else {
            LeadingQuantityParser
                .parse(
                    ingredient.note
                        .ifBlank { ingredient.display }
                        .withoutPictographs()
                        .trim(),
                )?.value
                ?: ingredient.quantity
        }
}

/** A step's text, read word by word. */
private class StepText(val text: String) {
    val words = StepLinks.Word.findAll(text).toList()
    private val startingAt = words.indices.associateBy { words[it].range.first }
    private val endingAt = words.indices.associateBy { words[it].range.last + 1 }
    private val durations = DurationDetector.detect(text)

    /** The stems of the step's words. */
    val stems = words.map { StepLinks.stem(it.value.lowercase(Locale.ROOT)) }.toSet()

    /**
     * Each place this, the [step]th step, names one of [foods], whose name words are [names], among the indexes
     * [uses], or among all where that is null.
     */
    fun namings(
        step: Int,
        foods: List<Food>,
        names: List<List<String>>,
        uses: List<Int>?,
        german: Boolean,
    ): List<Naming> {
        val byWord =
            StepLinks
                .mentions(names, text)
                .filter { uses == null || it.ingredient in uses }
                .groupBy { startingAt.getValue(it.range.first) }
        val namings = mutableListOf<Naming>()
        val own = sortedMapOf<Int, MutableList<Int>>()
        byWord.forEach { (index, mentions) ->
            val ingredients = mentions.map { it.ingredient }.distinct()
            val word = words[index]
            if (ingredients.size > 1) {
                // A word that may name either of two ingredients names neither for sure: no amount, but a use of both.
                ingredients.forEach { namings += Naming(it, step, word.range.first, word.range.last + 1, plain = true) }
            } else {
                own.getOrPut(ingredients.single()) { mutableListOf() } += index
            }
        }
        own.forEach { (ingredient, indexes) -> namings += namingsOf(ingredient, step, indexes.sorted(), byWord, foods[ingredient], german) }
        return namings
    }

    private fun namingsOf(
        ingredient: Int,
        step: Int,
        indexes: List<Int>,
        byWord: Map<Int, List<StepLinks.Mention>>,
        food: Food,
        german: Boolean,
    ): List<Naming> {
        val plain = indexes.filter { index -> byWord.getValue(index).any { it.match != StepLinks.Match.Built } }
        // A word built on the food's name names something made of it: "Paneerwürfel", "Hefewasser".
        val built =
            (indexes - plain.toSet()).map { index ->
                val word = words[index]
                val key = word.value.lowercase(Locale.ROOT)
                val others = byWord.getValue(index).flatMap { rest(key, food.names[it.name]) }.toSet()
                Naming(ingredient, step, word.range.first, word.range.last + 1, plain = false, others = others)
            }
        // The words of a name in a row are one naming: "olive oil", "Lemon Curd".
        val runs = mutableListOf<IntRange>()
        plain.forEach { index ->
            val last = runs.lastOrNull()
            if (last != null && last.last == index - 1 &&
                spaced(index - 1)
            ) {
                runs[runs.lastIndex] = last.first..index
            } else {
                runs += index..index
            }
        }
        return built + runs.map { naming(ingredient, step, it, byWord, food, german) }
    }

    /** The naming of the name words [run]. */
    private fun naming(
        ingredient: Int,
        step: Int,
        run: IntRange,
        byWord: Map<Int, List<StepLinks.Mention>>,
        food: Food,
        german: Boolean,
    ): Naming {
        var first = run.first
        // Words of the line before the name belong to it: the "Weiße" of "Weiße Schokolade", the "melted" of "the melted butter".
        while (first > 0 && first - 1 !in byWord && spaced(first - 1) && food.describes(words[first - 1].value)) first--
        val start = words[first].range.first
        val end = words[run.last].range.last + 1
        val before = before(start)
        val follow = follow(end, german)
        if (before is Side.Joined || follow is Follow.Longer) {
            val others = listOfNotNull((before as? Side.Joined)?.word?.value, (follow as? Follow.Longer)?.word)
            return Naming(
                ingredient,
                step,
                start,
                end,
                plain = false,
                others = others.map { StepLinks.stem(it.lowercase(Locale.ROOT)) }.toSet(),
            )
        }
        val lead = lead(before, words[first], german)
        val share = lead == Lead.Share || follow == Follow.Share
        val head = words[run.last].value
        // German capitalises its nouns: "salzen" is a verb, not the "Salz" of the list.
        val verb = german && head.first().isLowerCase() && food.capitalised(head) == true
        val named =
            run.flatMap { index ->
                byWord.getValue(index).filter { it.match != StepLinks.Match.Built }.map { words[index].value.lowercase(Locale.ROOT) to it }
            }
        val amount =
            if (share || verb || follow != Follow.Open ||
                !food.fits(named)
            ) {
                null
            } else {
                amountAt(lead, start, words[first], ingredient, food)
            }
        return Naming(ingredient, step, start, end, plain = true, share = share, amount = amount)
    }

    private fun amountAt(
        lead: Lead,
        start: Int,
        first: MatchResult,
        ingredient: Int,
        food: Food,
    ): StepAmount? =
        when (lead) {
            is Lead.Article -> {
                StepAmount(lead.start, start, ingredient, food.line)
            }

            Lead.Open -> {
                // A word capitalised only for starting the sentence, as "Weiße" of "Weiße Schokolade", takes the line's spelling.
                val initial = first.value.first()
                if (initial.isUpperCase() && food.capitalised(first.value) == false && opensSentence(first)) {
                    StepAmount(start, start + 1, ingredient, food.line, " " + initial.lowercase())
                } else {
                    StepAmount(start, start, ingredient, food.line)
                }
            }

            else -> {
                null
            }
        }

    /** What [before] a naming that starts with the word [first] says about writing an amount there. */
    private fun lead(before: Side, first: MatchResult, german: Boolean): Lead =
        when (before) {
            Side.Edge, Side.Mark -> {
                Lead.Open
            }

            Side.Number -> {
                Lead.Share
            }

            is Side.Joined -> {
                Lead.Closed
            }

            is Side.Word -> {
                if (before.word.value.lowercase(Locale.ROOT) in
                    ARTICLES
                ) {
                    article(before.word, first, german)
                } else {
                    afterWord(before.word, german, Lead.Open)
                }
            }
        }

    /** Whether an amount reads well after [word], where [open] says how it would go; or whether the step gives a share there. */
    private fun afterWord(word: MatchResult, german: Boolean, open: Lead): Lead {
        val key = word.value.lowercase(Locale.ROOT)
        return when {
            gives(key) || isUnit(key) || text.getOrNull(word.range.first - 1)?.isDigit() == true -> Lead.Share

            key in JOINERS || durations.any { it.end == word.range.last + 1 } -> open

            // Within a German sentence, a capital marks a noun: a measure, as in "einen Schuss Milch", or the
            // "Hälfte" of "die Hälfte der Butter", unless it ends a place, as in "In einer Schüssel Mehl mischen".
            german && word.value.first().isUpperCase() && !opensSentence(word) -> if (endsPlace(word)) open else Lead.Share

            german -> if (inflected(key)) Lead.Closed else open

            // An English sentence starts with its verb, as in "Add flour"; within it, the word could be an adjective.
            opensSentence(word) && !describing(key) -> open

            else -> Lead.Closed
        }
    }

    /** Whether the amount may take the place of [article] before a naming that starts with the word [first]. */
    private fun article(article: MatchResult, first: MatchResult, german: Boolean): Lead {
        val english = article.value.equals("the", ignoreCase = true)
        // A German adjective after an article ends to fit it: "die gehackten Walnüsse" would not read "50 g gehackten Walnüsse".
        if (english == german || (german && first.value.first().isLowerCase())) return Lead.Closed
        val replace = Lead.Article(article.range.first)
        return when (val before = before(article.range.first)) {
            Side.Edge, Side.Mark -> {
                replace
            }

            Side.Number -> {
                Lead.Share
            }

            is Side.Joined -> {
                Lead.Closed
            }

            is Side.Word -> {
                val key = before.word.value.lowercase(Locale.ROOT)
                when {
                    !english -> afterWord(before.word, german = true, replace)
                    gives(key) || isUnit(key) -> Lead.Share
                    else -> replace
                }
            }
        }
    }

    /** What follows a naming that ends at [end]. */
    private fun follow(end: Int, german: Boolean): Follow =
        when (val after = after(end)) {
            Side.Number -> {
                Follow.Share
            }

            is Side.Joined -> {
                Follow.Longer(after.word.value)
            }

            is Side.Word -> {
                val word = after.word.value
                val key = word.lowercase(Locale.ROOT)
                when {
                    // A German noun right after another makes one name with it: "Tomaten Mozzarella".
                    german -> if (word.first().isUpperCase()) Follow.Longer(word) else Follow.Open

                    key in FOLLOWERS || (key.length > MIN_WORD && key.endsWith("ly")) -> Follow.Open

                    else -> Follow.Longer(word)
                }
            }

            else -> {
                Follow.Open
            }
        }

    /** Whether the noun [noun] ends a place, as "Schüssel" does in "In einer Schüssel Mehl und Zucker mischen". */
    private fun endsPlace(noun: MatchResult): Boolean {
        var index = startingAt.getValue(noun.range.first) - 1
        while (index >= 0 && spaced(index)) {
            val key = words[index].value.lowercase(Locale.ROOT)
            when {
                key in PLACED -> {
                    return true
                }

                key in ARTICLES || key in DurationDetector.numberWords || key == "einem" -> {
                    return index > 0 && spaced(index - 1) && words[index - 1].value.lowercase(Locale.ROOT) in PLACES
                }

                // An adjective between, as in "In einer zweiten Schüssel".
                words[index].value.first().isLowerCase() -> {
                    index--
                }

                else -> {
                    return false
                }
            }
        }
        return false
    }

    /** Whether [word] starts a sentence. */
    fun opensSentence(word: MatchResult): Boolean = before(word.range.first) == Side.Edge

    /** Whether only spaces stand between the [index]th word and the next. */
    private fun spaced(index: Int): Boolean {
        val gap = text.substring(words[index].range.last + 1, words[index + 1].range.first)
        return gap.isNotEmpty() && gap.all { it in SPACES }
    }

    private fun before(position: Int): Side {
        if (text.getOrNull(position - 1).isAny(HYPHENS) && text.getOrNull(position - 2)?.isLetter() == true) {
            return Side.Joined(words[endingAt.getValue(position - 1)])
        }
        var index = position - 1
        while (index >= 0 && text[index] in SPACES) index--
        val char = text.getOrNull(index) ?: return Side.Edge
        return when {
            char.isLetter() -> Side.Word(words[endingAt.getValue(index + 1)])

            isNumber(char) -> Side.Number

            // The dot of "1 Teel. Salz" ends a unit, not a sentence.
            char == '.' && text.getOrNull(index - 1)?.isLetter() == true &&
                isUnit(words[endingAt.getValue(index)].value.lowercase(Locale.ROOT)) -> Side.Number

            char in SENTENCE_ENDS -> Side.Edge

            else -> Side.Mark
        }
    }

    private fun after(position: Int): Side {
        if (text.getOrNull(position).isAny(HYPHENS) && text.getOrNull(position + 1)?.isLetter() == true) {
            return Side.Joined(words[startingAt.getValue(position + 1)])
        }
        var index = position
        while (index < text.length && text[index] in SPACES) index++
        val char = text.getOrNull(index) ?: return Side.Edge
        return when {
            char.isLetter() -> Side.Word(words[startingAt.getValue(index)])

            // A time after a naming is the step's, as in "Zwiebeln 5 Minuten dünsten"; any other number gives an amount.
            isNumber(char) -> if (durations.any { it.start == index }) Side.Mark else Side.Number

            char == '(' && text
                .substring(index + 1)
                .trimStart()
                .firstOrNull()
                ?.let(::isNumber) == true -> Side.Number

            char in SENTENCE_ENDS -> Side.Edge

            else -> Side.Mark
        }
    }
}

/** What a word built on the name word [name] adds to it, as stems: "würfel" of "paneerwürfel", "hefe" of "hefewasser". */
private fun rest(word: String, name: String): List<String> {
    val rest = if (word.startsWith(name)) word.removePrefix(name) else word.removeSuffix(name)
    // German may join the parts with an "s" or an "n": "Tomatenwürfel" built on "Tomate".
    val joined = rest.drop(1).takeIf { rest.firstOrNull()?.let { it == 's' || it == 'n' } == true }
    return listOfNotNull(rest, joined).filter { it.isNotEmpty() }.map(StepLinks::stem)
}

/** Whether [key] gives an amount, a share or none of an ingredient: a number, "etwas", "the remaining", "ohne". */
private fun gives(key: String) = key in DurationDetector.numberWords || StepLinks.stem(key) in GIVES

private fun isUnit(key: String) = key.trimEnd('.') in IngredientFormatter.KNOWN_UNITS || Measures.named(key, null) != null

/** Whether an English word describes rather than acts, as "fresh", "melted" and "boiling" do. */
private fun describing(key: String) = StepLinks.describes(key) || (key.length > LONG_WORD && (key.endsWith("ed") || key.endsWith("ing")))

/** Whether a German word has the ending of an adjective or a pronoun before a noun: "kalte", "gehackten", "diese". */
private fun inflected(key: String) = key.length > MIN_WORD && (key.endsWith("e") || (key.dropLast(1).endsWith("e") && key.last() in "nrsm"))

private fun isNumber(char: Char) = char.isDigit() || char in GLYPH_VALUES

private fun Char?.isAny(chars: Set<Char>) = this != null && this in chars

private const val MIN_WORD = 3

/** Past "Bring" and "Shred": longer words ending in "-ed" or "-ing" describe, as "melted" and "boiling" do. */
private const val LONG_WORD = 5

/** One word in this many within the sentences starting with a capital makes a recipe German. */
private const val CAPITALS_SHARE = 6

/** The least words within sentences that tell German from English. */
private const val EVIDENCE = 8

private val SPACES = setOf(' ', '\t', ' ', ' ')
private val HYPHENS = setOf('-', '‐', '‑')

/** What may stand between the words of one name: "olive oil", "Low-Carb-Tortillas". */
private val JOINING = SPACES + HYPHENS
private val SENTENCE_ENDS = setOf('.', '!', '?', ':', ';', '\n', '\r', '•')

/** "the" and the German articles an amount may take the place of: "den Zucker" reads "240 g Zucker". */
private val ARTICLES = setOf("the", "der", "die", "das", "den", "dem")

/** Words after which an amount reads as part of the sentence: "und 2 EL Öl", "mit 100 ml Sahne", "with 2 eggs". */
private val JOINERS =
    setOf(
        "und",
        "oder",
        "sowie",
        "mit",
        "in",
        "auf",
        "aus",
        "über",
        "unter",
        "zu",
        "für",
        "and",
        "or",
        "plus",
        "with",
        "into",
        "onto",
        "on",
        "over",
        "to",
        "for",
    )

/** Words after which an English sentence goes on, so the food before them is not part of a longer name, as in "lemon juice". */
private val FOLLOWERS =
    JOINERS + setOf("until", "till", "then", "as", "while", "if", "so", "but", "together", "before", "after", "at", "from", "by")

/** Places an amount may follow, as in "In einer Schüssel Mehl mischen", and the same with the article in: "im Topf". */
private val PLACES = setOf("in", "auf")
private val PLACED = setOf("im", "ins", "aufs")

/** Words that give an amount, a share or none of an ingredient, by their stems, besides numbers. */
private val GIVES =
    listOf(
        "einem",
        "eines",
        "kein",
        "ohne",
        "etwas",
        "wenig",
        "viel",
        "mehr",
        "noch",
        "halb",
        "restlich",
        "übrig",
        "weitere",
        "zusätzlich",
        "alle",
        "von",
        "vom",
        "des",
        "no",
        "without",
        "some",
        "more",
        "less",
        "little",
        "bit",
        "enough",
        "half",
        "remaining",
        "leftover",
        "reserved",
        "another",
        "additional",
        "extra",
        "all",
        "any",
        "of",
    ).map(StepLinks::stem).toSet()

package app.aroma.core.format

/**
 * Recipe text from social media posts. When Mealie finds no recipe data on a page, it builds the
 * recipe from the link preview, which for Instagram and similar sites is the whole caption,
 * framed by the platform: `Name on Instagram: "…"`. This recovers the caption and splits it into
 * the parts of a recipe.
 */
object SocialCaption {
    // Mealie numbers a name it already has, so a post imported twice ends in " (1)" after the quote.
    private val Quoted = Regex("""^[^\n]{0,200}?:\s*["“„«](.*)["”“»]\s*\.?(\s+\(\d+\))?\s*$""", RegexOption.DOT_MATCHES_ALL)
    private val SiteSuffix = Regex("""\s+[|·•–-]\s+(Instagram|TikTok|Facebook|YouTube|Pinterest|Threads)\s*$""", RegexOption.IGNORE_CASE)

    /** The post's own text from a link preview's title and description, whichever holds more of it. */
    fun extract(title: String, description: String): String =
        listOf(title, description)
            .map(::unwrap)
            .maxBy { it.length }

    private fun unwrap(text: String): String {
        val trimmed = text.trim().replace(SiteSuffix, "")
        return (Quoted.find(trimmed)?.groupValues?.get(1) ?: trimmed).trim()
    }

    fun parse(caption: String): CaptionRecipe = CaptionParser(caption).parse()
}

/**
 * A recipe read out of free text. Ingredient lines starting with "# " are section titles, as in
 * the editor. [name] is empty when the text does not start with anything that reads as a title.
 */
data class CaptionRecipe(
    val name: String,
    val description: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val notes: List<String>,
    val servings: Double?,
)

private class CaptionParser(caption: String) {
    private enum class Part { Intro, Ingredients, Steps, Notes }

    private val lines = caption.replace("\r\n", "\n").lines().map(::normalize)
    private val intro = mutableListOf<String>()
    private val ingredients = mutableListOf<String>()
    private val steps = mutableListOf<String>()
    private val notes = mutableListOf<String>()
    private val outro = mutableListOf<String>()

    fun parse(): CaptionRecipe {
        var part = Part.Intro
        var numberedSteps = false
        var continuesStep = false
        var servings: Double? = null
        for (line in lines) {
            if (line.isEmpty()) {
                continuesStep = false
                continue
            }
            val header = headerOf(line)
            if (header != null) {
                part = header
                servings = servings ?: servingsIn(line)
                continuesStep = false
                continue
            }
            val numbered = Numbered.find(line)?.groupValues?.get(1)
            val bullet = Bullet.find(line)?.groupValues?.get(1)
            val inlineNote = InlineNote.find(line)?.groupValues?.get(1)
            var addsToStep = false
            when {
                inlineNote != null -> {
                    notes += inlineNote
                }

                numbered != null && part != Part.Notes -> {
                    part = Part.Steps
                    numberedSteps = true
                    steps += withoutLeadingSymbols(numbered)
                    addsToStep = true
                }

                part == Part.Steps && numberedSteps && continuesStep -> {
                    steps[steps.lastIndex] = steps.last() + " " + line
                    addsToStep = true
                }

                part == Part.Steps && numberedSteps -> {
                    outro += line
                }

                part == Part.Steps -> {
                    steps += withoutLeadingSymbols(bullet ?: line)
                    addsToStep = true
                }

                part == Part.Ingredients -> {
                    ingredients += if (isSubHeader(line, bullet)) "# " + plain(line) else withoutLeadingSymbols(bullet ?: line)
                }

                part == Part.Notes -> {
                    notes += withoutLeadingSymbols(bullet ?: line)
                }

                bullet != null -> {
                    ingredients += withoutLeadingSymbols(bullet)
                }

                ingredients.isEmpty() && steps.isEmpty() -> {
                    intro += line
                }

                else -> {
                    outro += line
                }
            }
            continuesStep = addsToStep
        }
        val (name, titleRest) = title()
        val description = listOfNotNull(titleRest) + intro.drop(1) + outro.filterNot(::isCallToAction)
        return CaptionRecipe(
            name = name,
            description = description.joinToString("\n").trim(),
            ingredients = ingredients.filter { it.isNotBlank() }.dropLastWhile { it.startsWith("# ") },
            steps = steps.filter { it.isNotBlank() },
            notes = notes.filter { it.isNotBlank() },
            servings = servings ?: lines.firstNotNullOfOrNull(::servingsIn),
        )
    }

    /** First line of the intro, cut after its first sentence when it runs long. */
    private fun title(): Pair<String, String?> {
        val clean = plain(intro.firstOrNull() ?: return "" to null)
        if (clean.length <= MAX_TITLE) return clean to null
        val end = SentenceEnd.find(clean)?.takeIf { it.range.first in MIN_TITLE..MAX_SENTENCE }
        if (end != null) return clean.substring(0, end.range.first).trim() to clean.substring(end.range.last + 1).trim().ifEmpty { null }
        val words = clean.take(MAX_TITLE).substringBeforeLast(' ')
        return words to clean.removePrefix(words).trim().ifEmpty { null }
    }

    private fun headerOf(line: String): Part? {
        val text =
            plain(line)
                .replace(Parenthesized, "")
                .trimEnd(':')
                .trim()
                .lowercase()
        if (text.isEmpty() || line.length > MAX_HEADER) return null
        return when {
            IngredientHeaders.any { text.startsWith(it) } -> Part.Ingredients
            StepHeaders.any { text.startsWith(it) } -> Part.Steps
            NoteHeaders.any { text == it || text.startsWith("$it ") } -> Part.Notes
            else -> null
        }
    }

    private fun isSubHeader(line: String, bullet: String?): Boolean =
        bullet == null && line.endsWith(":") && line.length <= MAX_HEADER && line.none { it.isDigit() }

    private fun servingsIn(line: String): Double? =
        Servings
            .find(line)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotEmpty() }
            ?.replace(',', '.')
            ?.toDoubleOrNull()

    private fun isCallToAction(line: String): Boolean = line.lowercase().let { text -> CallsToAction.any { it in text } }

    companion object {
        private const val MIN_TITLE = 8
        private const val MAX_TITLE = 50
        private const val MAX_SENTENCE = 90
        private const val MAX_HEADER = 48

        private val Numbered = Regex("""^(?:(?:schritt|step)\s*)?\d{1,2}\s*[.):]\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val Bullet = Regex("""^[-–—•·*▪▫◦●○✓✔➤►▶→]\s*(.+)$""")
        private val InlineNote = Regex("""^\W*(?:tipp|tip|hinweis|note)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
        private val SentenceEnd = Regex("""[.!?]\s|\s[–—-]\s|\s\|\s""")
        private val Parenthesized = Regex("""\(.*?\)""")
        private val Servings =
            Regex(
                """(\d+(?:[.,]\d+)?)\s*(?:portionen|portion|personen|person|servings|serving|people|persons)\b|serves\s+(\d+)""",
                RegexOption.IGNORE_CASE,
            )
        private val Keycap = Regex("""(\d)️?⃣""")
        private val OnlyTags = Regex("""^(?:[#@][\p{L}\p{N}_.]+\s*)+$""")
        private val TrailingTags = Regex("""(?:\s+#[\p{L}\p{N}_]+)+\s*$""")
        private val InlineTag = Regex("""#([\p{L}\p{N}_]+)""")

        private val IngredientHeaders =
            listOf(
                "zutaten",
                "ingredients",
                "ingredient",
                "einkaufsliste",
                "du brauchst",
                "ihr braucht",
                "you need",
                "you’ll need",
                "you'll need",
            )
        private val StepHeaders =
            listOf(
                "zubereitung",
                "anleitung",
                "so geht",
                "so wird",
                "schritte",
                "instructions",
                "directions",
                "method",
                "steps",
                "how to make",
                "preparation",
            )
        private val NoteHeaders = listOf("tipp", "tipps", "tip", "tips", "hinweis", "hinweise", "notes", "note")
        private val CallsToAction =
            listOf(
                "speicher",
                "save this",
                "save the",
                "folg",
                "follow",
                "link in",
                "teilt",
                "share",
                "markier",
                "tag a",
                "kommentier",
                "comment",
            )

        /** Keycap digits ("1️⃣") become plain numbering; hashtags at the end of a line go, inline ones keep their word. */
        private fun normalize(line: String): String {
            val text = line.replace(Keycap, "$1.").trim()
            if (OnlyTags.matches(text)) return ""
            return text.replace(TrailingTags, "").replace(InlineTag, "$1").trim()
        }

        private fun isPictograph(codePoint: Int): Boolean =
            (Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt() && codePoint != DEGREE) ||
                codePoint in 0xFE00..0xFE0F ||
                codePoint == ZERO_WIDTH_JOINER ||
                codePoint in 0x1F3FB..0x1F3FF

        private fun withoutLeadingSymbols(text: String): String {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                if (!isPictograph(codePoint) && !Character.isWhitespace(codePoint)) break
                index += Character.charCount(codePoint)
            }
            return text.substring(index).trim()
        }

        /** Text without emoji, for names and headers. */
        fun plain(text: String): String =
            buildString { text.codePoints().forEach { if (!isPictograph(it)) appendCodePoint(it) } }
                .replace(Regex("""\s+"""), " ")
                .trim()
                .trim('-', '–', '—', '|', ':', '·', '*', '_', ' ')

        private const val DEGREE = 0x00B0
        private const val ZERO_WIDTH_JOINER = 0x200D
    }
}

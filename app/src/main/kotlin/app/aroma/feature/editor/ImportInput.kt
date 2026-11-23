package app.aroma.feature.editor

/**
 * What the add screen's one field holds: a [link], the [text] of a recipe, or both. The first web
 * link is the link. Apps share a link with its title or a few words around it, which are no
 * recipe, so what stands beside the link only counts as its text where it is longer than that.
 */
data class ImportInput(val link: String?, val text: String) {
    companion object {
        fun of(input: String): ImportInput {
            val link = findLink(input) ?: return ImportInput(null, input.trim())
            val rest = input.replaceFirst(link, "").trim()
            val isRecipe = rest.lines().count(String::isNotBlank) > 1 || rest.length > TITLE_LENGTH
            return ImportInput(link, if (isRecipe) rest else "")
        }
    }
}

/** The first web link in [text], without the punctuation of the sentence it ends. */
internal fun findLink(text: String): String? = WebLink.find(text)?.value?.trimEnd('.', ',', ')', '!', '?', '"', '”')

private val WebLink = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

/** As long as a title or a line such as "Look at this recipe" around a shared link gets. */
private const val TITLE_LENGTH = 100

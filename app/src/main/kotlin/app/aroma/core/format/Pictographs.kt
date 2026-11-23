package app.aroma.core.format

/**
 * The text without emoji. Posts set them before ingredients ("🌾 500 g Mehl"), where they hide the amount from
 * scaling. Pictographs go, and so does everything that only exists to build emoji: variation selectors, the joiner
 * between pictographs ("👩‍🍳"), skin tones, the letter pairs of flags, tags and the keycap mark, whose digit stays
 * ("1️⃣" becomes "1").
 *
 * The spaces around an emoji become one, or none at the start or end of a line, after an opening bracket or before
 * punctuation. Line breaks stay, so a line of only emoji becomes empty. Text without emoji comes back as it is:
 * letters of any script, digits, fractions, units and signs such as "°", "×", "–", "•" or quotes are not pictographs.
 */
fun String.withoutPictographs(): String {
    val text = StringBuilder(length)
    // Whether an emoji went since the last character kept, and whether a space stood next to it.
    var gap = false
    var spaced = false
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        when {
            isEmojiAt(index, codePoint, afterEmoji = gap) -> {
                while (text.lastOrNull()?.isSpace() == true) {
                    text.deleteAt(text.lastIndex)
                    spaced = true
                }
                gap = true
            }

            gap && this[index].isSpace() -> {
                spaced = true
            }

            else -> {
                if (gap && spaced && text.isNotEmpty() && spaceFits(text.last(), this[index])) text.append(' ')
                gap = false
                spaced = false
                text.appendCodePoint(codePoint)
            }
        }
        index += Character.charCount(codePoint)
    }
    return text.toString()
}

/** Whether the code point at [index] belongs to an emoji. A joiner only does between pictographs: Indic scripts join letters with it. */
private fun String.isEmojiAt(index: Int, codePoint: Int, afterEmoji: Boolean): Boolean =
    when (codePoint) {
        ZERO_WIDTH_JOINER -> afterEmoji || (index + 1 < length && isPictographic(codePointAt(index + 1)))
        TEXT_PRESENTATION, EMOJI_PRESENTATION, COMBINING_KEYCAP -> true
        in SKIN_TONES, in REGIONAL_INDICATORS, in TAGS -> true
        else -> isPictographic(codePoint)
    }

/** A space within a line, unlike a line break. */
private fun Char.isSpace() = this == '\t' || category == CharCategory.SPACE_SEPARATOR

/** Whether the space where an emoji was belongs between [before] and [after]. */
private fun spaceFits(before: Char, after: Char) =
    !before.isWhitespace() && before !in "([{" && !after.isWhitespace() && after !in ".,;:!?…)]}"

private fun isPictographic(codePoint: Int): Boolean =
    PICTOGRAPHIC.binarySearch { range ->
        when {
            range.last < codePoint -> -1
            range.first > codePoint -> 1
            else -> 0
        }
    } >= 0

private const val ZERO_WIDTH_JOINER = 0x200D
private const val TEXT_PRESENTATION = 0xFE0E
private const val EMOJI_PRESENTATION = 0xFE0F
private const val COMBINING_KEYCAP = 0x20E3
private val SKIN_TONES = 0x1F3FB..0x1F3FF

/** The letters A to Z that flags are written with, two to a flag: "🇩🇪" is D and E. */
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF

/** Invisible letters after "🏴" that name the flag of a region, such as Scotland's. */
private val TAGS = 0xE0020..0xE007F

/**
 * Unicode's Extended_Pictographic code points, in the notation of emoji-data.txt: the pictographs of all emoji, and
 * the symbols and unassigned code points kept for emoji to come. These are its ranges from Unicode 14 to 16
 * (https://www.unicode.org/Public/16.0.0/ucd/emoji/emoji-data.txt), merged where they meet, as Java 21's
 * Character.isExtendedPictographic has them. Android has that method only from API 36, and ICU's
 * UProperty.EXTENDED_PICTOGRAPHIC from API 29, while Aroma runs from API 26. Unicode 17 narrowed the property to
 * emoji alone, without symbols such as "★", "♡" or "☞" that posts use as emoji all the same.
 */
private val PICTOGRAPHIC: List<IntRange> =
    """
    00A9 00AE 203C 2049 2122 2139 2194..2199 21A9..21AA 231A..231B 2328 2388 23CF 23E9..23F3 23F8..23FA 24C2 25AA..25AB
    25B6 25C0 25FB..25FE 2600..2605 2607..2612 2614..2685 2690..2705 2708..2712 2714 2716 271D 2721 2728 2733..2734 2744
    2747 274C 274E 2753..2755 2757 2763..2767 2795..2797 27A1 27B0 27BF 2934..2935 2B05..2B07 2B1B..2B1C 2B50 2B55 3030
    303D 3297 3299 1F000..1F0FF 1F10D..1F10F 1F12F 1F16C..1F171 1F17E..1F17F 1F18E 1F191..1F19A 1F1AD..1F1E5
    1F201..1F20F 1F21A 1F22F 1F232..1F23A 1F23C..1F23F 1F249..1F3FA 1F400..1F53D 1F546..1F64F 1F680..1F6FF 1F774..1F77F
    1F7D5..1F7FF 1F80C..1F80F 1F848..1F84F 1F85A..1F85F 1F888..1F88F 1F8AE..1F8FF 1F90C..1F93A 1F93C..1F945
    1F947..1FAFF 1FC00..1FFFD
    """.trim()
        .split(Regex("""\s+"""))
        .map { entry -> entry.split("..").map { it.toInt(16) }.let { it.first()..it.last() } }

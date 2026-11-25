package app.aroma.core.model

/**
 * Which recipes a cookbook holds, as far as Aroma can tell and change it. Mealie gathers a cookbook's
 * recipes by a rule, such as "in one of these categories" or "with all of these tags". Where the rule
 * asks for categories and tags alone, a recipe goes into the cookbook by being filed under them, and
 * out of it by being filed under them no longer.
 */
sealed interface CookbookRule {
    /** No rule: the cookbook holds every recipe. */
    data object Everything : CookbookRule

    /** Categories and tags alone, every one of [conditions] to be met. */
    data class Filed(val conditions: List<FilingCondition>) : CookbookRule {
        /** Whether a recipe filed under [filing] is in the cookbook. */
        fun holds(filing: Filing): Boolean = conditions.all { it.holds(filing) }

        /**
         * [filing] with what the cookbook asks for added, from the group's [available] categories and
         * tags; null where that cannot be done by adding, as where a condition asks for one to be left out.
         */
        fun adding(filing: Filing, available: Organizers): Filing? =
            conditions.fold(filing as Filing?) { filed, condition -> filed?.let { condition.meetBy(it, available) } }

        /**
         * [filing] without what the cookbook asks for: every category and tag its first condition that
         * holds names, which takes the recipe out. Null where the cookbook asks only for some to be left out.
         */
        fun removing(filing: Filing): Filing? =
            conditions.firstOrNull { it.match != FilingCondition.Match.NoneOf && it.holds(filing) }?.let { condition ->
                filing.without(condition.kind) { condition.names(it) }
            }
    }

    /** Anything else, a rating, a date, a tool, or one condition or another: Mealie alone knows what it holds. */
    data object Own : CookbookRule

    companion object {
        /**
         * The rule of a cookbook from Mealie's reading of its filter, [parts] in their order, and the
         * filter as [written]; Mealie leaves the parts empty where it could not read the filter.
         */
        fun of(parts: List<RulePart>, written: String): CookbookRule {
            if (parts.isEmpty()) return if (written.isBlank()) Everything else Own
            val conditions =
                parts.mapIndexed { index, part ->
                    // One condition or another is more than filing a recipe can promise.
                    if (index > 0 && !part.joinedBy.equals("AND", ignoreCase = true)) return Own
                    FilingCondition.of(part) ?: return Own
                }
            return Filed(conditions)
        }
    }
}

/** One part of a cookbook's filter as Mealie reads it: what it looks at, how, the values, and the word that joins it to the part before. */
data class RulePart(
    val attribute: String?,
    val operator: String?,
    val values: List<String>,
    val joinedBy: String? = null,
)

/** A condition on a recipe's categories or tags: [match] the ones named by [values], which are their ids, slugs or names ([key]). */
data class FilingCondition(
    val kind: OrganizerKind,
    val key: Key,
    val match: Match,
    val values: List<String>,
) {
    enum class Match { AnyOf, AllOf, NoneOf }

    /** What the values name an organizer by. */
    enum class Key {
        Id,
        Slug,
        Name,
        ;

        fun of(organizer: Organizer): String =
            when (this) {
                Id -> organizer.id
                Slug -> organizer.slug
                Name -> organizer.name
            }
    }

    /** Whether [organizer] is one this condition names. */
    fun names(organizer: Organizer): Boolean = values.any { value -> key.of(organizer).trim().equals(value.trim(), ignoreCase = true) }

    fun holds(filing: Filing): Boolean {
        val filed = filing.of(kind)
        return when (match) {
            Match.AnyOf -> filed.any(::names)
            Match.AllOf -> values.all { value -> filed.any { key.of(it).trim().equals(value.trim(), ignoreCase = true) } }
            Match.NoneOf -> filed.none(::names)
        }
    }

    /** [filing] with this condition met from [available], or null where it cannot be met by adding. */
    fun meetBy(filing: Filing, available: Organizers): Filing? {
        if (holds(filing)) return filing
        val named = values.map { value -> available.of(kind).firstOrNull { key.of(it).trim().equals(value.trim(), ignoreCase = true) } }
        val wanted =
            when (match) {
                Match.AnyOf -> listOfNotNull(named.firstOrNull { it != null })

                // Every one is asked for, so one the group no longer has cannot be met.
                Match.AllOf -> if (named.any { it == null }) return null else named.filterNotNull()

                Match.NoneOf -> return null
            }
        if (wanted.isEmpty()) return null
        return filing.with(kind, wanted)
    }

    companion object {
        /** The condition [part] states, or null where it asks about anything but categories and tags. */
        fun of(part: RulePart): FilingCondition? {
            val attribute = part.attribute?.trim()?.lowercase() ?: return null
            val kind =
                when (attribute.substringBefore('.')) {
                    "recipe_category", "recipecategory" -> OrganizerKind.Category
                    "tags" -> OrganizerKind.Tag
                    else -> return null
                }
            val key =
                when (attribute.substringAfter('.', missingDelimiterValue = "")) {
                    "id" -> Key.Id
                    "slug" -> Key.Slug
                    "name" -> Key.Name
                    else -> return null
                }
            val match =
                when (part.operator?.trim()?.uppercase()) {
                    "IN", "IS", "=" -> Match.AnyOf
                    "CONTAINS ALL" -> Match.AllOf
                    "NOT IN", "IS NOT", "<>" -> Match.NoneOf
                    else -> return null
                }
            val values = part.values.filter { it.isNotBlank() }
            if (values.isEmpty()) return null
            return FilingCondition(kind, key, match, values)
        }
    }
}

/** The categories or the tags of [this] filing. */
fun Filing.of(kind: OrganizerKind): List<Organizer> =
    when (kind) {
        OrganizerKind.Category -> categories
        OrganizerKind.Tag -> tags
    }

/** The group's categories or tags. */
fun Organizers.of(kind: OrganizerKind): List<Organizer> =
    when (kind) {
        OrganizerKind.Category -> categories
        OrganizerKind.Tag -> tags
    }

private fun Filing.with(kind: OrganizerKind, added: List<Organizer>): Filing {
    val now = of(kind)
    val more = now + added.filter { organizer -> now.none(organizer::sameAs) }
    return when (kind) {
        OrganizerKind.Category -> copy(categories = more)
        OrganizerKind.Tag -> copy(tags = more)
    }
}

private fun Filing.without(kind: OrganizerKind, leaves: (Organizer) -> Boolean): Filing =
    when (kind) {
        OrganizerKind.Category -> copy(categories = categories.filterNot(leaves))
        OrganizerKind.Tag -> copy(tags = tags.filterNot(leaves))
    }

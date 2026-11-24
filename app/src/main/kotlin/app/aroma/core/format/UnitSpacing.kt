package app.aroma.core.format

/**
 * Binds a number to the unit after it with a narrow no-break space, "500 g", "30 Min.", "180 °C",
 * as a German typesetter does, so the two never part at a line's end. Text of a recipe's own, such
 * as its steps, is set this way; the length stays the same, so marks found in it stay in place.
 */
object UnitSpacing {
    private val Units =
        listOf(
            "g",
            "kg",
            "mg",
            "ml",
            "cl",
            "dl",
            "l",
            "EL",
            "TL",
            "Msp.",
            "Msp",
            "Prise",
            "Prisen",
            "Min.",
            "Min",
            "Minute",
            "Minuten",
            "Std.",
            "Std",
            "Stunde",
            "Stunden",
            "Sek.",
            "Sekunden",
            "°C",
            "°F",
            "cm",
            "mm",
            "Stück",
            "Scheiben",
            "Zehen",
            "Bund",
            "Dose",
            "Dosen",
            "Pck.",
            "Päckchen",
            "Tasse",
            "Tassen",
            "tbsp",
            "tsp",
            "cup",
            "cups",
            "oz",
            "lb",
            "lbs",
            "min",
            "mins",
            "minute",
            "minutes",
            "hour",
            "hours",
            "hr",
            "hrs",
            "second",
            "seconds",
            "sec",
            "secs",
        )

    private val Between =
        Regex("""(?<=\d)[  ](?=(?:${Units.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})(?![\p{L}\p{N}]))""")

    /** [text] with a narrow no-break space between each number and the unit that follows it. */
    fun apply(text: String): String = text.replace(Between, " ")
}

package app.aroma.core.format

/**
 * How amounts are shown: as the recipe writes them, or converted on the phone to grams and
 * millilitres or to cups, ounces and pounds. Converting only changes what is displayed.
 */
enum class MeasureSystem { AsWritten, Metric, US }

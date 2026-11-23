package app.aroma.core.format

import java.util.Locale

/**
 * What a level US cup of a food weighs in grams, so that a metric kitchen can weigh what a US
 * recipe measures in cups. Milk, water, cream, broth, coconut milk, wine and juice are missing on
 * purpose: metric recipes pour liquids in millilitres too.
 *
 * Dry goods weigh what King Arthur Baking's ingredient weight chart gives
 * (https://www.kingarthurbaking.com/learn/ingredient-weight-chart), one source for all of them so
 * they agree with each other. Fats, syrups and dairy weigh what USDA FoodData Central's household
 * measures give (SR Legacy, https://fdc.nal.usda.gov), which are weighed rather than taken as 8 oz.
 *
 * A food matches only when its name says what it is, once words that don't change the weight,
 * such as "packed", "unsalted" or "zarte", are left out. "Kosher salt", "almond flour" or "walnuts"
 * match nothing and stay volumes: a tablespoon of kosher salt weighs 8 or 16 g depending on the
 * brand, and a cup of whole walnuts 100 g to the USDA but 128 g to King Arthur.
 */
internal enum class CupWeight(val grams: Double, private vararg val names: String) {
    Flour(120.0, "flour", "all-purpose flour", "plain flour", "bread flour", "cake flour", "Mehl", "Weizenmehl"),
    WholeWheatFlour(113.0, "whole wheat flour", "wholemeal flour", "Vollkornmehl", "Weizenvollkornmehl"),
    Sugar(198.0, "sugar", "caster sugar", "superfine sugar", "Zucker", "Kristallzucker", "Feinzucker"),
    BrownSugar(213.0, "brown sugar", "brauner Zucker"),
    PowderedSugar(113.0, "powdered sugar", "confectioners' sugar", "icing sugar", "Puderzucker"),
    Rice(198.0, "rice", "long-grain rice", "basmati rice", "jasmine rice", "Reis", "Langkornreis", "Basmatireis", "Jasminreis"),
    Oats(89.0, "oats", "Haferflocken"),
    Cocoa(84.0, "cocoa", "cocoa powder", "Kakao", "Kakaopulver", "Backkakao"),
    Salt(288.0, "salt", "table salt", "Salz", "Speisesalz", "Jodsalz"),
    BakingPowder(192.0, "baking powder", "Backpulver"),
    BakingSoda(288.0, "baking soda", "bicarbonate of soda", "Natron", "Speisenatron"),
    ChoppedNuts(
        113.0,
        "chopped nuts",
        "chopped walnuts",
        "chopped pecans",
        "gehackte Nüsse",
        "gehackte Walnüsse",
        "gehackte Walnusskerne",
    ),
    Almonds(142.0, "almonds", "whole almonds", "Mandeln", "ganze Mandeln"),
    SlicedAlmonds(86.0, "sliced almonds", "flaked almonds", "Mandelblättchen", "gehobelte Mandeln"),
    SliveredAlmonds(114.0, "slivered almonds", "Mandelstifte"),
    GratedCheese(
        113.0,
        "grated cheese",
        "grated cheddar",
        "grated cheddar cheese",
        "grated mozzarella",
        "grated mozzarella cheese",
        "grated swiss cheese",
        "grated monterey jack",
        "grated monterey jack cheese",
        "geriebener Käse",
        "Reibekäse",
        "geriebener Gouda",
        "geriebener Emmentaler",
        "geriebener Mozzarella",
    ),
    Parmesan(
        100.0,
        "parmesan",
        "parmesan cheese",
        "parmigiano reggiano",
        "grated parmesan",
        "grated parmesan cheese",
        "grated parmigiano reggiano",
        "geriebener Parmesan",
    ),
    ChocolateChips(170.0, "chocolate chips", "milk chocolate chips", "Schokotropfen", "Schokodrops", "Schokoladentropfen"),
    Cornstarch(112.0, "cornstarch", "corn starch", "cornflour", "Speisestärke", "Maisstärke"),
    Breadcrumbs(112.0, "breadcrumbs", "bread crumbs", "Semmelbrösel", "Paniermehl"),
    Panko(50.0, "panko", "panko breadcrumbs", "panko bread crumbs"),
    Butter(227.0, "butter"),
    Oil(218.0, "oil", "vegetable oil", "canola oil", "sunflower oil", "rapeseed oil", "Öl", "Pflanzenöl", "Rapsöl", "Sonnenblumenöl"),
    OliveOil(216.0, "olive oil", "Olivenöl"),
    Honey(339.0, "honey", "Honig"),
    MapleSyrup(315.0, "maple syrup", "Ahornsirup"),
    PeanutButter(258.0, "peanut butter", "Erdnussbutter"),
    Yogurt(245.0, "yogurt", "yoghurt", "Joghurt", "Naturjoghurt"),
    SourCream(230.0, "sour cream", "saure Sahne", "Sauerrahm", "Schmand"),
    CreamCheese(232.0, "cream cheese", "Frischkäse"),
    ;

    companion object {
        /** Words that change how a food lies in the cup. Others found after a comma or in a note are prep. */
        private val forms = listOf("chopped", "sliced", "slivered", "grated", "ground", "gehackt", "gehobelt", "gerieben", "gemahlen")

        private val neutral =
            setOf(
                "packed",
                "firmly",
                "lightly",
                "loosely",
                "unsifted",
                "soft",
                "softened",
                "melted",
                "cold",
                "chilled",
                "room",
                "temperature",
                "unsalted",
                "salted",
                "sweet",
                "semi",
                "semisweet",
                "bittersweet",
                "mini",
                "dark",
                "light",
                "white",
                "fine",
                "finely",
                "coarsely",
                "freshly",
                "plain",
                "pure",
                "natural",
                "organic",
                "raw",
                "uncooked",
                "dry",
                "dried",
                "toasted",
                "blanched",
                "unsweetened",
                "dutch",
                "process",
                "processed",
                "extra",
                "virgin",
                "neutral",
                "granulated",
                "unbleached",
                "bleached",
                "enriched",
                "old",
                "fashioned",
                "rolled",
                "quick",
                "cooking",
                "instant",
                "creamy",
                "smooth",
                "crunchy",
                "runny",
                "sharp",
                "type",
                "typ",
                "weiche",
                "kalte",
                "zimmerwarme",
                "geschmolzene",
                "zerlassene",
                "flüssige",
                "flüssiger",
                "weißer",
                "feiner",
                "feines",
                "ungesalzene",
                "gesalzene",
                "ungesüßtes",
                "ungesüßter",
                "zarte",
                "kernige",
                "natives",
                "neutrales",
                "blanchierte",
            )

        private val parentheses = Regex("""\([^)]*\)""")
        private val nonLetters = Regex("""\P{L}+""")

        private val byWords: Map<Set<String>, CupWeight> = entries.flatMap { weight -> weight.names.map { words(it) to weight } }.toMap()

        /**
         * The weight of [food], such as "light brown sugar, packed" or Mealie's food "walnuts" with
         * the [note] "chopped". Text after a comma and the note only add how the food is cut.
         */
        fun of(food: String, note: String = ""): CupWeight? {
            val main = food.replace(parentheses, " ").substringBefore(',')
            val cut = words("${food.substringAfter(',', "")} $note").filter { it in forms }
            return (words(main) + cut).takeIf { it.isNotEmpty() }?.let { byWords[it] }
        }

        private fun words(text: String): Set<String> =
            text
                .lowercase(Locale.ROOT)
                .replace("'", "")
                .replace("’", "")
                .split(nonLetters)
                .filter { it.isNotEmpty() && it !in neutral }
                .mapTo(mutableSetOf(), ::canonical)

        /** "Shredded" cheese is grated; German forms lose their endings: "geriebener" is "gerieben". */
        private fun canonical(word: String) = if (word == "shredded") "grated" else forms.firstOrNull { word.startsWith(it) } ?: word
    }
}

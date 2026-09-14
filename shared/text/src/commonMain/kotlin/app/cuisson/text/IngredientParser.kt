package app.cuisson.text

/**
 * What a parser managed to pull out of one ingredient line.
 *
 * Every field is allowed to be absent, because plenty of real lines are just "sel". None
 * of this is ever shown to the reader: the recipe always displays the line as it was
 * written. This exists so that two recipes asking for onions can be recognised as asking
 * for the same thing when a shopping list is built. See ADR-0004.
 */
data class ParsedIngredient(
    val quantityMin: Double? = null,
    val quantityMax: Double? = null,
    val unit: String? = null,
    val unitSystem: UnitKind = UnitKind.NONE,
    val altUnit: String? = null,
    val altSystem: UnitKind = UnitKind.NONE,
    val item: String? = null,
    val preparation: String? = null,
    val optional: Boolean = false,
    /**
     * How much of the line was accounted for, from 0 to 1. A line that gave up an amount,
     * a unit and an item is worth trusting; one that gave up only a name is a guess, and
     * the shopping list needs to know which it is holding.
     */
    val confidence: Float = 0f,
)

enum class UnitKind { METRIC, IMPERIAL, COUNT, NONE }

private data class Measure(val canonical: String, val kind: UnitKind)

/**
 * Units as they are written, and what they mean.
 *
 * Counting words earn their place here even though they measure nothing: "5 cloves
 * garlic" and "2 cans tomatoes" put a word between the number and the thing, and a parser
 * that misses it ends up believing the item is "cloves".
 */
private val UNITS: Map<String, Measure> = buildMap {
    fun put(kind: UnitKind, canonical: String, vararg written: String) {
        written.forEach { put(it, Measure(canonical, kind)) }
    }
    put(UnitKind.METRIC, "g", "g", "gr", "gram", "grams", "gramme", "grammes")
    put(UnitKind.METRIC, "kg", "kg", "kilo", "kilos", "kilogram", "kilograms", "kilogramme")
    put(UnitKind.METRIC, "mg", "mg")
    put(UnitKind.METRIC, "ml", "ml", "millilitre", "millilitres", "milliliter", "milliliters")
    put(UnitKind.METRIC, "cl", "cl")
    put(UnitKind.METRIC, "l", "l", "litre", "litres", "liter", "liters")
    put(UnitKind.IMPERIAL, "oz", "oz", "ounce", "ounces")
    put(UnitKind.IMPERIAL, "lb", "lb", "lbs", "pound", "pounds")
    put(UnitKind.IMPERIAL, "cup", "cup", "cups", "tasse", "tasses")
    put(UnitKind.IMPERIAL, "tsp", "tsp", "tsps", "teaspoon", "teaspoons")
    put(UnitKind.IMPERIAL, "tbsp", "tbsp", "tbsps", "tbs", "tablespoon", "tablespoons")
    put(UnitKind.IMPERIAL, "qt", "qt", "quart", "quarts")
    put(UnitKind.IMPERIAL, "pt", "pt", "pint", "pints")
    put(UnitKind.COUNT, "clove", "clove", "cloves", "gousse", "gousses")
    put(UnitKind.COUNT, "can", "can", "cans", "tin", "tins", "boite", "boites")
    put(UnitKind.COUNT, "stick", "stick", "sticks", "baton", "batons")
    put(UnitKind.COUNT, "sprig", "sprig", "sprigs", "brin", "brins")
    put(UnitKind.COUNT, "bunch", "bunch", "bunches", "botte", "bottes")
    put(UnitKind.COUNT, "head", "head", "heads")
    put(UnitKind.COUNT, "slice", "slice", "slices", "tranche", "tranches")
    put(UnitKind.COUNT, "piece", "piece", "pieces", "morceau", "morceaux")
    put(UnitKind.COUNT, "stalk", "stalk", "stalks", "tige", "tiges")
    put(UnitKind.COUNT, "ball", "ball", "balls", "boule", "boules")
    put(UnitKind.COUNT, "packet", "packet", "packets", "sachet", "sachets", "package")
    put(UnitKind.NONE, "pinch", "pinch", "pinches", "pincee", "pincees")
    put(UnitKind.NONE, "handful", "handful", "handfuls", "poignee", "poignees")
    put(UnitKind.NONE, "drizzle", "drizzle", "filet")
}

/** Written as several words, so they have to be tried before the single-word lookup. */
private val UNIT_PHRASES: List<Pair<String, Measure>> = listOf(
    "cuillere a soupe" to Measure("tbsp", UnitKind.IMPERIAL),
    "cuilleres a soupe" to Measure("tbsp", UnitKind.IMPERIAL),
    "c a soupe" to Measure("tbsp", UnitKind.IMPERIAL),
    "cuillere a cafe" to Measure("tsp", UnitKind.IMPERIAL),
    "cuilleres a cafe" to Measure("tsp", UnitKind.IMPERIAL),
    "c a cafe" to Measure("tsp", UnitKind.IMPERIAL),
    // Quebec says "a the" where France says "a cafe", and Ricardo is Quebecois.
    "cuillere a the" to Measure("tsp", UnitKind.IMPERIAL),
    "cuilleres a the" to Measure("tsp", UnitKind.IMPERIAL),
    "c a the" to Measure("tsp", UnitKind.IMPERIAL),
    "fl oz" to Measure("floz", UnitKind.IMPERIAL),
)

private val CONNECTORS = setOf("of", "de", "du", "des", "d", "a", "the")

private val OPTIONAL_MARKS = listOf(
    "optional", "facultatif", "facultative", "to taste", "au gout", "si desire",
)

/**
 * Reads one ingredient line into its pieces.
 *
 * Deliberately shallow. It finds the amount, the unit and the name, and gives up on
 * anything cleverer rather than guessing: a line it cannot read returns a low confidence
 * and the raw text, which the shopping list can show to a person instead of pretending to
 * have understood it.
 */
fun parseIngredient(line: String): ParsedIngredient {
    val text = line.trim()
    if (text.isEmpty()) return ParsedIngredient()

    val optional = OPTIONAL_MARKS.any { fold(text).contains(it) }

    val amount = readAmountAt(text, 0)?.takeIf { text.take(it.where.first).isBlank() }
    var at = amount?.let { it.where.last + 1 } ?: 0

    val unit = readMeasureAt(text, at)
    if (unit != null) at = unit.second

    // A conversion right after the unit, "3 tbsp (45 ml)", tells us the same amount in
    // the other system. Worth keeping: a shopping list adding up millilitres does not
    // want to convert tablespoons itself.
    var alt: Measure? = null
    if (unit != null) {
        val conversion = readConversion(text, at)
        if (conversion != null) {
            alt = conversion.first
            at = conversion.second
        }
    }

    val rest = text.substring(at.coerceAtMost(text.length))
    val (item, preparation) = splitItem(rest)

    val confidence = when {
        amount != null && unit != null && !item.isNullOrBlank() -> 1f
        amount != null && !item.isNullOrBlank() -> 0.8f
        !item.isNullOrBlank() -> 0.5f
        else -> 0f
    }

    return ParsedIngredient(
        quantityMin = amount?.value,
        quantityMax = amount?.upper ?: amount?.value,
        unit = unit?.first?.canonical,
        unitSystem = unit?.first?.kind ?: UnitKind.NONE,
        altUnit = alt?.canonical,
        altSystem = alt?.kind ?: UnitKind.NONE,
        item = item,
        preparation = preparation,
        optional = optional,
        confidence = confidence,
    )
}

/** The unit at [at], as a canonical measure and the position just past it. */
private fun readMeasureAt(line: String, at: Int): Pair<Measure, Int>? {
    val tail = line.substring(at.coerceAtMost(line.length))
    val folded = fold(tail).trimStart()
    val skipped = tail.length - tail.trimStart().length

    UNIT_PHRASES.firstOrNull { (phrase, _) ->
        folded.startsWith(phrase) &&
            (folded.length == phrase.length || !folded[phrase.length].isLetter())
    }?.let { (phrase, measure) ->
        return measure to at + skipped + phrase.length
    }

    val word = folded.takeWhile { it.isLetter() }
    if (word.isEmpty()) return null
    val measure = UNITS[word] ?: return null
    var end = at + skipped + word.length
    if (line.getOrNull(end) == '.') end++
    return measure to end
}

/** "(45 ml)" or "/ 1 kg" straight after a unit: the same amount in the other system. */
private fun readConversion(line: String, at: Int): Pair<Measure, Int>? {
    var i = at
    while (i < line.length && line[i] == ' ') i++
    val opener = line.getOrNull(i) ?: return null
    if (opener != '(' && opener != '/') return null
    val amount = readAmountAt(line, i + 1) ?: return null
    if (line.substring(i + 1, amount.where.first).isNotBlank()) return null
    val unit = readMeasureAt(line, amount.where.last + 1) ?: return null
    var end = unit.second
    if (opener == '(') {
        val close = line.indexOf(')', end)
        if (close < 0) return null
        end = close + 1
    }
    return unit.first to end
}

/**
 * The name of the thing, and what the recipe wants done to it.
 *
 * Brackets go first, because a comma inside one is not the comma that divides a line:
 * "oil (vegetable, canola or peanut oil)" was being read as a thing called "oil
 * (vegetable".
 *
 * Then the comma, but only where it is really dividing. Recipe writers use it both ways.
 * "small onion, finely chopped" is a name and an instruction; "boneless, skinless chicken
 * breasts" is one name with a comma in it, and splitting that gives a shopping list an
 * item called "boneless". Two signals settle it: a tail that reads like an instruction,
 * or a head long enough to be a name on its own.
 */
private fun splitItem(rest: String): Pair<String?, String?> {
    var body = rest.trim()
    // Leading connector: "of mushrooms", "de lardons".
    val firstWord = body.takeWhile { it.isLetter() }
    if (fold(firstWord) in CONNECTORS) body = body.drop(firstWord.length).trimStart()

    body = body
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    val comma = body.indexOf(',')
    val head = if (comma >= 0) body.take(comma).trim() else body
    val tail = if (comma >= 0) body.drop(comma + 1).trim() else ""

    val divides = comma >= 0 &&
        (looksLikeInstruction(tail) || head.split(" ").size >= 2)

    val name = (if (divides) head else body)
        .trim()
        .trim('-', '.', ':', ';')
        .trim()

    return name.ifBlank { null } to tail.takeIf { divides && it.isNotBlank() }
}

private val PREPARATION_WORDS = setOf(
    "finely", "roughly", "thinly", "coarsely", "freshly", "lightly", "well",
    "to", "for", "plus", "at", "cut", "divided", "optional", "or",
)

/**
 * Whether this reads as something done to the ingredient rather than more of its name.
 *
 * A past participle is the giveaway in both languages, and the handful of adverbs that
 * introduce one covers most of the rest.
 */
private fun looksLikeInstruction(tail: String): Boolean {
    val words = fold(tail).split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    if (words.first() in PREPARATION_WORDS) return true
    return words.take(2).any { word ->
        word.endsWith("ed") || word.endsWith("ee") || word.endsWith("es") && word.length > 4
    }
}

/** Lowercase and stripped of accents, so "pincée" and "pincee" are the same word. */
private fun fold(text: String): String = text.lowercase()
    .map { ACCENTS[it] ?: it }
    .joinToString("")

private val ACCENTS: Map<Char, Char> = buildMap {
    "àâäá".forEach { put(it, 'a') }
    "èéêë".forEach { put(it, 'e') }
    "ìíîï".forEach { put(it, 'i') }
    "òóôö".forEach { put(it, 'o') }
    "ùúûü".forEach { put(it, 'u') }
    put('ç', 'c')
    put('’', '\'')
}

package app.cuisson.text

/**
 * Units of weight and volume, which are the only ones that make a bracket a conversion.
 *
 * "3 tbsp (45 ml) olive oil" states one amount twice, so doubling has to move both or the
 * line argues with itself. "1 (14-ounce) can diced tomatoes" states an amount and then the
 * size of the tin, and doubling that gives two tins of the same size. The difference is
 * whether a unit of measure stands between the number and the bracket, which is why this
 * list holds no counting words: a tin, a clove and a stick are things, not measurements.
 */
private val MEASURES = setOf(
    "g", "gr", "gram", "grams", "gramme", "grammes",
    "kg", "kilo", "kilos", "kilogram", "kilograms", "kilogramme", "kilogrammes",
    "mg", "ml", "millilitre", "millilitres", "milliliter", "milliliters",
    "cl", "l", "litre", "litres", "liter", "liters",
    "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
    "cup", "cups", "tasse", "tasses",
    "tsp", "tsps", "teaspoon", "teaspoons",
    "tbsp", "tbsps", "tbs", "tablespoon", "tablespoons",
    "qt", "quart", "quarts", "pt", "pint", "pints", "gal", "gallon", "gallons",
    "cuillere", "cuilleres", "cuillère", "cuillères",
)

/**
 * Rewrites a line's quantities for a different number of servings.
 *
 * Only the characters holding a number are replaced. Everything else, the wording, the
 * preparation, the note references, the author's punctuation, is left exactly as it was
 * published, which is ADR-0004 applied at display time. A line with no number, "salt" or
 * "muscade", comes back untouched, which is right: salt does not double.
 *
 * What is deliberately not scaled: any number past the opening quantity that is not a
 * conversion of it. "cut into 4cm cubes" is a size, "(Note 4)" is a reference, and a
 * recipe doubled does not want 8cm cubes. The cost is that a line like "400ml coconut
 * milk (1 standard can)" still says one can after doubling. Getting that right would mean
 * guessing which trailing numbers are amounts, and a wrong guess in an ingredient list is
 * worse than a number left alone: the reader can see the line, and the scale is on screen
 * above it.
 */
fun scaleIngredient(line: String, factor: Double): String {
    if (factor == 1.0 || line.isBlank()) return line
    val opening = readAmountAt(line, 0) ?: return line
    // The quantity has to open the line. A number further in is describing something.
    if (line.take(opening.where.first).isNotBlank()) return line

    val quantity = readQuantityAt(line, 0) ?: return line
    val marked = quantity.amounts.toMutableList()

    // "or 12 large fresh" offers the same amount another way, and a doubled line that
    // says "24 dried chillies or 12 large fresh" tells one of the two readers a lie.
    readAlternativeAfter(line, quantity.end)?.let { marked += it.amounts }

    // Right to left, so the earlier positions stay where they were.
    var out = line
    marked.sortedByDescending { it.where.first }.forEach { amount ->
        out = out.substring(0, amount.where.first) +
            scaledText(amount, factor) +
            out.substring(amount.where.last + 1)
    }
    return out
}

/** Every amount belonging to one stated quantity, and where that statement ends. */
private data class Quantity(val amounts: List<Amount>, val end: Int)

/**
 * Reads one quantity and everything that restates it.
 *
 * A quantity is rarely just a number. It can be written in two parts, "1½ cups plus
 * 1 Tbsp."; it can be given again in another system, "¾ lb (340 g)" or "2 lb/ 1 kg"; and
 * the second telling can use words this code knows nothing about, "¾ cup (1½ sticks;
 * 169 g)". All of it moves together or the line ends up arguing with itself.
 *
 * The guard against reading too much is adjacency. A bracket counts as a restatement only
 * when it follows the unit immediately, which is what separates "6 oz. (170 g) chocolate"
 * from the "(60%–70% cacao)" later in the same line, and "¾ lb (340 g) beef" from
 * "1 (14-ounce) can", where the number has no unit and the bracket is the size of a tin.
 */
private fun readQuantityAt(line: String, from: Int): Quantity? {
    val first = readAmountAt(line, from) ?: return null
    val found = mutableListOf(first)
    var at = first.where.last + 1

    val unit = readUnitAt(line, at) ?: return Quantity(found, at)
    at = unit.last + 1

    // "1½ cups plus 1 Tbsp." is one amount written in two pieces.
    while (true) {
        val joiner = Regex("""^\s*(?:plus|and|\+|et)\s+""").find(line.substring(at)) ?: break
        val more = readAmountAt(line, at + joiner.value.length) ?: break
        if (more.where.first != at + joiner.value.length) break
        found += more
        at = more.where.last + 1
        readUnitAt(line, at)?.let { at = it.last + 1 }
    }

    // The same amount said again, in a bracket or after a slash.
    while (true) {
        val restated = readRestatementAt(line, at) ?: break
        found += restated.amounts
        at = restated.end
    }

    return Quantity(found, at)
}

/**
 * A restatement of the amount just read: "(340 g)", "(1½ sticks; 169 g)", "/ 1 kg".
 *
 * Everything numeric inside the bracket moves, because a bracket sitting against a
 * measured amount exists to say that amount over again and every number in it is part of
 * that. A bracket with no numbers, "(packed)", contributes nothing and is skipped.
 */
private fun readRestatementAt(line: String, at: Int): Quantity? {
    var i = at
    while (i < line.length && line[i] == ' ') i++
    return when (line.getOrNull(i)) {
        '(' -> {
            val close = line.indexOf(')', i)
            if (close < 0) return null
            val inside = mutableListOf<Amount>()
            var j = i + 1
            while (j < close) {
                val amount = readAmountAt(line, j)
                if (amount == null || amount.where.last >= close) break
                inside += amount
                j = amount.where.last + 1
                // Step over whatever word follows before looking for the next number.
                while (j < close && line[j] != ' ') j++
                while (j < close && line[j] == ' ') j++
                if (j < close && !line[j].isDigit() && VULGAR_CHARS.none { it == line[j] }) {
                    while (j < close && line[j] != ' ') j++
                }
            }
            if (inside.isEmpty()) null else Quantity(inside, close + 1)
        }
        '/' -> {
            val amount = readAmountAt(line, i + 1) ?: return null
            if (line.substring(i + 1, amount.where.first).isNotBlank()) return null
            val unit = readUnitAt(line, amount.where.last + 1) ?: return null
            Quantity(listOf(amount), unit.last + 1)
        }
        else -> null
    }
}

private val VULGAR_CHARS = "½¼¾⅓⅔⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞".toSet()

/**
 * The same amount offered a different way, after "or".
 *
 * The number has to come straight after the word. "or tamarind pulp soaked in 1 tbsp of
 * hot water" also has a number after an "or", and it is a method rather than an
 * alternative amount: doubling it would tell someone to soak in twice the water.
 */
private fun readAlternativeAfter(line: String, from: Int): Quantity? {
    if (from >= line.length) return null
    val mark = Regex("""\b(?:or|ou)\s+""").find(line, from) ?: return null
    return readQuantityAt(line, mark.range.last + 1)
        ?.takeIf { it.amounts.first().where.first == mark.range.last + 1 }
}

private fun scaledText(amount: Amount, factor: Double): String {
    val lower = formatAmount(amount.value * factor, amount.commaDecimal)
    val upper = amount.upper ?: return lower
    return lower + (amount.rangeSeparator ?: " to ") +
        formatAmount(upper * factor, amount.commaDecimal)
}

/** Words that sit between the number and its unit and mean nothing: "1/4 de tasse". */
private val CONNECTORS = setOf("of", "de", "du", "des", "d")

/**
 * The unit of measure sitting at [at], if that is what is there.
 *
 * One connector is stepped over, because "1/4 de tasse (65 ml)" states a conversion just
 * as plainly as "1/4 cup (65 ml)" does, and the word in between should not hide it.
 */
private fun readUnitAt(line: String, at: Int): IntRange? =
    readWordAt(line, at)?.let { (word, range) ->
        when {
            word in MEASURES -> range
            word in CONNECTORS -> readWordAt(line, range.last + 1)
                ?.takeIf { it.first in MEASURES }
                ?.second
            else -> null
        }
    }

private fun readWordAt(line: String, at: Int): Pair<String, IntRange>? {
    var i = at
    while (i < line.length && (line[i] == ' ' || line[i] == '\'' || line[i] == '\u2019')) i++
    val start = i
    while (i < line.length && line[i].isLetter()) i++
    if (i == start) return null
    val end = if (line.getOrNull(i) == '.') i + 1 else i
    return line.substring(start, i).lowercase() to (start until end)
}

/**
 * The same amount written again in another system, straight after the first.
 *
 * Two shapes, both common: a slash, as in "2 lb/ 1 kg", and a bracket, as in "¾ lb
 * (340 g)". Either way it has to be followed by a unit of measure, or it is not a
 * conversion of anything.
 */
private fun readConversionAt(line: String, at: Int): Amount? {
    var i = at
    while (i < line.length && line[i] == ' ') i++
    val opener = line.getOrNull(i) ?: return null
    if (opener != '/' && opener != '(') return null
    val amount = readAmountAt(line, i + 1) ?: return null
    if (line.substring(i + 1, amount.where.first).isNotBlank()) return null
    return if (readUnitAt(line, amount.where.last + 1) != null) amount else null
}

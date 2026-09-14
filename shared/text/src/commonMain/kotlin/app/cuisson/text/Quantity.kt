package app.cuisson.text

/**
 * A number as a recipe writes it.
 *
 * Recipes write the same amount half a dozen ways, and all of them turn up in one
 * library: "1 1/2", "1½", "½", "0.5", "1,5" and "2 to 3". [text] is the span exactly as
 * it was written and [where] is its place in the line, because scaling replaces those
 * characters and leaves everything else untouched. Rebuilding the line from its pieces is
 * how "5 garlic cloves" becomes "5 clove garlic". See ADR-0004.
 */
data class Amount(
    val value: Double,
    val text: String,
    val where: IntRange,
    /** The upper end when the writer gave a range, as in "2 to 3 onions". */
    val upper: Double? = null,
    /** True when the line wrote its decimals with a comma, as French recipes do. */
    val commaDecimal: Boolean = false,
    /** Whatever the writer put between the two ends of a range: " - ", " to ", " a ". */
    val rangeSeparator: String? = null,
)

private val VULGAR = mapOf(
    '½' to 0.5, '¼' to 0.25, '¾' to 0.75,
    '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
    '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8,
    '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
    '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
)

/**
 * Reads the number starting at [from], if there is one.
 *
 * Handles a whole number, a decimal written with either separator, a fraction, a mixed
 * number written either with a space or glued to a vulgar fraction, and a range. A range
 * keeps its lower end as the value, because "30 to 40 minutes" means check at thirty and
 * "2 to 3 onions" means two will do.
 */
fun readAmountAt(line: String, from: Int = 0): Amount? {
    var i = from
    while (i < line.length && line[i] == ' ') i++
    val start = i
    if (i >= line.length) return null

    var whole: Double? = null
    var comma = false

    // A bare fraction opening the line, "1/4 de tasse". Tried before the digit run,
    // because reading the numerator as a whole number turns a quarter into a one and the
    // rest of the line then looks like it has no unit.
    readFractionAt(line, i)?.let { (value, end) ->
        return finishAmount(line, value, start, end, comma = false)
    }

    // A leading digit run, which may turn out to be the whole part of a mixed number.
    val digits = StringBuilder()
    while (i < line.length && line[i].isDigit()) digits.append(line[i++])
    if (digits.isNotEmpty()) {
        // A decimal point or a decimal comma. Three digits after a comma is a thousands
        // separator in English, not a decimal, so it is left alone.
        if (i + 1 < line.length && (line[i] == '.' || line[i] == ',') && line[i + 1].isDigit()) {
            val after = StringBuilder()
            var j = i + 1
            while (j < line.length && line[j].isDigit()) after.append(line[j++])
            if (!(line[i] == ',' && after.length >= 3)) {
                comma = line[i] == ','
                digits.append('.').append(after)
                i = j
            }
        }
        whole = digits.toString().toDoubleOrNull() ?: return null
    }

    var value = whole
    // "1 1/2", "1½", "½", "1/2".
    val afterWhole = i
    var j = i
    if (whole != null) while (j < line.length && line[j] == ' ') j++

    val vulgar = line.getOrNull(j)?.let(VULGAR::get)
    if (vulgar != null) {
        value = (whole ?: 0.0) + vulgar
        i = j + 1
    } else if (j < line.length && line[j].isDigit()) {
        // Only a fraction here, not the next number in the line: it must have a slash.
        val slash = readFractionAt(line, j)
        if (slash != null) {
            value = (whole ?: 0.0) + slash.first
            i = slash.second
        } else if (whole != null) {
            i = afterWhole
        }
    } else if (whole == null) {
        val only = readFractionAt(line, j) ?: return null
        value = only.first
        i = only.second
    }

    if (value == null) return null
    return finishAmount(line, value, start, i, comma)
}

/** Adds the upper end of a range, when the writer gave one, and packages the result. */
private fun finishAmount(
    line: String,
    value: Double,
    start: Int,
    from: Int,
    comma: Boolean,
): Amount {
    var upper: Double? = null
    var separator: String? = null
    var end = from
    val rangeMark = Regex("""^\s*(?:[-–]|to\b|or\b|ou\b|à)\s*""")
        .find(line.substring(from))
    if (rangeMark != null) {
        val next = readAmountAt(line, from + rangeMark.value.length)
        if (next != null && next.where.first == from + rangeMark.value.length) {
            upper = next.value
            separator = rangeMark.value
            end = next.where.last + 1
        }
    }
    return Amount(
        value = value,
        text = line.substring(start, end),
        where = start until end,
        upper = upper,
        commaDecimal = comma,
        rangeSeparator = separator,
    )
}

/** "3/4" and the position just past it, or null when this is not a fraction. */
private fun readFractionAt(line: String, at: Int): Pair<Double, Int>? {
    val match = Regex("""^(\d{1,3})\s*/\s*(\d{1,3})""").find(line.substring(at)) ?: return null
    val top = match.groupValues[1].toDouble()
    val bottom = match.groupValues[2].toDouble()
    if (bottom == 0.0) return null
    return top / bottom to at + match.value.length
}

private val FRACTIONS = listOf(
    1 to 2, 1 to 3, 2 to 3, 1 to 4, 3 to 4,
    1 to 8, 3 to 8, 5 to 8, 7 to 8,
    1 to 6, 5 to 6,
)

/**
 * Writes an amount the way a person would.
 *
 * Fractions rather than decimals wherever one is close enough, because "1 1/2 tbsp" is
 * something you can measure and "1.5 tbsp" is something you have to think about. Anything
 * that is not near a familiar fraction falls back to at most two decimal places, with the
 * separator the line was written with.
 */
fun formatAmount(value: Double, commaDecimal: Boolean = false): String {
    if (value <= 0) return "0"
    val whole = kotlin.math.floor(value + 1e-9).toInt()
    val rest = value - whole

    if (rest < 1e-6) return whole.toString()

    FRACTIONS.firstOrNull { (top, bottom) ->
        kotlin.math.abs(rest - top.toDouble() / bottom) < 0.01
    }?.let { (top, bottom) ->
        return if (whole == 0) "$top/$bottom" else "$whole $top/$bottom"
    }

    val rounded = (value * 100).toLong() / 100.0
    val text = rounded.toString().removeSuffix(".0").let {
        if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it
    }
    return if (commaDecimal) text.replace('.', ',') else text
}

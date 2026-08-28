package app.cuisson.importer

/**
 * Reads the ISO 8601 durations schema.org uses for prepTime, cookTime and totalTime.
 *
 * Publishers do not normalise these. Observed in the wild: PT1H15M, PT2H5M, and PT200M
 * for a recipe that takes three and a half hours. Days appear occasionally on things that
 * prove or cure, so P2DT3H is handled too.
 *
 * Returns null rather than guessing when the string is not a duration.
 */
internal fun parseIsoDurationMinutes(raw: String?): Int? {
    val text = raw?.trim()?.uppercase() ?: return null
    if (!text.startsWith("P")) return null

    val body = text.removePrefix("P")
    val datePart = body.substringBefore("T", if (body.contains("T")) "" else body)
    val timePart = if (body.contains("T")) body.substringAfter("T") else ""

    var minutes = 0
    var matchedAnything = false

    fun scan(part: String, units: Map<Char, Int>) {
        var number = StringBuilder()
        for (character in part) {
            if (character.isDigit() || character == '.') {
                number.append(character)
            } else {
                val value = number.toString().toDoubleOrNull()
                val perUnit = units[character]
                if (value != null && perUnit != null) {
                    minutes += (value * perUnit).toInt()
                    matchedAnything = true
                }
                number = StringBuilder()
            }
        }
    }

    scan(datePart, mapOf('D' to 24 * 60, 'W' to 7 * 24 * 60, 'Y' to 365 * 24 * 60))
    scan(timePart, mapOf('H' to 60, 'M' to 1, 'S' to 0))

    return if (matchedAnything) minutes else null
}

package app.cuisson.importer

/**
 * Reads the durations publishers put in prepTime, cookTime and totalTime.
 *
 * schema.org specifies ISO 8601 and plenty of sites ignore it, so both forms are handled.
 * Everything here came from looking at what real sites emit:
 *
 *  - PT1H15M and PT2H5M, as specified
 *  - PT200M for a recipe that takes three and a half hours, never normalised
 *  - PT2400S, expressed entirely in seconds
 *  - "35 minutes" and "20 minutes", plain English, no ISO anywhere in sight
 *
 * Returns null rather than guessing, because a wrong cooking time is worse than none.
 */
internal fun parseDurationMinutes(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return parseIsoDurationMinutes(text) ?: parseHumanDurationMinutes(text)
}

internal fun parseIsoDurationMinutes(raw: String?): Int? {
    val text = raw?.trim()?.uppercase() ?: return null
    if (!text.startsWith("P")) return null

    val body = text.removePrefix("P")
    val hasTime = body.contains("T")
    val datePart = if (hasTime) body.substringBefore("T") else body
    val timePart = if (hasTime) body.substringAfter("T") else ""

    var seconds = 0L
    var matched = false

    fun scan(part: String, units: Map<Char, Long>) {
        var number = StringBuilder()
        for (character in part) {
            if (character.isDigit() || character == '.') {
                number.append(character)
            } else {
                val value = number.toString().toDoubleOrNull()
                val perUnit = units[character]
                if (value != null && perUnit != null) {
                    seconds += (value * perUnit).toLong()
                    matched = true
                }
                number = StringBuilder()
            }
        }
    }

    scan(datePart, mapOf('D' to 86_400L, 'W' to 604_800L, 'Y' to 31_536_000L))
    scan(timePart, mapOf('H' to 3_600L, 'M' to 60L, 'S' to 1L))

    if (!matched) return null
    // Seconds are floored into minutes rather than rounded, so PT30M45S stays 30. But a
    // duration given only in seconds, as PT2400S, must not collapse to nothing.
    return (seconds / 60).toInt().takeIf { it > 0 }
}

/**
 * "35 minutes", "1 hour 15 minutes", "1 h 30", "1 heure 30 minutes".
 *
 * Deliberately narrow. It matches a number followed by a unit it recognises in English or
 * French, and ignores everything else, so a sentence that merely mentions a number does
 * not become a cooking time.
 */
internal fun parseHumanDurationMinutes(raw: String): Int? {
    val text = raw.lowercase()
    var minutes = 0
    var matched = false

    HUMAN_DURATION.findAll(text).forEach { match ->
        val value = match.groupValues[1].toIntOrNull() ?: return@forEach
        val unit = match.groupValues[2]
        minutes += when {
            unit.startsWith("h") -> value * 60
            unit.startsWith("j") || unit.startsWith("d") -> value * 60 * 24
            else -> value
        }
        matched = true
    }

    if (!matched) return null
    // A bare "1 h 30" leaves the 30 unlabelled, so pick it up when it follows an hour.
    TRAILING_MINUTES.find(text)?.let { minutes += it.groupValues[1].toIntOrNull() ?: 0 }
    return minutes.takeIf { it > 0 }
}

private val HUMAN_DURATION = Regex(
    """(\d+)\s*(heures?|hours?|hrs?|h|minutes?|mins?|m|jours?|days?|d)\b"""
)

/** "1 h 30" and "1h30": the trailing number is minutes with no unit of its own. */
private val TRAILING_MINUTES = Regex("""\d+\s*(?:h|heures?|hours?|hrs?)\s*(\d{1,2})(?!\s*\d)(?!\s*(?:m|min))""")

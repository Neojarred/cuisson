package app.cuisson.text

/**
 * Finds the cooking time a step tells you to wait.
 *
 * "Simmer gently for 35 minutes" should offer a timer for thirty-five minutes. The point
 * is a step you wait through, so this looks for a duration that follows a word about
 * waiting, and ignores the rest.
 *
 * Deliberately narrow. "Cut into 4 cm cubes" and "180°C for 1 hour" are different kinds
 * of number, and offering a timer for the wrong one is worse than offering none: someone
 * sets it, walks away, and comes back to a burnt pan.
 */
fun durationInStep(text: String): Int? {
    val match = WAITING.find(text) ?: return null
    var seconds = secondsOf(match.groupValues[1], match.groupValues[2]) ?: return null

    // "1 hr 15 minutes" is one duration, not an hour followed by a coincidence. A second
    // amount straight after the first, in a smaller unit, belongs to the same wait.
    val rest = text.substring(match.range.last + 1)
    CONTINUATION.find(rest)?.let { more ->
        if (more.range.first <= 2) {
            secondsOf(more.groupValues[1], more.groupValues[2])
                ?.takeIf { it < seconds }
                ?.let { seconds += it }
        }
    }

    return seconds.takeIf { it in 30..(12 * 3600) }
}

private fun secondsOf(amount: String, unit: String): Int? {
    val value = amount.toIntOrNull() ?: return null
    return when {
        unit.lowercase().startsWith("h") -> value * 3600
        unit.lowercase().startsWith("s") -> value
        else -> value * 60
    }
}

private val CONTINUATION = Regex(
    "^\\s*(\\d{1,3})\\s*(hours?|hrs?|h|minutes?|mins?|min|seconds?|secs?)\\b",
    RegexOption.IGNORE_CASE,
)

/**
 * A word about waiting, then a number, then a unit of time. The number may be a range,
 * in which case the lower end wins.
 */
private val WAITING = Regex(
    "(?:simmer|cook|bake|boil|roast|rest|prove|proof|chill|marinate|fry|saute|steam|" +
        "leave|stand|soak|reduce|braise|knead|infuse|mijoter|reposer|laisser|cuire)" +
        "[^.!?]{0,60}?(\\d{1,3})(?:\\s*[-\u2013]\\s*\\d{1,3})?\\s*" +
        "(hours?|hrs?|h|minutes?|mins?|min|seconds?|secs?|heures?)\\b",
    RegexOption.IGNORE_CASE,
)

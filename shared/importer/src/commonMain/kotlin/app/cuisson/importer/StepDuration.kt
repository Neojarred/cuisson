package app.cuisson.importer

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
internal fun durationInStep(text: String): Int? {
    val match = WAITING.find(text) ?: return null
    val amount = match.groupValues[1].toIntOrNull() ?: return null
    // "30 - 40 minutes" means check at thirty, so the first number is the one to set.
    val unit = match.groupValues[2].lowercase()
    val seconds = when {
        unit.startsWith("h") -> amount * 3600
        unit.startsWith("s") -> amount
        else -> amount * 60
    }
    return seconds.takeIf { it in 30..(12 * 3600) }
}

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

package app.cuisson.text

/**
 * Breaks a long method step into cards for Cook Mode.
 *
 * Some publishers write a step as a dense paragraph holding five separate actions. Bon
 * Appetit does it as a matter of house style. Read on a phone at the hob, that is a wall
 * of text you lose your place in halfway down.
 *
 * The split happens here, at the moment of display, and never at import: rewriting the
 * author's structure into the database would contradict ADR-0004 and could not be undone.
 * Turning splitting off restores exactly what they wrote.
 *
 * The rule is deliberately reluctant. A step is only split if it is long enough to be a
 * problem, and the pieces are only kept apart if each is substantial. Breaking
 * "Add the flour. Stir." into two cards would be worse than the wall of text.
 */
object StepSplitter {

    fun split(step: String, enabled: Boolean = true): List<String> {
        val text = step.trim()
        if (!enabled || text.length <= LONG_ENOUGH_TO_SPLIT) return listOf(text)

        val sentences = sentences(text)
        if (sentences.size < 2) return listOf(text)

        // Short sentences join the one before them, so an instruction and its aside stay
        // together: "Simmer for 20 minutes. Do not stir." is one action, not two.
        val cards = mutableListOf<StringBuilder>()
        sentences.forEach { sentence ->
            val last = cards.lastOrNull()
            if (last != null && (last.length < SUBSTANTIAL || sentence.length < SUBSTANTIAL)) {
                last.append(' ').append(sentence)
            } else {
                cards += StringBuilder(sentence)
            }
        }

        val result = cards.map { it.toString().trim() }
        return if (result.size < 2) listOf(text) else result
    }

    /**
     * Splits on sentence endings, but not on the full stops inside "1.5", "Tbsp." or
     * "180°C.", which are not ends of sentences.
     */
    private fun sentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val character = text[index]
            current.append(character)
            if (character in ".!?") {
                val next = text.getOrNull(index + 1)
                val after = text.getOrNull(index + 2)
                val endsHere = (next == null || next == ' ') &&
                    (after == null || after.isUpperCase() || after.isDigit()) &&
                    !endsWithAbbreviation(current)
                if (endsHere) {
                    out += current.toString().trim()
                    current.clear()
                    index++
                    continue
                }
            }
            index++
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out.filter { it.isNotEmpty() }
    }

    private fun endsWithAbbreviation(soFar: StringBuilder): Boolean {
        val word = soFar.toString().trimEnd('.').takeLastWhile { !it.isWhitespace() }
        return word.lowercase() in ABBREVIATIONS || (word.length == 1 && word[0].isLetter())
    }

    private val ABBREVIATIONS = setOf(
        "tbsp", "tsp", "oz", "lb", "lbs", "approx", "min", "mins", "hr", "hrs",
        "no", "etc", "e.g", "i.e", "c", "f", "g", "kg", "ml", "cl", "l", "cm",
    )

    /** Below this a step is one screenful already. */
    private const val LONG_ENOUGH_TO_SPLIT = 180

    /** A card shorter than this is an aside rather than an action. */
    private const val SUBSTANTIAL = 60
}

package app.cuisson.importer

/**
 * Trims the search-engine tail off a recipe title.
 *
 * Publishers write titles for Google rather than for a phone screen, so a lasagne
 * arrives as "Lasagnes a la bolognaise : la meilleure recette" and a quiche as "Quiche
 * lorraine maison : la meilleure recette". In a list of forty recipes that suffix is on
 * every row and tells the reader nothing.
 *
 * Deliberately rule-based rather than a model. The junk is formulaic, the rules run
 * offline at no cost, and the original is kept so a bad trim is visible and reversible.
 */
internal object TitleTidy {

    fun tidy(raw: String, sourceHost: String? = null): String {
        var title = raw.trim()

        // "Chicken tikka masala | BBC Good Food". Everything after the pipe is the site.
        title.indexOf(" | ").takeIf { it > 0 }?.let { title = title.substring(0, it) }

        // A separator followed by a phrase that is pure marketing.
        for (separator in SEPARATORS) {
            val at = title.lastIndexOf(separator)
            if (at <= 0) continue
            val tail = title.substring(at + separator.length).trim()
            if (isJunkTail(tail, sourceHost)) {
                title = title.substring(0, at).trim()
            }
        }

        // "Brioche perdue facile et rapide" with nothing in front of the boast.
        for (suffix in TRAILING_BOASTS) {
            if (title.length > suffix.length + 3 && title.endsWith(suffix, ignoreCase = true)) {
                title = title.dropLast(suffix.length).trim().trimEnd(',', '-', ':')
            }
        }

        return title.ifBlank { raw.trim() }
    }

    private fun isJunkTail(tail: String, sourceHost: String?): Boolean {
        if (tail.isEmpty() || tail.split(" ").size > 5) return false
        val lower = tail.lowercase()
        if (JUNK_TAILS.any { lower == it || lower.startsWith("$it ") }) return true

        // "Beef wellington - Great British Chefs": the tail is the site's own name.
        val host = sourceHost?.lowercase()?.removePrefix("www.")?.substringBefore('.') ?: return false
        return host.isNotEmpty() && lower.replace(" ", "").contains(host)
    }

    private val SEPARATORS = listOf(" : ", " - ", " — ", " – ")

    private val JUNK_TAILS = listOf(
        "la meilleure recette", "meilleure recette", "notre recette", "recette facile",
        "recette", "la recette", "recipe", "easy recipe", "best recipe", "recipes",
        "how to make it",
    )

    /**
     * "maison" is deliberately absent. A homemade quiche lorraine is a different thing
     * from a quiche lorraine, so that word is part of the dish rather than a boast.
     */
    private val TRAILING_BOASTS = listOf(
        " facile et rapide", " facile", " rapide",
        " recipe", " (the best)", " the best",
    )
}

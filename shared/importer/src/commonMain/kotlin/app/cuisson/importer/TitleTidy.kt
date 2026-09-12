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

        // "BA's Best Chocolate Chip Cookies" is a magazine's brand in front of a dish,
        // and "Best Lentil Soup" is "Lasagna (The Best)" with the words reordered.
        LEADING_BOAST.find(title)?.let { match ->
            val rest = title.drop(match.value.length).trim()
            // A shorter floor than the garnish cut uses: "Lentil Soup" and "Brownies"
            // are complete titles, whereas cutting a menu description short is not.
            if (rest.length >= SHORTEST_NAME) title = rest.replaceFirstChar { it.uppercaseChar() }
        }

        // "Recette de la tarte tatin" is a title about a recipe rather than a title.
        for (prefix in LEADING_FILLER) {
            if (title.length > prefix.length + 6 && title.startsWith(prefix, ignoreCase = true)) {
                // Removing "La recette du " leaves a sentence starting in lower case.
                title = title.drop(prefix.length).trim()
                    .replaceFirstChar { it.uppercaseChar() }
                break
            }
        }

        title = shortenGarnishList(title)

        return title.ifBlank { raw.trim() }
    }

    /**
     * Cuts the garnishes off a restaurant's full menu description.
     *
     * Fine dining sites name a dish the way a menu does: "Fillet of beef wellington with
     * parsley root puree mini fondants, sauteed kale and rosemary jus" is ninety-six
     * characters, and in a list of forty recipes it is unreadable. The dish is the first
     * clause; everything after "with" or the first comma is what comes alongside it.
     *
     * Only applied to titles long enough to be a problem, and never when it would leave a
     * stub. Nothing is lost either way: the full title is kept and the recipe screen can
     * show it. Trimming for a list is not the same as discarding.
     */
    private fun shortenGarnishList(title: String): String {
        if (title.length <= LONG_TITLE) return title
        val cut = GARNISH_JOINS
            .mapNotNull { join -> title.indexOf(join, ignoreCase = true).takeIf { it > 0 } }
            .filter { it >= SHORTEST_DISH }
            .minOrNull() ?: return title
        return title.substring(0, cut).trim().trimEnd(',')
    }

    /** Long enough that a list of them cannot be scanned. */
    private const val LONG_TITLE = 60

    /** Below this a garnish cut leaves a stub rather than a dish. */
    private const val SHORTEST_DISH = 12

    /** A dish can be named in one short word. "Brownies" is a title. */
    private const val SHORTEST_NAME = 5

    private fun isJunkTail(tail: String, sourceHost: String?): Boolean {
        if (tail.isEmpty() || tail.split(" ").size > 5) return false
        val lower = tail.lowercase()
        if (JUNK_TAILS.any { lower == it || lower.startsWith("$it ") }) return true

        // "Beef wellington - Great British Chefs": the tail is the site's own name.
        val host = sourceHost?.lowercase()?.removePrefix("www.")?.substringBefore('.') ?: return false
        return host.isNotEmpty() && lower.replace(" ", "").contains(host)
    }

    private val SEPARATORS = listOf(" : ", " - ", " — ", " – ")

    /**
     * A boast at the front of a title: "Best", "The Best", or a publication claiming it,
     * as in "BA's Best". The apostrophe may be either kind.
     */
    private val LEADING_BOAST = Regex(
        """^(?:the\s+best|best|our\s+best|my\s+best|\S+['’]s\s+best)\s+""",
        RegexOption.IGNORE_CASE,
    )

    private val GARNISH_JOINS = listOf(" with ", " avec ", ", ", " served ", " accompagne")

    private val LEADING_FILLER = listOf(
        "la recette du ", "la recette de la ", "la recette de ", "recette de la ",
        "recette du ", "recette de ", "recipe for ", "how to make ",
    )

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

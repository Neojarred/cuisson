package app.cuisson.domain

/**
 * Reduces a recipe's address to the part that identifies the page.
 *
 * The same recipe reaches Cuisson by several spellings of one address. A link shared from
 * a phone carries tracking parameters, a link copied from a browser may not, and the host
 * may or may not have www in front of it. Comparing the addresses as written therefore
 * misses duplicates that are plainly the same page.
 *
 * Deliberately conservative. It only removes things that cannot change which page you
 * land on, so two recipes that genuinely differ are never merged. Two different addresses
 * for the same dish are not duplicates and this is not trying to catch them.
 */
fun sourceUrlKey(url: String?): String? {
    val trimmed = url?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .substringBefore('?')
        .trimEnd('/')
        .takeIf { it.isNotEmpty() }
}

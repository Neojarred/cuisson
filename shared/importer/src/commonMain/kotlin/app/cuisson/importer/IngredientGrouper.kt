package app.cuisson.importer

import app.cuisson.domain.DraftIngredient
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/**
 * Recovers the publisher's own division of an ingredient list.
 *
 * A lasagne's ingredients belong under "Meat Sauce", "Bechamel" and "Assembly", and a
 * flat list of nineteen items destroys information the cook needs. schema.org has no
 * field for this, and every site checked publishes one flat `recipeIngredient` array, so
 * the grouping can only come from the page itself.
 *
 * Rather than a selector per recipe plugin, this uses the structured data as an anchor:
 * we already know exactly what each ingredient says, so its row can be located in the
 * document, and any short heading sitting between those rows is a group name. That works
 * on sites nobody has written a rule for.
 *
 * It fails safe in both directions. Where the page renders its recipe card in JavaScript,
 * which is most of them, the rows are not in the fetched HTML and nothing is grouped.
 * Where only one heading is found, the result is one group, which is the same as none, so
 * the flat list stands.
 */
object IngredientGrouper {

    fun group(html: String, ingredients: List<String>): List<DraftIngredient> {
        if (ingredients.size < 2) return ingredients.map(::DraftIngredient)

        val positions = runCatching { headingsAndRows(html, ingredients) }.getOrNull()
            ?: return ingredients.map(::DraftIngredient)

        // An ingredient too short to locate in the page, "salt" or "poivre", has no
        // position and so no heading above it. Left as it is, that hole splits a group in
        // two and the heading is printed twice. The publisher's order is authoritative:
        // an unplaced ingredient sitting between two Meat Sauce items is Meat Sauce.
        var carried: String? = null
        val labels = ingredients.indices.map { index ->
            positions.groupFor(index)?.also { carried = it } ?: carried
        }

        // Two distinct labels counting the absence of one, because a single heading
        // partway down the list is meaningful by itself: everything above it is the
        // recipe and everything below is "Topping options", which is the case that made
        // a flat list annoying to read.
        //
        // The bar on coverage is deliberately low. Ingredients like "salt" are too short
        // to locate in a page safely, so a correctly grouped list still has holes.
        val named = labels.count { it != null }
        return if (labels.distinct().size < 2 || named < 2) {
            ingredients.map(::DraftIngredient)
        } else {
            ingredients.mapIndexed { index, text -> DraftIngredient(text, labels[index]) }
        }
    }

    private class Placement(
        private val headings: List<Pair<Int, String>>,
        private val rows: Map<Int, Int>,
    ) {
        val coverage: Double get() = if (rows.isEmpty()) 0.0 else rows.size.toDouble()

        fun groupFor(ingredientIndex: Int): String? {
            val at = rows[ingredientIndex] ?: return null
            return headings.lastOrNull { it.first < at }?.second
        }
    }

    private fun headingsAndRows(html: String, ingredients: List<String>): Placement? {
        val document = Ksoup.parse(html)
        document.select("script, style, noscript").forEach { it.remove() }

        val ordered = mutableListOf<Element>()
        collect(document.body(), ordered)

        val needles = ingredients.map { normalise(it) }

        // Which ingredient, if any, does each element's own text state?
        val statedBy = HashMap<Element, Int>()
        ordered.forEach { element ->
            if (element.childrenSize() != 0) return@forEach
            val text = normalise(element.text())
            if (text.length < 6) return@forEach
            val index = needles.indexOfFirst { it.length >= 6 && text.contains(it) }
            if (index >= 0) statedBy[element] = index
        }
        if (statedBy.size < 3) return null

        // The ingredient list is the smallest element holding most of the ingredients.
        // Searching the whole document instead picks up the method's own headings, which
        // produced "Step 1" and "Recipe information" as ingredient groups.
        // The smallest element that holds every ingredient found. Accepting one that
        // held only half let a single large group win: Ricardo's "Meat Sauce" list
        // contains eight of sixteen ingredients and no heading, so grouping it against
        // itself produced nothing.
        val distinctStated = statedBy.values.distinct().size
        val container = ordered
            .filter { candidate ->
                ingredientsUnder(candidate, statedBy).size == distinctStated
            }
            .minByOrNull { descendantCount(it) }
            ?: return null

        val inside = mutableListOf<Element>()
        collect(container, inside)

        val rows = mutableMapOf<Int, Int>()
        inside.forEachIndexed { at, element ->
            statedBy[element]?.let { index -> if (index !in rows) rows[index] = at }
        }
        if (rows.size < 3) return null

        val headings = inside.withIndex()
            .filter { (_, element) -> element !in statedBy }
            .mapNotNull { (at, element) ->
                if (!looksLikeHeading(element)) return@mapNotNull null
                // "Topping options:" is a heading with its punctuation still attached.
                val text = element.text().trim().trimEnd(':', '-', '\u2014').trim()
                text.takeIf { it.isNotEmpty() && !isSectionTitle(it) }?.let { at to it }
            }
            .distinctBy { it.second }

        return Placement(headings, rows)
    }

    private fun descendantCount(element: Element): Int {
        val all = mutableListOf<Element>()
        collect(element, all)
        return all.size
    }

    /** The distinct ingredients stated somewhere beneath this element. */
    private fun ingredientsUnder(element: Element, statedBy: Map<Element, Int>): Set<Int> {
        val found = mutableSetOf<Int>()
        val all = mutableListOf<Element>()
        collect(element, all)
        all.forEach { statedBy[it]?.let(found::add) }
        return found
    }

    private fun collect(element: Element?, into: MutableList<Element>) {
        if (element == null) return
        into.add(element)
        element.children().forEach { collect(it, into) }
    }

    /**
     * A heading is a heading tag, or something a recipe plugin calls a group or subtitle,
     * and it is short. A sentence that happens to sit in an h3 is not a group name.
     */
    private fun looksLikeHeading(element: Element): Boolean {
        val text = element.text().trim()
        if (text.isEmpty() || text.length > 40 || text.split(" ").size > 6) return false
        val tag = element.tagName().lowercase()
        if (tag in setOf("h2", "h3", "h4", "h5", "h6", "legend", "caption")) return true
        val classes = element.className().lowercase()
        return listOf("group", "subtitle", "section-title", "ingredient-heading")
            .any { classes.contains(it) }
    }

    /**
     * "Ingredients" names the list rather than dividing it, and "Step 1" belongs to the
     * method. Bon Appetit puts both inside one container, which produced "Step 1" and
     * "Recipe information" as ingredient groups.
     */
    private fun isSectionTitle(text: String): Boolean {
        val clean = normalise(text)
        if (STEP_HEADING.matches(clean)) return true
        return clean in setOf(
            "ingredients", "ingredient", "ingredienten", "ingredientes",
            "method", "instructions", "directions", "steps", "preparation", "etapes",
            "nutrition", "equipment", "notes", "recipe information", "you will need",
        )
    }

    private val STEP_HEADING = Regex("(?:step|etape|stap|schritt)\\s*\\d+")

    /** Accents are folded, so "Ingredients" and "Ingredients" compare equal. */
    private fun normalise(text: String): String =
        text.lowercase()
            .replace(Regex("[àâäáã]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[îïí]"), "i")
            .replace(Regex("[ôöó]"), "o")
            .replace(Regex("[ùûüú]"), "u")
            .replace('ç', 'c')
            .replace(' ', ' ')
            .filter { it.isLetterOrDigit() || it == ' ' }
            .replace(Regex("\\s+"), " ")
            .trim()
}

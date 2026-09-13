package app.cuisson.domain

/**
 * One line of an ingredient list.
 *
 * [rawText] is what the source actually said and is never modified. [amendment] is the
 * user's wording where they disagreed with it, kept beside the original rather than over
 * it, so an edit can be taken back and the phase 4 parser still has the line the site
 * published. See ADR-0004.
 *
 * Everything else is the parser's reading of the line, and any of it may be absent. The
 * interface displays [text]; the parsed fields drive scaling and the shopping list.
 *
 * Re-rendering a line from the parsed pieces is how "5 garlic cloves" becomes
 * "5 clove garlic". Don't.
 */
data class IngredientLine(
    val id: String,
    val position: Int,
    val rawText: String,
    val amendment: String? = null,
    val groupLabel: String? = null,
    val quantity: QuantityRange? = null,
    val unit: MeasureUnit? = null,
    val itemText: String? = null,
    val preparation: String? = null,
    val optional: Boolean = false,
    val canonicalItemId: String? = null,
    val parseConfidence: Float = 0f,
) {
    /** The line as the reader should see it: their wording if they gave one. */
    val text: String get() = amendment ?: rawText

    val isAmended: Boolean get() = amendment != null
}

/**
 * A quantity, which may be a range. "2 to 3 onions" is a range; "2 onions" is a range
 * whose ends are equal.
 */
data class QuantityRange(val min: Double, val max: Double = min) {
    val isRange: Boolean get() = min != max
}

/**
 * A unit as written, normalised. Sources routinely state two systems at once, as in
 * "400ml / 14 oz", so both readings are kept and the interface shows the one matching the
 * reader's preference.
 */
data class MeasureUnit(
    val canonical: String,
    val system: UnitSystem,
    val alternate: MeasureUnit? = null,
)

enum class UnitSystem { METRIC, IMPERIAL, COUNT, NONE }

package app.cuisson.domain

/**
 * The result of an extraction, before the user has accepted it at the Review.
 *
 * Everything here is as close to what the source said as possible. Ingredient lines are
 * plain strings: splitting them into quantity, unit and item happens later, in a stage
 * that can be rerun and corrected. Nothing is invented, and anything the extractor could
 * not make sense of is reported in [warnings] rather than quietly dropped.
 *
 * The publisher's description is deliberately absent. It is the one genuinely copyrighted
 * part of a recipe page, and it is also the waffle nobody wants.
 */
data class DraftRecipe(
    val title: String,
    /** The title exactly as the source wrote it, before any tidying. See ADR-0004. */
    val rawTitle: String? = null,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val imageUrl: String? = null,
    val servingsText: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredientLines: List<DraftIngredient> = emptyList(),
    val steps: List<DraftStep> = emptyList(),
    val sourceNotes: List<SourceNote> = emptyList(),
    val language: String? = null,
    val tier: ExtractionTier = ExtractionTier.STRUCTURED,
    val warnings: List<ExtractionWarning> = emptyList(),
) {
    val looksUsable: Boolean
        get() = title.isNotBlank() && ingredientLines.isNotEmpty() && steps.isNotEmpty()

    /** The ingredient lines as written, without their grouping. */
    val ingredientTexts: List<String> get() = ingredientLines.map { it.text }
}

/**
 * One ingredient as the source wrote it, and the heading it sat under.
 *
 * [group] is the publisher's own division of the list, such as "Meat Sauce" or
 * "Bechamel", which a lasagne needs and a flat list destroys. It is absent far more often
 * than it is present, because most sites do not say.
 */
data class DraftIngredient(
    val text: String,
    val group: String? = null,
)

data class DraftStep(
    val text: String,
    val sectionLabel: String? = null,
)

/**
 * Something the extractor noticed and could not resolve. These reach the Review so the
 * user sees what is uncertain, rather than being told everything went well.
 */
enum class ExtractionWarning {
    NO_INGREDIENTS,
    NO_STEPS,
    NO_TITLE,
    SERVINGS_NOT_UNDERSTOOD,
    DURATION_NOT_UNDERSTOOD,
    STEPS_CONTAINED_MARKUP,
    REFERENCES_UNCAPTURED_NOTES,
}

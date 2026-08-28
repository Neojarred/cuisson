package app.cuisson.domain

import kotlin.time.Instant
import kotlin.jvm.JvmInline

/**
 * A dish as Cuisson stores it.
 *
 * Every imported recipe keeps the text it arrived as. Parsed fields are an overlay on top
 * of that text and never replace it, so a bad parse is always correctable and a better
 * parser can be run over the whole library later. See docs/adr/0004.
 */
data class Recipe(
    val id: RecipeId,
    val title: String,
    val source: Source,
    val servings: Servings?,
    val timings: Timings,
    val ingredients: List<IngredientLine>,
    val steps: List<Step>,
    val notes: String?,
    val language: String,
    val extraction: Extraction,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JvmInline
value class RecipeId(val value: String)

/**
 * Where a recipe came from. The URL is kept for every import so the original page is
 * always one tap away, and so a second import of the same address can be recognised
 * rather than silently duplicated.
 */
data class Source(
    val kind: SourceKind,
    val url: String? = null,
    val name: String? = null,
)

enum class SourceKind { WEB, MANUAL, PASTED_TEXT, PHOTO, SOCIAL, APP_IMPORT }

data class Servings(val count: Double, val unit: String? = null)

data class Timings(
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
)

/**
 * How a recipe was extracted and how far it can be trusted. A recipe read from a site's
 * own structured data is reliable. One assembled by a model from a photograph is not, and
 * the interface says so until a human has looked at it.
 */
data class Extraction(
    val tier: ExtractionTier,
    val confidence: Float,
    val needsReview: Boolean,
)

enum class ExtractionTier {
    /** Read from the machine-readable recipe the site publishes. The main path. */
    STRUCTURED,

    /** Read using a stored rule for a site that publishes no structured data. */
    SITE_RULE,

    /** Assembled by a language model from something with no structure to read. */
    MODEL,

    /** Nothing could be extracted, so the page text was kept for the user to salvage. */
    PAGE_TEXT,

    /** Typed in by the user. */
    HAND_WRITTEN,
}

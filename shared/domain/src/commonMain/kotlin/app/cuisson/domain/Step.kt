package app.cuisson.domain

/**
 * One instruction in a recipe's method.
 *
 * [sourceText] is what the author wrote and is never modified. [amendment] is the user's
 * wording where they disagreed with it, kept beside the original rather than over it so
 * an edit can always be taken back. See ADR-0004.
 *
 * Read [text] to display a step. That is the whole reason the author's wording is not
 * called `text`: the property you reach for without thinking is the right one.
 *
 * [durationSeconds] is parsed out of the text so "simmer for 20 minutes" can offer a
 * timer, and re-read whenever the step is amended. [references] records pointers the
 * source made to material we did not capture, such as "see Note 3", so the recipe can
 * admit it is incomplete rather than looking whole and failing the cook halfway through.
 */
data class Step(
    val id: String,
    val position: Int,
    val sourceText: String,
    val amendment: String? = null,
    val durationSeconds: Int? = null,
    val references: List<String> = emptyList(),
) {
    val text: String get() = amendment ?: sourceText

    val isAmended: Boolean get() = amendment != null
}

package app.cuisson.domain

/**
 * One instruction in a recipe's method.
 *
 * [durationSeconds] is parsed out of the text at import so "simmer for 20 minutes" can
 * offer a timer. [references] records pointers the source made to material we did not
 * capture, such as "see Note 3", so the recipe can admit it is incomplete rather than
 * looking whole and failing the cook halfway through.
 */
data class Step(
    val id: String,
    val position: Int,
    val text: String,
    val durationSeconds: Int? = null,
    val references: List<String> = emptyList(),
)

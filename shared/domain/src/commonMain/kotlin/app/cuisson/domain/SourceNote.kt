package app.cuisson.domain

/**
 * A note the publisher wrote alongside their recipe.
 *
 * RecipeTin Eats' beef rendang carries nine of these, and its ingredients point straight
 * at them: "12 dried chilies (Note 1a)". Without the note the recipe is quietly
 * incomplete, and the cook finds out at the hob.
 *
 * [label] is the publisher's own numbering where they used one, so a Note Reference can
 * be resolved against it. Kept as written and attributed to them, never mixed into the
 * recipe as though Cuisson said it, and never sent to anyone else. See ADR-0009.
 */
data class SourceNote(
    val label: String?,
    val text: String,
)

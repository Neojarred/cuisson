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

/**
 * The Source Note labels this recipe actually points at.
 *
 * A publisher's notes section can be longer than the recipe, and most of it is the
 * author writing about their recipe rather than telling you how to cook it. The reason
 * for capturing notes at all was narrower than that: an ingredient reading "12 dried
 * chilies (Note 1a)" is incomplete without 1a. Those are the notes worth putting in
 * front of someone, and the rest can wait behind a tap.
 */
fun Recipe.referencedNoteLabels(): Set<String> {
    val text = ingredients.map { it.rawText } + steps.map { it.text }
    return text.flatMap { NOTE_REFERENCE.findAll(it).map { m -> m.groupValues[1].lowercase() } }
        .toSet()
}

private val NOTE_REFERENCE = Regex("""\bnote\s*(\d+[a-z]?)\b""", RegexOption.IGNORE_CASE)

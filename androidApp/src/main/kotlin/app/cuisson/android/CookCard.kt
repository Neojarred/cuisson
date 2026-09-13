package app.cuisson.android

import app.cuisson.domain.Recipe
import app.cuisson.domain.SourceNote
import app.cuisson.text.StepSplitter

/**
 * One screenful of cooking.
 *
 * A card is usually a whole step. A step written as a dense paragraph becomes several,
 * derived here and never stored, so the recipe keeps exactly what its author wrote.
 *
 * [notes] are the author's notes this card actually points at. They are carried on the
 * card because that is where they are wanted: at the hob, against the instruction that
 * mentions them, rather than in a list at the bottom of the recipe.
 */
data class CookCard(
    val stepNumber: Int,
    val partOfStep: Int,
    val partsInStep: Int,
    val text: String,
    val durationSeconds: Int?,
    val notes: List<SourceNote>,
    /**
     * What a timer started from this card is called.
     *
     * Derived from the step's own id rather than its position, so a timer survives the
     * process that started it and still points at the right step afterwards.
     */
    val timerId: String,
) {
    val isWholeStep: Boolean get() = partsInStep == 1
}

fun cookCardsFor(recipe: Recipe, splitLongSteps: Boolean = true): List<CookCard> {
    val notesByLabel = recipe.sourceNotes
        .mapNotNull { note -> note.label?.lowercase()?.let { it to note } }
        .toMap()

    return recipe.steps.flatMapIndexed { index, step ->
        val parts = StepSplitter.split(step.text, splitLongSteps)
        parts.mapIndexed { part, text ->
            CookCard(
                stepNumber = index + 1,
                partOfStep = part + 1,
                partsInStep = parts.size,
                text = text,
                // The timer belongs to the part of the step that mentions the duration,
                // not to all of them: a step split into four should not offer four timers.
                durationSeconds = step.durationSeconds?.takeIf { mentionsTime(text) },
                notes = referencedIn(text).mapNotNull(notesByLabel::get),
                timerId = "${step.id}-$part",
            )
        }
    }
}

private fun mentionsTime(text: String): Boolean =
    Regex("""\d+\s*(second|sec|minute|min|hour|hr)""", RegexOption.IGNORE_CASE)
        .containsMatchIn(text)

private fun referencedIn(text: String): List<String> =
    Regex("""\bnote\s*(\d+[a-z]?)\b""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map { it.groupValues[1].lowercase() }
        .toList()

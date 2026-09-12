package app.cuisson.domain

import kotlin.time.Instant

/**
 * Turns an accepted Draft Recipe into a stored Recipe.
 *
 * Ingredient lines arrive holding only their raw text, and that is correct: splitting a
 * line into quantity, unit and item is a separate stage that does not exist yet, and
 * pretending otherwise would put invented structure into the database. Raw text is what
 * gets stored either way, per ADR-0004, so parsing can be run over the library later.
 *
 * Child identifiers are derived from the recipe's own, which keeps them stable when the
 * same draft is saved twice and avoids needing a random source in shared code.
 */
fun DraftRecipe.toRecipe(id: String, now: Instant): Recipe = Recipe(
    id = RecipeId(id),
    title = title.ifBlank { "Untitled recipe" },
    rawTitle = rawTitle?.takeIf { it != title },
    source = Source(
        kind = if (sourceUrl != null) SourceKind.WEB else SourceKind.PASTED_TEXT,
        url = sourceUrl,
        name = sourceName,
    ),
    servings = parseServings(servingsText),
    timings = Timings(prepMinutes, cookMinutes, totalMinutes),
    ingredients = ingredientLines.mapIndexed { index, line ->
        IngredientLine(
            id = "$id-i$index",
            position = index,
            rawText = line.text,
            groupLabel = line.group,
        )
    },
    steps = steps.mapIndexed { index, step ->
        Step(
            id = "$id-s$index",
            position = index,
            text = step.text,
            references = referencesIn(step.text),
        )
    },
    notes = null,
    sourceNotes = sourceNotes,
    imagePath = null,
    chapterId = null,
    language = language ?: "en",
    extraction = Extraction(
        tier = tier,
        confidence = if (looksUsable) 1f else 0.5f,
        needsReview = !looksUsable || warnings.isNotEmpty(),
    ),
    createdAt = now,
    updatedAt = now,
)

/**
 * "6" is six servings. "8 personnes" is eight of something the publisher named, and the
 * name is worth keeping. Anything with no leading number is left alone rather than
 * guessed at.
 */
internal fun parseServings(text: String?): Servings? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val match = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""").find(trimmed) ?: return null
    val count = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
    return Servings(count, unit)
}

/** Pointers to material the source held outside the part we captured. */
internal fun referencesIn(text: String): List<String> =
    Regex("""\b[Nn]ote\s*\d+[a-z]?\b""").findAll(text).map { it.value }.distinct().toList()

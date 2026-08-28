package app.cuisson.importer

import app.cuisson.domain.DraftRecipe
import app.cuisson.domain.DraftStep
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.ExtractionWarning

/**
 * Reads a recipe out of plain text somebody typed or pasted.
 *
 * This is the path for a recipe dictated by a relative, copied out of a message, or typed
 * from a card. There is no markup to lean on, so it works from the shape of the writing:
 * ingredient lines are short and start with an amount, method lines are sentences.
 *
 * It is wrong sometimes, and that is expected rather than hidden. Everything it produces
 * goes to the Review, and a line it misfiled is one tap from being moved. What it must
 * never do is drop a line: every non-empty line the user gave ends up somewhere.
 */
object TextRecipeParser {

    fun parse(text: String, providedTitle: String? = null): DraftRecipe {
        val lines = text.lines().map { it.trim() }
        val warnings = mutableListOf<ExtractionWarning>()

        var title = providedTitle?.trim().orEmpty()
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()

        var section = if (providedTitle != null) Section.UNKNOWN else Section.LOOKING_FOR_TITLE

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            headingFor(line)?.let { heading ->
                section = heading
                continue
            }

            when (section) {
                Section.LOOKING_FOR_TITLE -> {
                    title = stripBullet(line)
                    section = Section.UNKNOWN
                }
                Section.INGREDIENTS -> ingredients += stripBullet(line)
                Section.METHOD -> steps += stripNumbering(line)
                Section.UNKNOWN ->
                    if (looksLikeIngredient(line)) {
                        ingredients += stripBullet(line)
                    } else {
                        steps += stripNumbering(line)
                    }
            }
        }

        if (title.isBlank()) warnings += ExtractionWarning.NO_TITLE
        if (ingredients.isEmpty()) warnings += ExtractionWarning.NO_INGREDIENTS
        if (steps.isEmpty()) warnings += ExtractionWarning.NO_STEPS

        return DraftRecipe(
            title = title,
            ingredientLines = ingredients,
            steps = steps.map { DraftStep(it) },
            tier = ExtractionTier.HAND_WRITTEN,
            warnings = warnings,
        )
    }

    private enum class Section { LOOKING_FOR_TITLE, UNKNOWN, INGREDIENTS, METHOD }

    /**
     * People label the two halves, in either language, with or without punctuation. A
     * heading is only a heading if it is short: a sentence that happens to contain the
     * word "preparation" is a step.
     */
    private fun headingFor(line: String): Section? {
        val cleaned = line.lowercase().trim(' ', ':', '-', '—', '.', '#', '*')
        if (cleaned.length > 24) return null
        return when {
            cleaned in INGREDIENT_HEADINGS -> Section.INGREDIENTS
            cleaned in METHOD_HEADINGS -> Section.METHOD
            else -> null
        }
    }

    /**
     * An ingredient line usually opens with an amount, and is short. A method line is a
     * sentence. Where the two disagree, the amount wins, because "2 eggs" is an
     * ingredient however it is punctuated.
     */
    private fun looksLikeIngredient(line: String): Boolean {
        val body = stripBullet(line)
        val words = body.split(Regex("\\s+")).size

        // "2) Add the stock and simmer" opens with a digit and is not two of anything.
        // A writer numbering their own steps is checked before amounts, or every
        // numbered step becomes an ingredient.
        if (NUMBERED_LINE.containsMatchIn(body) && words > 6) return false

        if (STARTS_WITH_AMOUNT.containsMatchIn(body)) return true
        val endsLikeSentence = body.endsWith('.') || body.endsWith('!')
        return words <= 7 && !endsLikeSentence
    }

    private fun stripBullet(line: String): String =
        line.trimStart('-', '*', '•', '·', '–', '—', ' ').trim()

    /** "1." and "Step 3:" are the writer numbering their own steps, not part of them. */
    private fun stripNumbering(line: String): String =
        stripBullet(line).replace(STEP_NUMBER, "").trim()

    private val INGREDIENT_HEADINGS = setOf(
        "ingredients", "ingredient", "ingrédients", "ingredients list",
        "you will need", "il vous faut", "ingrédient",
    )

    private val METHOD_HEADINGS = setOf(
        "method", "instructions", "directions", "steps", "preparation",
        "préparation", "étapes", "etapes", "réalisation", "realisation",
        "how to make it", "method of preparation",
    )

    private val STARTS_WITH_AMOUNT = Regex(
        """^\s*(\d+([.,/]\d+)?|[½¼¾⅓⅔⅛⅜⅝⅞]|une?|deux|trois|quatre|a|an)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A leading "1.", "2)" or "3:" that the writer used to number their own steps. */
    private val NUMBERED_LINE = Regex("""^\s*\d{1,2}\s*[.):\-]\s+\S""")

    private val STEP_NUMBER = Regex(
        """^\s*(step|étape|etape)?\s*\d+\s*[.):\-]?\s*""",
        RegexOption.IGNORE_CASE,
    )
}

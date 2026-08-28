package app.cuisson.importer

import app.cuisson.domain.DraftRecipe
import app.cuisson.domain.DraftStep
import app.cuisson.domain.ExtractionWarning
import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Structured Extraction: reads the machine-readable recipe a site already publishes.
 *
 * This is the main import path, not a fallback. Google requires this markup for a recipe
 * to appear as a rich result, so nearly every recipe site carries it. Because it is
 * parsing rather than inference, it cannot invent an ingredient that was not on the page.
 *
 * Nothing here trusts the specification. Every field is read defensively, because what
 * publishers actually emit differs from what schema.org describes, and a page that gets
 * one field wrong should still yield a usable recipe.
 */
object StructuredExtractor {

    fun extract(html: String, sourceUrl: String? = null): DraftRecipe? {
        val recipe = findRecipeNode(html) ?: return null
        val warnings = mutableListOf<ExtractionWarning>()

        val title = recipe["name"].firstString()?.let(::cleanText).orEmpty()
        if (title.isBlank()) warnings += ExtractionWarning.NO_TITLE

        val ingredients = (recipe["recipeIngredient"] ?: recipe["ingredients"])
            .allStrings()
            .map(::cleanText)
            .filter { it.isNotBlank() }
        if (ingredients.isEmpty()) warnings += ExtractionWarning.NO_INGREDIENTS

        val steps = readInstructions(recipe["recipeInstructions"], warnings)
        if (steps.isEmpty()) warnings += ExtractionWarning.NO_STEPS

        if (steps.any { referencesUncapturedNotes(it.text) }) {
            warnings += ExtractionWarning.REFERENCES_UNCAPTURED_NOTES
        }

        val servings = readServings(recipe["recipeYield"], warnings)
        val total = parseIsoDurationMinutes(recipe["totalTime"].firstString())
        val prep = parseIsoDurationMinutes(recipe["prepTime"].firstString())
        val cook = parseIsoDurationMinutes(recipe["cookTime"].firstString())
        if (total == null && prep == null && cook == null && recipe["totalTime"] != null) {
            warnings += ExtractionWarning.DURATION_NOT_UNDERSTOOD
        }

        return DraftRecipe(
            title = title,
            sourceUrl = sourceUrl,
            sourceName = recipe["author"].firstString()?.let(::cleanText),
            imageUrl = recipe["image"].firstString(),
            servingsText = servings,
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total ?: sumOrNull(prep, cook),
            ingredientLines = ingredients,
            steps = steps,
            language = recipe["inLanguage"].firstString(),
            warnings = warnings,
        )
    }

    /**
     * Instructions arrive as plain strings, as HowToStep objects, or as HowToSection
     * objects each holding their own list of steps. Sections carry a name such as
     * "For the sauce", which is worth keeping.
     */
    private fun readInstructions(
        element: JsonElement?,
        warnings: MutableList<ExtractionWarning>,
    ): List<DraftStep> {
        val steps = mutableListOf<DraftStep>()

        fun walk(node: JsonElement?, section: String?) {
            when (node) {
                is JsonArray -> node.forEach { walk(it, section) }
                is JsonObject -> {
                    if (node.declaresType("HowToSection")) {
                        val label = node["name"].firstString()?.let(::cleanText)
                        walk(node["itemListElement"] ?: node["steps"], label)
                    } else {
                        val text = (node["text"] ?: node["name"]).firstString()
                        if (!text.isNullOrBlank()) {
                            if (containsMarkup(text)) {
                                warnings += ExtractionWarning.STEPS_CONTAINED_MARKUP
                            }
                            addSplit(steps, cleanText(text), section)
                        }
                    }
                }
                else -> {
                    val text = node.firstString()
                    if (!text.isNullOrBlank()) {
                        if (containsMarkup(text)) {
                            warnings += ExtractionWarning.STEPS_CONTAINED_MARKUP
                        }
                        addSplit(steps, cleanText(text), section)
                    }
                }
            }
        }

        walk(element, null)
        return steps.filter { it.text.isNotBlank() }.distinctBy { it.text to it.sectionLabel }
    }

    /**
     * Some publishers put an entire method into one string with newlines in it. Splitting
     * on blank lines recovers the steps; splitting more aggressively than that would
     * break sentences that belong together.
     */
    private fun addSplit(into: MutableList<DraftStep>, text: String, section: String?) {
        val parts = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            parts.forEach { into += DraftStep(it, section) }
        } else {
            into += DraftStep(text, section)
        }
    }

    /**
     * recipeYield is the least consistent field in the whole vocabulary. Three sites
     * checked, three shapes: the number 6, the string "8 personnes", and the array
     * ["6"]. It is kept as written rather than reduced to a number, because "8 personnes"
     * carries a unit and "6" does not.
     */
    private fun readServings(
        element: JsonElement?,
        warnings: MutableList<ExtractionWarning>,
    ): String? {
        val raw = element.firstString()?.let(::cleanText)?.takeIf { it.isNotBlank() }
        if (raw == null && element != null) {
            warnings += ExtractionWarning.SERVINGS_NOT_UNDERSTOOD
        }
        return raw
    }

    /**
     * "See Note 3" and "recipe video above" point at material held in the page body,
     * outside the structured data. Dropping the target silently leaves a recipe that
     * looks complete and fails the cook halfway through.
     */
    private fun referencesUncapturedNotes(text: String): Boolean =
        Regex("""\b(note\s*\d+|notes?\s+below|video\s+above|see\s+notes?)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    private fun containsMarkup(text: String): Boolean =
        text.contains('<') && Regex("<[a-zA-Z/][^>]*>").containsMatchIn(text)

    private fun sumOrNull(a: Int?, b: Int?): Int? =
        if (a == null && b == null) null else (a ?: 0) + (b ?: 0)
}

/**
 * Strips any markup a publisher embedded and decodes HTML entities, so "&frac12; tsp"
 * and "<p>Simmer</p>" become what a person would have written.
 */
internal fun cleanText(raw: String): String =
    Ksoup.parse(raw).text().replace(' ', ' ').trim()

package app.cuisson.importer

import app.cuisson.domain.DraftRecipe
import app.cuisson.domain.DraftStep
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.ExtractionWarning
import com.fleeksoft.ksoup.Ksoup

/**
 * The last resort, when a page carries no structured recipe and no model is available.
 *
 * It keeps the title and the readable text so the user can fix it by hand in half a
 * minute. Refusing with an error instead would lose what they were trying to do, and a
 * half-captured recipe is worth more than a clean failure.
 *
 * Nothing here pretends to have understood anything. The result is always marked as
 * needing review.
 */
object PageTextExtractor {

    fun extract(html: String, sourceUrl: String? = null): DraftRecipe {
        val document = Ksoup.parse(html)

        document.select("script, style, nav, header, footer, aside, noscript, form")
            .forEach { it.remove() }

        val title = document.select("meta[property=og:title]").attr("content")
            .ifBlank { document.select("h1").firstOrNull()?.text().orEmpty() }
            .ifBlank { document.title() }
            .let(::cleanText)

        val paragraphs = document.select("p, li")
            .map { cleanText(it.text()) }
            .filter { it.length > 2 }
            .distinct()

        return DraftRecipe(
            title = title,
            sourceUrl = sourceUrl,
            ingredientLines = emptyList(),
            steps = paragraphs.map { DraftStep(it) },
            tier = ExtractionTier.PAGE_TEXT,
            warnings = listOf(
                ExtractionWarning.NO_INGREDIENTS,
                if (title.isBlank()) ExtractionWarning.NO_TITLE else ExtractionWarning.NO_STEPS,
            ).distinct(),
        )
    }
}

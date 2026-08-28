package app.cuisson.importer

import app.cuisson.domain.DraftRecipe

/**
 * Turns something the user handed us into a Draft Recipe, ready for the Review.
 *
 * The order is deliberate and is the subject of ADR-0003. Structured Extraction runs
 * first and handles nearly every URL. A language model is not consulted here at all: a
 * URL never reaches one. When nothing can be read, the page text is kept rather than
 * discarded, so the user can salvage it.
 */
class ImportPipeline(private val fetcher: RecipeFetcher) {

    suspend fun importUrl(rawUrl: String): ImportOutcome =
        when (val fetched = fetcher.fetch(rawUrl)) {
            is FetchResult.Success -> fromHtml(fetched.html, fetched.finalUrl)
            is FetchResult.Blocked -> ImportOutcome.Blocked(fetched.status, fetched.url)
            is FetchResult.Failed -> ImportOutcome.Failed(fetched.status, fetched.reason)
            is FetchResult.NotAUrl -> ImportOutcome.NotAUrl(fetched.input)
        }

    fun fromHtml(html: String, sourceUrl: String?): ImportOutcome {
        val structured = StructuredExtractor.extract(html, sourceUrl)
        if (structured != null && structured.looksUsable) {
            return ImportOutcome.Ready(structured)
        }
        // A recipe that parsed but came out thin is still better than the page text,
        // because at least its ingredients came from the publisher's own markup.
        if (structured != null && structured.ingredientLines.isNotEmpty()) {
            return ImportOutcome.NeedsWork(structured)
        }
        return ImportOutcome.NeedsWork(PageTextExtractor.extract(html, sourceUrl))
    }
}

sealed interface ImportOutcome {
    /** Extraction succeeded. The Review can be dismissed in one tap. */
    data class Ready(val draft: DraftRecipe) : ImportOutcome

    /** Something came out, but the user needs to look at it before it is a Recipe. */
    data class NeedsWork(val draft: DraftRecipe) : ImportOutcome

    /**
     * The site refused us. Kept separate from a failure because the user needs different
     * advice: nothing they do will make this URL work, and pasting the text will.
     */
    data class Blocked(val status: Int, val url: String) : ImportOutcome

    data class Failed(val status: Int?, val reason: String) : ImportOutcome {
        val describe: String
            get() = listOfNotNull(status?.let { "HTTP $it" }, reason.takeIf { it.isNotBlank() })
                .joinToString(": ")
                .ifBlank { "the request did not complete" }
    }
    data class NotAUrl(val input: String) : ImportOutcome
}

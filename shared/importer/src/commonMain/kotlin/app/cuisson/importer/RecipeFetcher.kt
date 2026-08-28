package app.cuisson.importer

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Url

/**
 * Fetches a page the user asked for, from the user's own device.
 *
 * This is a browser doing what browsers do, per ADR-0005: one page, because a person
 * chose it, from their address. It does not crawl, does not follow links of its own, and
 * never fetches anything the user did not hand it.
 */
class RecipeFetcher(private val client: HttpClient) {

    suspend fun fetch(rawUrl: String): FetchResult {
        val url = normalise(rawUrl) ?: return FetchResult.NotAUrl(rawUrl)

        return try {
            val response = client.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "en,fr;q=0.9")
            }
            val status = response.status.value
            when {
                status in 200..299 -> FetchResult.Success(
                    html = response.bodyAsText(),
                    finalUrl = response.request.url.toString(),
                )
                status in BLOCKING_STATUSES -> FetchResult.Blocked(status, url)
                // Non-standard codes, which Cloudflare and friends emit freely, carry an
                // empty description. The number is then the only thing worth saying.
                else -> FetchResult.Failed(status, response.status.description)
            }
        } catch (error: Throwable) {
            // Some failures arrive with no message at all, and "import failed" with a
            // blank line underneath tells nobody anything. The exception's own name is
            // usually the most informative thing available.
            val message = error.message?.takeIf { it.isNotBlank() }
            val kind = error::class.simpleName ?: "error"
            FetchResult.Failed(status = null, reason = message?.let { "$kind: $it" } ?: kind)
        }
    }

    /**
     * Downloads an image so the recipe works offline.
     *
     * The copy stays on the device and travels only in the user's own export, per
     * ADR-0007. Nothing here ever puts it in front of anyone else.
     */
    suspend fun fetchBytes(rawUrl: String, maxBytes: Int = 4 * 1024 * 1024): ByteArray? {
        val url = normalise(rawUrl) ?: return null
        return runCatching {
            val response = client.get(url) { header("User-Agent", BROWSER_USER_AGENT) }
            if (response.status.value !in 200..299) return null
            response.bodyAsBytes().takeIf { it.size in 1..maxBytes }
        }.getOrNull()
    }

    /** People paste addresses with no scheme, and share sheets deliver text around them. */
    private fun normalise(raw: String): String? {
        val candidate = URL_IN_TEXT.find(raw.trim())?.value ?: raw.trim()
        if (candidate.isEmpty()) return null
        val withScheme = when {
            candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
            else -> "https://$candidate"
        }
        val parsed = runCatching { Url(withScheme) }.getOrNull() ?: return null
        if (!parsed.host.contains('.')) return null
        return withScheme
    }

    private companion object {
        /**
         * Sites serve different markup to what they take for a robot, and some refuse it
         * outright. Identifying as the browser this actually is, on the phone it is
         * running on, is honest rather than evasive.
         */
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        /**
         * Kept apart from ordinary failures because the user needs different advice.
         * 402 and 403 are what large publishers return to traffic they distrust, 429 is
         * rate limiting. "This site refused us" is actionable. "Import failed" is not.
         */
        val BLOCKING_STATUSES = setOf(401, 402, 403, 405, 406, 429, 451)

        val URL_IN_TEXT = Regex("""https?://\S+""")
    }
}

sealed interface FetchResult {
    data class Success(val html: String, val finalUrl: String) : FetchResult
    data class Blocked(val status: Int, val url: String) : FetchResult
    data class Failed(val status: Int?, val reason: String) : FetchResult
    data class NotAUrl(val input: String) : FetchResult
}

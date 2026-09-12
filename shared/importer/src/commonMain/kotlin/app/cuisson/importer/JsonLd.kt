package app.cuisson.importer

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    allowTrailingComma = true
}

/**
 * Pulls every `application/ld+json` block out of a page and finds the Recipe in them.
 *
 * Publishers wrap this three different ways and all three appear on ordinary sites: a
 * single object, a top level array, and an `@graph` holding a dozen unrelated nodes with
 * the recipe somewhere inside. A block that fails to parse is skipped rather than
 * aborting the page, because a broken analytics blob should not cost you the recipe.
 */
internal fun findRecipeNode(html: String): JsonObject? {
    val document = Ksoup.parse(html)
    val blocks = document.select("script[type=application/ld+json]")
    for (block in blocks) {
        val text = block.data().ifBlank { block.html() }.trim()
        if (text.isEmpty()) continue
        val parsed = runCatching { lenientJson.parseToJsonElement(text) }.getOrNull() ?: continue
        searchForRecipe(parsed)?.let { return it }
    }
    return null
}

private fun searchForRecipe(element: JsonElement, depth: Int = 0): JsonObject? {
    if (depth > 12) return null
    when (element) {
        is JsonObject -> {
            if (element.declaresType("Recipe")) return element
            for (value in element.values) {
                searchForRecipe(value, depth + 1)?.let { return it }
            }
        }
        is JsonArray -> {
            for (value in element) {
                searchForRecipe(value, depth + 1)?.let { return it }
            }
        }
        else -> return null
    }
    return null
}

/** `@type` is a string on most sites and an array on some. Both mean the same thing. */
internal fun JsonObject.declaresType(wanted: String): Boolean {
    return when (val type = this["@type"]) {
        is JsonPrimitive -> type.contentOrNullSafe()?.equals(wanted, ignoreCase = true) == true
        is JsonArray -> type.any {
            (it as? JsonPrimitive)?.contentOrNullSafe()?.equals(wanted, ignoreCase = true) == true
        }
        else -> false
    }
}

/**
 * Reads a field that may be a string, a number, an array of either, or an object with a
 * `name` or `text`. Returns the first usable string.
 */
internal fun JsonElement?.firstString(): String? = when (this) {
    null -> null
    is JsonPrimitive -> contentOrNullSafe()?.takeIf { it.isNotBlank() }
    is JsonArray -> firstNotNullOfOrNull { it.firstString() }
    is JsonObject -> this["name"].firstString() ?: this["text"].firstString()
        ?: this["url"].firstString()
}

internal fun JsonElement?.allStrings(): List<String> = when (this) {
    null -> emptyList()
    is JsonPrimitive -> listOfNotNull(contentOrNullSafe()?.takeIf { it.isNotBlank() })
    is JsonArray -> flatMap { it.allStrings() }
    is JsonObject -> (this["text"] ?: this["name"]).allStrings()
}

/** A JSON null arrives as the unquoted literal `null`, which is not a usable string. */
/**
 * Picks the image worth keeping out of the several a publisher lists.
 *
 * Google asks for the same photo in more than one aspect ratio and recommends 1x1 first,
 * so taking the first item reliably gets the square crop. On a recipe screen that reads
 * as a zoomed-in version of the real photo, which is what it is.
 *
 * Explicit dimensions are used where they exist, either on an ImageObject or written into
 * the URL, which most image pipelines do. Where nothing says how big anything is, the
 * first one stands, because guessing would be worse than the publisher's own order.
 */
internal fun JsonElement?.bestImageUrl(): String? {
    val candidates = when (this) {
        null -> return null
        is JsonArray -> toList()
        else -> listOf(this)
    }

    val measured = candidates.mapNotNull { candidate ->
        val url = candidate.firstString() ?: return@mapNotNull null
        val declared = (candidate as? JsonObject)?.let { obj ->
            val width = obj["width"].firstString()?.filter { it.isDigit() }?.toIntOrNull()
            val height = obj["height"].firstString()?.filter { it.isDigit() }?.toIntOrNull()
            if (width != null) width to (height ?: width) else null
        }
        val size = declared ?: dimensionsInUrl(url)
        url to size
    }

    if (measured.isEmpty()) return null

    val best = measured
        .filter { it.second != null }
        .maxByOrNull { (_, size) ->
            val (width, height) = size!!
            // Area first, then favour the wider crop of two the same size.
            width.toLong() * height + if (width > height) 1 else 0
        }
    return best?.first ?: measured.first().first
}

/** "photo-1200x800.jpg", "/w_1200,h_800/", "?width=1200&height=800". */
private fun dimensionsInUrl(url: String): Pair<Int, Int>? {
    Regex("""(\d{2,5})\s*[xX×]\s*(\d{2,5})""").find(url)?.let {
        return it.groupValues[1].toInt() to it.groupValues[2].toInt()
    }
    val width = Regex("""[?&_/](?:w|width)[=_]?(\d{2,5})""").find(url)
        ?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val height = Regex("""[?&_/](?:h|height)[=_]?(\d{2,5})""").find(url)
        ?.groupValues?.get(1)?.toIntOrNull()
    return width to (height ?: width)
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (!isString && content == "null") null else content

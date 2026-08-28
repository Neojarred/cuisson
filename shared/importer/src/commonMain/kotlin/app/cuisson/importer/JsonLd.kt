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
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (!isString && content == "null") null else content

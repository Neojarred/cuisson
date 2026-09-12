package app.cuisson.importer

import app.cuisson.domain.SourceNote
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/**
 * Reads the publisher's own recipe notes out of the page.
 *
 * These are not in the structured data, and they are the material an ingredient means
 * when it says "(Note 1a)". Eight of the twenty-seven pages in the corpus carry them.
 *
 * Notes are found by their container, which recipe plugins label, or by a heading that
 * says Notes, Tips, Variations or their French equivalents. What follows is split into
 * one note per block, and a leading label such as "1a." or "Note 3:" is lifted out so a
 * reference can be resolved against it.
 */
object NotesExtractor {

    fun extract(html: String): List<SourceNote> {
        val document = runCatching { Ksoup.parse(html) }.getOrNull() ?: return emptyList()
        document.select("script, style, noscript").forEach { it.remove() }

        val blocks = fromPluginContainer(document.body())
            ?: fromHeading(document.body())
            ?: return emptyList()

        return blocks
            .map { cleanText(it) }
            .filter { it.length > 12 && !isFurniture(it) }
            .distinct()
            .take(MAX_NOTES)
            .map { text ->
                val match = LABEL.find(text)
                SourceNote(
                    label = match?.groupValues?.get(1)?.trim(),
                    text = if (match != null) text.removeRange(match.range).trim() else text,
                )
            }
    }

    /**
     * A recipe plugin that names its notes container is the reliable case, and the
     * blocks are its direct children. Reading only leaf elements loses RecipeTin Eats'
     * labelled notes, because each is a span whose label sits in a nested strong.
     */
    private fun fromPluginContainer(body: Element?): List<String>? {
        if (body == null) return null
        CONTAINER_CLASSES.forEach { name ->
            // "wprm-recipe-notes" also matches "wprm-recipe-notes-container", and the
            // wrapper's only children are a heading and the real list, which is how
            // RecipeTin Eats' nine notes came out as two. The element holding the most
            // blocks is the list itself.
            val container = body.select("[class*=$name]")
                .filter { it.text().length > 20 }
                .maxByOrNull { it.children().size } ?: return@forEach
            val children = container.children().map { it.text() }.filter { it.isNotBlank() }
            return children.ifEmpty { listOf(container.text()) }
        }
        return null
    }

    /**
     * Otherwise a heading, and the notes are what follows it up to the next heading.
     *
     * Taking the heading's parent instead swept up whatever else lived in it: Bon Appetit
     * produced "Recipe notes Back to top Triangle", which is page furniture rather than
     * anything about the recipe.
     */
    private fun fromHeading(body: Element?): List<String>? {
        if (body == null) return null
        val heading = body.select("h2, h3, h4, h5").firstOrNull { element ->
            val text = normaliseHeading(element.text())
            text.length <= 24 && HEADINGS.any { text == it || text.startsWith("$it ") }
        } ?: return null

        val following = mutableListOf<String>()
        var sibling = heading.nextElementSibling()
        while (sibling != null && sibling.tagName().lowercase() !in HEADING_TAGS) {
            sibling.text().takeIf { it.isNotBlank() }?.let(following::add)
            sibling = sibling.nextElementSibling()
        }
        return following.ifEmpty { null }
    }

    /**
     * Navigation and buttons near a notes heading, and the heading itself. "Recipe
     * Notes:" and "Mes notes personnelles" name the section rather than being in it.
     */
    private fun isFurniture(text: String): Boolean {
        val lower = text.lowercase()
        if (FURNITURE.any { lower.contains(it) }) return true
        val heading = normaliseHeading(text)
        return heading.length <= 24 && HEADINGS.any { heading == it || heading.startsWith("$it ") }
    }

    private fun findContainer(body: Element?): Element? {
        if (body == null) return null

        // A plugin that names its notes container is the reliable case.
        CONTAINER_CLASSES.forEach { name ->
            body.select("[class*=$name]").firstOrNull { it.text().length > 20 }?.let { return it }
        }

        // Otherwise a heading, whose notes are its following siblings.
        val heading = body.select("h2, h3, h4, h5, strong, b").firstOrNull { element ->
            val text = normaliseHeading(element.text())
            text.length <= 24 && HEADINGS.any { text == it || text.startsWith("$it ") }
        } ?: return null
        return heading.parent()
    }

    private fun normaliseHeading(text: String): String =
        text.lowercase()
            .replace(Regex("[àâä]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .filter { it.isLetterOrDigit() || it == ' ' }
            .trim()

    private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    private val FURNITURE = listOf(
        "back to top", "jump to recipe", "print recipe", "save recipe", "rate this",
        "leave a comment", "subscribe", "advertisement", "share this",
    )

    private val CONTAINER_CLASSES = listOf(
        "wprm-recipe-notes", "tasty-recipes-notes", "mv-create-notes", "recipe-notes",
    )

    private val HEADINGS = listOf(
        "notes", "note", "recipe notes", "tips", "tip", "variations", "variation",
        "substitutions", "remarques", "remarque", "astuces", "astuce", "conseils",
        "conseil", "note du chef", "nos conseils", "mes notes personnelles",
        "notes personnelles",
    )

    /** "1a. Chillies -", "Note 3:", "2)" at the start of a note. */
    private val LABEL = Regex("""^(?:note\s*)?(\d+[a-z]?)\s*[.):\-]\s*""", RegexOption.IGNORE_CASE)

    /** A page with more than this many notes is almost certainly the wrong container. */
    private const val MAX_NOTES = 25
}

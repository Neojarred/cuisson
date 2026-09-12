package app.cuisson.importer

import app.cuisson.domain.ExtractionWarning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every page here is written for the test rather than copied from a site, but every shape
 * it exercises was observed on a real one. The wrapper variants, the three forms of
 * recipeYield and the unnormalised duration all came from fetching three ordinary recipe
 * sites and looking at what they actually publish.
 */
class StructuredExtractorTest {

    private fun page(jsonLd: String, extra: String = "") = """
        <!doctype html><html><head><title>A page</title>$extra
        <script type="application/ld+json">$jsonLd</script>
        </head><body><p>Blog preamble nobody wants.</p></body></html>
    """.trimIndent()

    @Test
    fun `reads a recipe published as a single object`() {
        val html = page(
            """
            {"@context":"https://schema.org","@type":"Recipe","name":"Classic lasagne",
             "recipeYield":6,"totalTime":"PT1H15M",
             "recipeIngredient":["500g beef mince","2 onions, chopped"],
             "recipeInstructions":[
               {"@type":"HowToStep","text":"Brown the mince."},
               {"@type":"HowToStep","text":"Layer and bake."}]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html, "https://example.com/l"))
        assertEquals("Classic lasagne", draft.title)
        assertEquals("6", draft.servingsText)
        assertEquals(75, draft.totalMinutes)
        assertEquals(listOf("500g beef mince", "2 onions, chopped"), draft.ingredientTexts)
        assertEquals(2, draft.steps.size)
        assertTrue(draft.looksUsable)
    }

    @Test
    fun `finds the recipe inside a graph of unrelated nodes`() {
        val html = page(
            """
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebSite","name":"A blog"},
              {"@type":"Person","name":"The author"},
              {"@type":"Recipe","name":"Beef stew","recipeYield":["6"],"totalTime":"PT200M",
               "recipeIngredient":["1 kg chuck steak"],
               "recipeInstructions":[{"@type":"HowToStep","text":"Simmer for a long time."}]}]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertEquals("Beef stew", draft.title)
        // recipeYield came back as an array on a real site. It still means six.
        assertEquals("6", draft.servingsText)
        assertEquals(200, draft.totalMinutes)
    }

    @Test
    fun `keeps a yield that carries its own unit`() {
        val html = page(
            """
            {"@type":"Recipe","name":"Quiche lorraine","recipeYield":"8 personnes",
             "inLanguage":"fr","recipeIngredient":["200 g de lardons"],
             "recipeInstructions":[{"@type":"HowToStep","text":"Prechauffer le four."}]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertEquals("8 personnes", draft.servingsText)
        assertEquals("fr", draft.language)
    }

    @Test
    fun `reads instructions given as plain strings`() {
        val html = page(
            """
            {"@type":"Recipe","name":"Toast",
             "recipeIngredient":["2 slices of bread"],
             "recipeInstructions":["Toast the bread.","Butter it."]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertEquals(listOf("Toast the bread.", "Butter it."), draft.steps.map { it.text })
    }

    @Test
    fun `keeps section labels from grouped instructions`() {
        val html = page(
            """
            {"@type":"Recipe","name":"Two part dish",
             "recipeIngredient":["flour"],
             "recipeInstructions":[
               {"@type":"HowToSection","name":"For the sauce","itemListElement":[
                 {"@type":"HowToStep","text":"Soften the onion."}]},
               {"@type":"HowToSection","name":"To assemble","itemListElement":[
                 {"@type":"HowToStep","text":"Layer it up."}]}]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertEquals(listOf("For the sauce", "To assemble"), draft.steps.map { it.sectionLabel })
    }

    @Test
    fun `strips markup and decodes entities that publishers embed`() {
        val html = page(
            """
            {"@type":"Recipe","name":"Sauce &amp; things",
             "recipeIngredient":["&frac12; tsp salt","1 &amp; a bit"],
             "recipeInstructions":[{"@type":"HowToStep","text":"<p>Simmer <b>gently</b>.</p>"}]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertEquals("Sauce & things", draft.title)
        assertEquals(listOf("½ tsp salt", "1 & a bit"), draft.ingredientTexts)
        assertEquals("Simmer gently.", draft.steps.single().text)
        assertTrue(ExtractionWarning.STEPS_CONTAINED_MARKUP in draft.warnings)
    }

    @Test
    fun `accepts a type given as an array`() {
        val html = page(
            """
            {"@type":["Recipe","NewsArticle"],"name":"Both at once",
             "recipeIngredient":["water"],"recipeInstructions":["Boil it."]}
            """.trimIndent()
        )
        assertEquals("Both at once", StructuredExtractor.extract(html)?.title)
    }

    @Test
    fun `a broken block does not cost us the recipe`() {
        val html = """
            <!doctype html><html><head>
            <script type="application/ld+json">{ this is not json at all }</script>
            <script type="application/ld+json">
              {"@type":"Recipe","name":"Survivor","recipeIngredient":["salt"],
               "recipeInstructions":["Season."]}
            </script></head><body></body></html>
        """.trimIndent()
        assertEquals("Survivor", StructuredExtractor.extract(html)?.title)
    }

    @Test
    fun `says so when the recipe points at notes we did not capture`() {
        val html = page(
            """
            {"@type":"Recipe","name":"Rendang","recipeIngredient":["galangal (Note 3)"],
             "recipeInstructions":[{"@type":"HowToStep","text":"Reduce the sauce (see Note 9)."}]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertTrue(ExtractionWarning.REFERENCES_UNCAPTURED_NOTES in draft.warnings)
    }

    @Test
    fun `reports what is missing instead of pretending`() {
        val html = page("""{"@type":"Recipe","name":"Nothing much"}""")
        val draft = assertNotNull(StructuredExtractor.extract(html))
        assertTrue(ExtractionWarning.NO_INGREDIENTS in draft.warnings)
        assertTrue(ExtractionWarning.NO_STEPS in draft.warnings)
        assertTrue(!draft.looksUsable)
    }

    @Test
    fun `never carries the publisher's prose`() {
        val html = page(
            """
            {"@type":"Recipe","name":"Grandmother's stew",
             "description":"A long and personal story about my grandmother's kitchen.",
             "recipeIngredient":["beef"],"recipeInstructions":["Cook it."]}
            """.trimIndent()
        )
        val draft = assertNotNull(StructuredExtractor.extract(html))
        // The description is the one genuinely copyrighted part of a recipe page, and it
        // is also the waffle. There is deliberately nowhere for it to go.
        assertTrue(draft.ingredientTexts.none { it.contains("grandmother", ignoreCase = true) })
        assertTrue(draft.steps.none { it.text.contains("grandmother", ignoreCase = true) })
    }

    @Test
    fun `returns null on a page with no recipe`() {
        assertNull(StructuredExtractor.extract("<html><body>Just a blog post.</body></html>"))
        assertNull(
            StructuredExtractor.extract(
                page("""{"@type":"NewsArticle","headline":"Not a recipe"}""")
            )
        )
    }
}

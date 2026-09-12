package app.cuisson.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shapes taken from real pages, and every rule here was forced by one of them. */
class NotesExtractorTest {

    /** RecipeTin Eats: a plugin container, with the label inside a nested element. */
    private val pluginPage = """
        <html><body>
        <div class="wprm-recipe-notes-container">
          <h3 class="wprm-recipe-notes-header">Recipe Notes:</h3>
          <div class="wprm-recipe-notes">
            <span><strong>1a. Chillies &ndash;</strong> 12 dried chillies makes a fairly spicy curry.</span>
            <span><strong>1b. Onion:</strong> Use a brown or yellow onion about the size of a tennis ball.</span>
            <span>3. Galangal is a root related to ginger, sold in Asian grocers.</span>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `reads the notes and their labels`() {
        val notes = NotesExtractor.extract(pluginPage)
        assertEquals(listOf("1a", "1b", "3"), notes.map { it.label })
        assertTrue(notes[0].text.startsWith("Chillies"))
        assertTrue(notes[2].text.startsWith("Galangal"))
    }

    @Test
    fun `takes the list rather than the wrapper around it`() {
        // "wprm-recipe-notes" also matches "wprm-recipe-notes-container", whose only
        // children are a heading and the list. Matching the wrapper gave two notes.
        assertEquals(3, NotesExtractor.extract(pluginPage).size)
    }

    @Test
    fun `the name of the section is not a note`() {
        assertTrue(NotesExtractor.extract(pluginPage).none { it.text.contains("Recipe Notes") })
    }

    @Test
    fun `reads notes that follow a heading`() {
        val page = """
            <html><body><article>
              <h2>Ingredients</h2><p>Not a note.</p>
              <h3>Notes</h3>
              <p>Freeze in an airtight container for up to a week.</p>
              <p>Swap the cream for creme fraiche if you prefer it sharper.</p>
              <h3>Nutrition</h3>
              <p>Calories 420 per serving.</p>
            </article></body></html>
        """.trimIndent()
        val notes = NotesExtractor.extract(page)
        assertEquals(2, notes.size)
        assertTrue(notes.none { it.text.contains("Calories") })
        assertTrue(notes.none { it.text.contains("Not a note") })
    }

    @Test
    fun `page furniture near a heading is not a note`() {
        // Bon Appetit produced "Recipe notes Back to top Triangle" from its navigation.
        val page = """
            <html><body><div>
              <h3>Recipe notes</h3>
              <p>Back to top</p>
              <p>This dough keeps for three days in the fridge and improves on the second.</p>
            </div></body></html>
        """.trimIndent()
        val notes = NotesExtractor.extract(page)
        assertEquals(1, notes.size)
        assertTrue(notes.single().text.startsWith("This dough"))
    }

    @Test
    fun `reads french headings`() {
        val page = """
            <html><body><div>
              <h3>Astuces</h3>
              <p>Si votre confiture est trop liquide, prolongez la cuisson de dix minutes.</p>
            </div></body></html>
        """.trimIndent()
        assertEquals(1, NotesExtractor.extract(page).size)
    }

    @Test
    fun `a page with no notes yields none`() {
        assertTrue(NotesExtractor.extract("<html><body><p>Just a recipe.</p></body></html>").isEmpty())
    }
}

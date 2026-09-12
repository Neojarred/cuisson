package app.cuisson.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shapes here are copied from how real sites lay their ingredient lists out, and
 * every rule exists because a real page broke an earlier version of this.
 */
class IngredientGrouperTest {

    private val ingredients = listOf(
        "500 g ground beef", "2 onions, chopped", "400 g tinned tomatoes",
        "50 g butter", "50 g plain flour", "500 ml whole milk",
    )

    /** Ricardo's shape: a heading above each nested list. */
    private fun groupedPage() = """
        <html><body><div class="recipe">
          <h2>Ingredients</h2>
          <ul>
            <li>
              <h3 class="subtitle">Meat Sauce</h3>
              <ul>
                <li><div>500 g ground beef</div></li>
                <li><div>2 onions, chopped</div></li>
                <li><div>400 g tinned tomatoes</div></li>
              </ul>
            </li>
            <li>
              <h3 class="subtitle">Béchamel Sauce</h3>
              <ul>
                <li><div>50 g butter</div></li>
                <li><div>50 g plain flour</div></li>
                <li><div>500 ml whole milk</div></li>
              </ul>
            </li>
          </ul>
        </div></body></html>
    """.trimIndent()

    @Test
    fun `recovers the groups a page states`() {
        val grouped = IngredientGrouper.group(groupedPage(), ingredients)
        assertEquals(
            listOf(
                "Meat Sauce", "Meat Sauce", "Meat Sauce",
                "Béchamel Sauce", "Béchamel Sauce", "Béchamel Sauce",
            ),
            grouped.map { it.group },
        )
        assertEquals(ingredients, grouped.map { it.text })
    }

    @Test
    fun `the name of the list is not a group`() {
        val grouped = IngredientGrouper.group(groupedPage(), ingredients)
        assertTrue(grouped.none { it.group == "Ingredients" })
    }

    @Test
    fun `one heading partway down is enough, because toppings are optional`() {
        val page = """
            <html><body><div class="recipe"><ul>
              <li>500 g ground beef</li>
              <li>2 onions, chopped</li>
              <li>400 g tinned tomatoes</li>
              <h4>Topping options:</h4>
              <li>50 g butter</li>
              <li>50 g plain flour</li>
              <li>500 ml whole milk</li>
            </ul></div></body></html>
        """.trimIndent()
        val grouped = IngredientGrouper.group(page, ingredients)
        assertNull(grouped[0].group)
        // The colon belongs to the page's typography, not to the group's name.
        assertEquals("Topping options", grouped[5].group)
    }

    @Test
    fun `an ingredient the page does not state keeps the group above it`() {
        // "salt" is too short to locate safely, so it has no row of its own. Leaving it
        // ungrouped split Meat Sauce in two and printed that heading twice.
        val withSalt = listOf(
            "500 g ground beef", "salt", "2 onions, chopped", "400 g tinned tomatoes",
            "50 g butter", "50 g plain flour", "500 ml whole milk",
        )
        val page = """
            <html><body><div class="recipe"><ul>
              <li>
                <h3 class="subtitle">Meat Sauce</h3>
                <ul>
                  <li><div>500 g ground beef</div></li>
                  <li><div>salt</div></li>
                  <li><div>2 onions, chopped</div></li>
                  <li><div>400 g tinned tomatoes</div></li>
                </ul>
              </li>
              <li>
                <h3 class="subtitle">Bechamel Sauce</h3>
                <ul>
                  <li><div>50 g butter</div></li>
                  <li><div>50 g plain flour</div></li>
                  <li><div>500 ml whole milk</div></li>
                </ul>
              </li>
            </ul></div></body></html>
        """.trimIndent()
        val groups = IngredientGrouper.group(page, withSalt).map { it.group }
        assertEquals("Meat Sauce", groups[1])
        assertEquals(listOf("Meat Sauce", "Bechamel Sauce"), groups.filterNotNull().distinct())
        // One change of group across the whole list, so each heading is printed once.
        assertEquals(1, groups.zipWithNext().count { (a, b) -> a != b })
    }

    @Test
    fun `a page with no headings is left flat`() {
        val page = """
            <html><body><ul>
              ${ingredients.joinToString("\n") { "<li>$it</li>" }}
            </ul></body></html>
        """.trimIndent()
        assertTrue(IngredientGrouper.group(page, ingredients).all { it.group == null })
    }

    @Test
    fun `a method's own headings are never ingredient groups`() {
        // Bon Appetit puts ingredients and steps inside one container, and an earlier
        // version reported "Step 1" and "Recipe information" as ingredient groups.
        val page = """
            <html><body><div class="recipe">
              <h3>Recipe information</h3>
              <ul>${ingredients.joinToString("") { "<li>$it</li>" }}</ul>
              <h3>Step 1</h3><p>Brown the beef in a wide pan.</p>
              <h3>Step 2</h3><p>Make the sauce and layer it up.</p>
            </div></body></html>
        """.trimIndent()
        assertTrue(IngredientGrouper.group(page, ingredients).all { it.group == null })
    }

    @Test
    fun `a page whose ingredients are rendered by javascript is left flat`() {
        // Two thirds of the corpus. There is nothing in the fetched HTML to group.
        val page = "<html><body><div id=\"recipe-root\"></div></body></html>"
        assertTrue(IngredientGrouper.group(page, ingredients).all { it.group == null })
    }

    @Test
    fun `a single ingredient is never grouped`() {
        assertTrue(IngredientGrouper.group(groupedPage(), listOf("500 g ground beef")).all { it.group == null })
    }
}

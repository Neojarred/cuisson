package app.cuisson.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SourceUrlKeyTest {

    @Test
    fun `the same page shared in different ways matches`() {
        val plain = sourceUrlKey("https://www.marmiton.org/recettes/recette_quiche_30283.aspx")
        assertEquals(plain, sourceUrlKey("http://marmiton.org/recettes/recette_quiche_30283.aspx"))
        assertEquals(plain, sourceUrlKey("https://www.marmiton.org/recettes/recette_quiche_30283.aspx/"))
        assertEquals(
            plain,
            sourceUrlKey("https://www.marmiton.org/recettes/recette_quiche_30283.aspx?utm_source=whatsapp"),
        )
        assertEquals(
            plain,
            sourceUrlKey("https://www.marmiton.org/recettes/recette_quiche_30283.aspx#ingredients"),
        )
    }

    @Test
    fun `different pages stay different`() {
        assertNotEquals(
            sourceUrlKey("https://www.marmiton.org/recettes/recette_quiche_30283.aspx"),
            sourceUrlKey("https://www.marmiton.org/recettes/recette_quiche_18215.aspx"),
        )
        assertNotEquals(
            sourceUrlKey("https://www.bbcgoodfood.com/recipes/classic-lasagne"),
            sourceUrlKey("https://www.bbcgoodfood.com/recipes/chicken-tikka-masala"),
        )
    }

    @Test
    fun `a recipe with no address has no key`() {
        assertNull(sourceUrlKey(null))
        assertNull(sourceUrlKey("  "))
    }
}

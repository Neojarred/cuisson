package app.cuisson.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Most of the item names here are taken, messiness and all, from what the parser made of a
 * real library of twenty-five recipes in English and French. The rules they guard were
 * settled with the user: the same ingredient merges, a variety stays itself, and anything
 * uncertain becomes its own ingredient rather than being merged into a guess.
 */
class IngredientResolverTest {

    private fun id(item: String, aliases: Map<String, String> = emptyMap()) =
        resolveIngredient(item, aliases).id

    @Test
    fun `no two ingredients claim the same written form`() {
        assertEquals(emptyList(), IngredientCatalogue.collisions())
    }

    @Test
    fun `every family exists and every ingredient reaches an aisle`() {
        IngredientCatalogue.all.forEach { ingredient ->
            ingredient.family?.let {
                assertTrue(IngredientCatalogue.find(it) != null, "${ingredient.id} has no family $it")
            }
            if (ingredient.family == null) {
                assertTrue(ingredient.aisle != null, "${ingredient.id} has no aisle and no family")
            }
        }
    }

    @Test
    fun `the same ingredient in either language is one ingredient`() {
        assertEquals("garlic", id("garlic cloves"))
        assertEquals("garlic", id("garlic clove"))
        assertEquals("garlic", id("'ail"))
        assertEquals("garlic", id("ail"))
        assertEquals("butter", id("beurre"))
        assertEquals("salt", id("sel"))
        assertEquals("egg", id("oeufs"))
        assertEquals("egg", id("œufs"))
    }

    /** "Onions" and "red onions" stay two lines. They sit together because they share a family. */
    @Test
    fun `a variety is not the thing it is a variety of`() {
        assertNotEquals(id("onion"), id("red onion"))
        assertEquals("onion", IngredientCatalogue.find(id("red onion"))?.family)
        assertNotEquals(id("sugar"), id("sucre glace"))
        assertNotEquals(id("sugar"), id("light brown sugar"))
        assertNotEquals(id("brown sugar"), id("dark brown sugar"))
    }

    @Test
    fun `tomato puree is not a kind of tomato`() {
        val puree = resolveIngredient("tomato purée")
        assertEquals("tomato-puree", puree.id)
        assertNull(puree.ingredient?.family)
        assertEquals(Aisle.TINS_JARS, puree.aisle)
    }

    @Test
    fun `a different form is a different thing to buy`() {
        assertNotEquals(id("dried chilies"), id("chilli"))
        assertNotEquals(id("ground cumin"), id("cumin seeds"))
    }

    @Test
    fun `water is never shoppable`() {
        assertFalse(resolveIngredient("water").shoppable)
        assertFalse(resolveIngredient("'eau").shoppable)
        assertFalse(resolveIngredient("boiling water").shoppable)
    }

    @Test
    fun `an aisle follows the family unless the ingredient names its own`() {
        assertEquals(Aisle.PRODUCE, resolveIngredient("red onion").aisle)
        assertEquals(Aisle.SPICES, resolveIngredient("dried chilies").aisle)
        assertEquals(Aisle.PRODUCE, resolveIngredient("chilli").aisle)
    }

    /** Every one of these is a real item name as the parser left it. */
    @Test
    fun `the untidy names a real library produces`() {
        assertEquals("peanut-oil", id("c. à s. d'huile d'arachide"))
        assertEquals("chopped-tomatoes", id("x 400g cans chopped tomatoes"))
        assertEquals("chopped-tomatoes", id("large can diced tomatoes"))
        assertEquals("parsley", id("finely chopped fresh parsley"))
        assertEquals("chicken-breast", id("boneless, skinless chicken breasts cut into 2.5cm cubes"))
        assertEquals("green-lentils", id("brown or green lentils"))
        assertEquals("green-lentils", id("lentilles vertes ou brunes"))
        assertEquals("onion", id("medium yellow or white onion"))
        assertEquals("salt", id("diamond crystal or ¾ tsp. morton kosher salt"))
        assertEquals("salt", id("salt, more to taste"))
        assertEquals("flour", id("king arthur unbleached all-purpose flour"))
        assertEquals("lemon", id("le jus d'un demi-citron"))
        assertEquals("chilli-flakes", id("une pincée de flocons de poivre de cayenne"))
        assertEquals("red-wine", id("bouteille de vin de bourgogne rouge"))
        assertEquals("dark-chocolate", id("chocolat noir à pâtisser 55 et 70 %"))
        assertEquals("olive-oil", id("tasse d’huile d’olive extra-vierge"))
        assertEquals("shortcrust-pastry", id("pâte à tarte maison ou du commerce"))
        assertEquals("chipotle-adobo", id("chipotle peppers from a can of chipotles in adobo"))
        assertEquals("red-pepper", id("red peppers deseeded and cut into chunks"))
        assertEquals("puff-pastry", id("sheet of puff pastry"))
        assertEquals("butter", id("knob of butter"))
    }

    /**
     * Words that look like preparation but are part of the product. The text is tried as
     * written before any word is removed, or these become plain beef and plain tomatoes.
     */
    @Test
    fun `a product named after how it was prepared keeps its name`() {
        assertEquals("beef-mince", id("bœuf haché"))
        assertEquals("beef-mince", id("lean ground beef"))
        assertEquals("chopped-tomatoes", id("chopped tomatoes"))
    }

    /** Aisle first, meat second: this is in a jar. */
    @Test
    fun `a sauce named after meat is not meat`() {
        assertEquals("tikka-paste", id("chicken tikka masala paste"))
        assertEquals(Aisle.TINS_JARS, resolveIngredient("harissa paste").aisle)
    }

    @Test
    fun `something unknown becomes its own ingredient and keeps one identity`() {
        val first = resolveIngredient("yuzu kosho")
        assertTrue(first.learned)
        assertEquals(first.id, resolveIngredient("Yuzu Kosho").id)
        assertEquals("yuzu kosho", first.name)
    }

    @Test
    fun `a correction is remembered and wins over the catalogue`() {
        val unknown = resolveIngredient("yuzu kosho")
        assertEquals("chilli-flakes", id("yuzu kosho", mapOf(unknown.key to "chilli-flakes")))

        val lardons = resolveIngredient("lardons")
        assertEquals("bacon", id("lardons", mapOf(lardons.key to "bacon")))
    }
}

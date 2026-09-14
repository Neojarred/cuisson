package app.cuisson.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parser feeds the shopping list and never the screen, so what matters here is
 * whether two recipes asking for the same thing can be seen to be asking for the same
 * thing. A wrong name is a line that will not consolidate; a wrong amount is a line that
 * will consolidate into the wrong number, which is worse.
 */
class IngredientParserTest {

    @Test
    fun `an amount, a unit and a name`() {
        val parsed = parseIngredient("200 g mushrooms")
        assertEquals(200.0, parsed.quantityMin)
        assertEquals("g", parsed.unit)
        assertEquals(UnitKind.METRIC, parsed.unitSystem)
        assertEquals("mushrooms", parsed.item)
        assertEquals(1f, parsed.confidence)
    }

    @Test
    fun `the unit can be stuck to the number`() {
        val parsed = parseIngredient("150ml natural yogurt")
        assertEquals(150.0, parsed.quantityMin)
        assertEquals("ml", parsed.unit)
        assertEquals("natural yogurt", parsed.item)
    }

    @Test
    fun `a connector between the unit and the name is not part of the name`() {
        assertEquals("mushrooms", parseIngredient("200g of mushrooms").item)
        assertEquals("lardons", parseIngredient("125 g de lardons").item)
    }

    /**
     * The counting words. Without them the parser decides the thing being bought is
     * "cloves", and garlic never meets garlic on a shopping list.
     */
    @Test
    fun `a counting word is a unit and not the thing itself`() {
        val garlic = parseIngredient("5 cloves garlic, minced")
        assertEquals("clove", garlic.unit)
        assertEquals(UnitKind.COUNT, garlic.unitSystem)
        assertEquals("garlic", garlic.item)
        assertEquals("minced", garlic.preparation)

        assertEquals("can", parseIngredient("2 cans chopped tomatoes").unit)

        val mozzarella = parseIngredient("1 boule de mozzarella")
        assertEquals("ball", mozzarella.unit)
        assertEquals("mozzarella", mozzarella.item)
    }

    @Test
    fun `what the recipe wants done to it is kept apart from what it is`() {
        val parsed = parseIngredient("1 small onion, finely chopped (Note 1b)")
        assertEquals("small onion", parsed.item)
        // The note reference is dropped from the parse and kept in the line itself, which
        // is what the reader sees. Nothing internal needs to carry it.
        assertEquals("finely chopped", parsed.preparation)
    }

    /**
     * A comma does two jobs in an ingredient list and the parser has to tell them apart.
     * Splitting the second of these gave a shopping list an item called "boneless".
     */
    @Test
    fun `a comma inside a name does not divide the line`() {
        assertEquals(
            "boneless, skinless chicken breasts cut into cubes",
            parseIngredient("8 boneless, skinless chicken breasts cut into cubes").item,
        )
        assertEquals("chuck steak", parseIngredient("2 lb chuck steak, or other beef").item)
    }

    @Test
    fun `a comma inside a bracket is not the dividing comma`() {
        // This was being read as a thing called "oil (vegetable".
        assertEquals(
            "oil",
            parseIngredient("2 tbsp oil (vegetable, canola or peanut oil)").item,
        )
    }

    @Test
    fun `quebec measures its teaspoons in tea`() {
        val parsed = parseIngredient("2 cuillères à thé de cumin moulu")
        assertEquals("tsp", parsed.unit)
        assertEquals("cumin moulu", parsed.item)
    }

    @Test
    fun `a bracket in the name is dropped from the name`() {
        assertEquals("corn kernels", parseIngredient("1 cup corn kernels (fresh or frozen)").item)
    }

    @Test
    fun `a conversion is kept as the other reading of the same amount`() {
        val parsed = parseIngredient("3 tbsp (45 ml) olive oil")
        assertEquals(3.0, parsed.quantityMin)
        assertEquals("tbsp", parsed.unit)
        assertEquals("ml", parsed.altUnit)
        assertEquals(UnitKind.METRIC, parsed.altSystem)
        assertEquals("olive oil", parsed.item)
    }

    @Test
    fun `a line that is only a name is still a line`() {
        val salt = parseIngredient("sel")
        assertNull(salt.quantityMin)
        assertNull(salt.unit)
        assertEquals("sel", salt.item)
        // Half read, and the shopping list needs to know that.
        assertEquals(0.5f, salt.confidence)
    }

    @Test
    fun `french spoons are spoons`() {
        val parsed = parseIngredient("1 cuillère à café extrait de vanille")
        assertEquals("tsp", parsed.unit)
        assertEquals("extrait de vanille", parsed.item)
    }

    @Test
    fun `a range keeps both ends`() {
        val parsed = parseIngredient("2 to 3 tbsp lemon juice")
        assertEquals(2.0, parsed.quantityMin)
        assertEquals(3.0, parsed.quantityMax)
        assertEquals("tbsp", parsed.unit)
    }

    @Test
    fun `optional is noticed wherever the line says it`() {
        assertTrue(parseIngredient("1 tbsp chives (optional)").optional)
        assertTrue(parseIngredient("Freshly ground black pepper, to taste").optional)
        assertTrue(!parseIngredient("2 large eggs").optional)
    }

    @Test
    fun `a fraction is an amount like any other`() {
        assertEquals(0.5, parseIngredient("½ tsp fennel seeds").quantityMin)
        assertEquals(1.5, parseIngredient("1 1/2 tbsp fresh ginger").quantityMin)
        assertEquals(0.25, parseIngredient("1/4 de tasse d'huile d'olive").quantityMin)
    }

    @Test
    fun `a decimal comma is a decimal`() {
        val parsed = parseIngredient("1,5 kg de joue de boeuf")
        assertEquals(1.5, parsed.quantityMin)
        assertEquals("kg", parsed.unit)
    }
}

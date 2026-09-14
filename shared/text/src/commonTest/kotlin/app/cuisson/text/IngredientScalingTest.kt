package app.cuisson.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every shape here was taken from a line in a real library of twenty-five recipes. The
 * lines themselves are rewritten, because other people's recipes do not belong in this
 * repository, but the awkwardness is theirs.
 *
 * The rule these are guarding is that scaling replaces numbers and nothing else. A line
 * that comes back with its wording changed is a worse bug than one that comes back
 * unscaled, because the reader cannot see that it happened.
 */
class IngredientScalingTest {

    @Test
    fun `a plain amount doubles`() {
        assertEquals("4 large eggs", scaleIngredient("2 large eggs", 2.0))
        assertEquals("300ml natural yogurt", scaleIngredient("150ml natural yogurt", 2.0))
    }

    @Test
    fun `a vulgar fraction becomes something you can measure`() {
        assertEquals("1 tsp fennel seeds", scaleIngredient("½ tsp fennel seeds", 2.0))
        assertEquals("1 1/2 lb lean ground beef", scaleIngredient("¾ lb lean ground beef", 2.0))
        // 1½ written with no space, which is how several sites write it.
        assertEquals("3 cups brown sugar", scaleIngredient("1½ cups brown sugar", 2.0))
    }

    @Test
    fun `halving gives a fraction rather than a decimal`() {
        assertEquals("1/2 tsp salt", scaleIngredient("1 tsp salt", 0.5))
        assertEquals("1 1/2 tbsp butter", scaleIngredient("3 tbsp butter", 0.5))
        assertEquals("2/3 cups milk", scaleIngredient("1 1/3 cups milk", 0.5))
    }

    /**
     * "2 oignon" is not French and "2/3 cups" is not English, and both are left that way
     * on purpose. Making the grammar agree means editing the author's words, which is the
     * one thing scaling must not do: a parser that conjugates will get it wrong somewhere
     * that matters, and an unchanged word next to a changed number is obvious to a reader
     * in a way that a quietly rewritten line is not.
     */
    @Test
    fun `the number changes and the words do not`() {
        assertEquals("2 oignon jaune tranché", scaleIngredient("1 oignon jaune tranché", 2.0))
        assertEquals("2 cloves garlic, minced", scaleIngredient("1 cloves garlic, minced", 2.0))
    }

    /**
     * A line that states the same amount twice has to move both halves or it contradicts
     * itself, and a cook reading the metric half would be told the wrong thing.
     */
    @Test
    fun `a conversion in brackets moves with the amount it converts`() {
        assertEquals(
            "1 1/2 lb (680 g) lean ground beef",
            scaleIngredient("¾ lb (340 g) lean ground beef", 2.0),
        )
        assertEquals("6 tbsp (90 ml) olive oil", scaleIngredient("3 tbsp (45 ml) olive oil", 2.0))
    }

    @Test
    fun `a conversion after a slash moves too`() {
        assertEquals(
            "4 lb/ 2 kg chuck steak, cut into 4cm cubes",
            scaleIngredient("2 lb/ 1 kg chuck steak, cut into 4cm cubes", 2.0),
        )
        assertEquals(
            "800ml / 28 oz coconut milk",
            scaleIngredient("400ml / 14 oz coconut milk", 2.0),
        )
    }

    /**
     * The distinction the whole design turns on. "1 (14-ounce) can" is one tin of a fixed
     * size, so doubling means two tins of that same size, not one tin twice as big.
     */
    @Test
    fun `a size in brackets is not a conversion`() {
        assertEquals(
            "2 (14-ounce) cans diced tomatoes",
            scaleIngredient("1 (14-ounce) cans diced tomatoes", 2.0),
        )
        assertEquals(
            "4 cans (28 oz each) whole plum tomatoes",
            scaleIngredient("2 cans (28 oz each) whole plum tomatoes", 2.0),
        )
    }

    @Test
    fun `a measurement further along the line is left alone`() {
        assertEquals(
            "4 lb beef, cut into 4cm cubes (Note 4)",
            scaleIngredient("2 lb beef, cut into 4cm cubes (Note 4)", 2.0),
        )
    }

    @Test
    fun `a line with no amount is returned as it was`() {
        assertEquals("salt", scaleIngredient("salt", 2.0))
        assertEquals("Dried oregano", scaleIngredient("Dried oregano", 3.0))
        assertEquals("freshly ground black pepper", scaleIngredient("freshly ground black pepper", 2.0))
    }

    @Test
    fun `french lines keep their comma and their wording`() {
        assertEquals("3 kg de joue de boeuf", scaleIngredient("1,5 kg de joue de boeuf", 2.0))
        assertEquals("250 g de lardons", scaleIngredient("125 g de lardons", 2.0))
        assertEquals(
            "1/2 de tasse (65 ml) d'huile d'olive",
            scaleIngredient("1/4 de tasse (32,5 ml) d'huile d'olive", 2.0),
        )
    }

    @Test
    fun `a range keeps the writer's own separator`() {
        assertEquals("4 to 6 onions", scaleIngredient("2 to 3 onions", 2.0))
        assertEquals("4 - 6 chillies", scaleIngredient("2 - 3 chillies", 2.0))
    }

    @Test
    fun `a multiplier form scales the count and not the tin`() {
        assertEquals(
            "4 x 400g cans chopped tomatoes",
            scaleIngredient("2 x 400g cans chopped tomatoes", 2.0),
        )
    }

    /**
     * A line that offers the same amount twice over. All four of these came out of one
     * library, and all four used to come back contradicting themselves: doubled at the
     * front, unchanged in the bracket, so whichever half the cook read, one of them lied.
     */
    @Test
    fun `an amount restated a second way moves with it`() {
        assertEquals(
            "24 dried chillies, or 24 large fresh (Note 1a)",
            scaleIngredient("12 dried chillies, or 12 large fresh (Note 1a)", 2.0),
        )
        assertEquals(
            "8 large lime leaves (or 12 small), finely sliced",
            scaleIngredient("4 large lime leaves (or 6 small), finely sliced", 2.0),
        )
        assertEquals(
            "3 cups plus 2 Tbsp. (400 g) plain flour",
            scaleIngredient("1½ cups plus 1 Tbsp. (200 g) plain flour", 2.0),
        )
        // The bracket restates the amount in words this code has never heard of, and
        // every number in it still belongs to that amount.
        assertEquals(
            "1 1/2 cup (3 sticks; 338 g) unsalted butter",
            scaleIngredient("¾ cup (1½ sticks; 169 g) unsalted butter", 2.0),
        )
    }

    /**
     * The counterweight to the test above. These numbers sit near a quantity and are not
     * one, and scaling any of them would be worse than scaling nothing.
     */
    @Test
    fun `a number that is not an amount stays where it is`() {
        // A strength, not a quantity. Doubling it invents 120% chocolate.
        assertEquals(
            "12 oz. (340 g) bittersweet chocolate (60%-70% cacao), chopped",
            scaleIngredient("6 oz. (170 g) bittersweet chocolate (60%-70% cacao), chopped", 2.0),
        )
        // "or" here introduces a method, not another way to measure the same thing.
        assertEquals(
            "4 tsp tamarind puree, or tamarind pulp soaked in 1 tbsp of hot water",
            scaleIngredient("2 tsp tamarind puree, or tamarind pulp soaked in 1 tbsp of hot water", 2.0),
        )
        // "or" introducing a substitute ingredient with no amount of its own.
        assertEquals(
            "4 lb/ 2 kg chuck steak, or other slow cooking beef",
            scaleIngredient("2 lb/ 1 kg chuck steak, or other slow cooking beef", 2.0),
        )
    }

    @Test
    fun `scaling by one changes nothing at all`() {
        val line = "1 1/2 tbsp fresh galangal, finely chopped (Note 3)"
        assertEquals(line, scaleIngredient(line, 1.0))
    }

    @Test
    fun `a number that is not the quantity does not make this a quantity`() {
        // The line opens with a word, so nothing here is an amount to scale.
        assertEquals(
            "Zest of 2 lemons",
            scaleIngredient("Zest of 2 lemons", 2.0),
        )
    }
}

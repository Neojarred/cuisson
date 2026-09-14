package app.cuisson.data

import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Servings
import app.cuisson.domain.Source
import app.cuisson.domain.SourceKind
import app.cuisson.domain.Timings
import app.cuisson.text.Aisle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a shopping list says, given the recipes on it. Each test is one of the decisions
 * made with the user about how a list should read in a shop.
 */
class ConsolidationTest {

    private fun recipe(id: String, servings: Double?, vararg lines: String) = Recipe(
        id = RecipeId(id),
        title = id,
        rawTitle = null,
        source = Source(SourceKind.MANUAL),
        servings = servings?.let { Servings(it) },
        timings = Timings(),
        ingredients = lines.mapIndexed { i, text ->
            IngredientLine(id = "$id-i$i", position = i, rawText = text)
        },
        steps = emptyList(),
        notes = null,
        sourceNotes = emptyList(),
        imagePath = null,
        chapterId = null,
        language = "en",
        extraction = Extraction(ExtractionTier.HAND_WRITTEN, 1f, false),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(1),
    )

    private fun items(vararg recipes: RecipeOnList, own: List<OwnLine> = emptyList()) =
        consolidate(recipes.toList(), own).flatMap { it.items }

    private fun item(name: String, vararg recipes: RecipeOnList, own: List<OwnLine> = emptyList()) =
        items(*recipes, own = own).single { it.name == name }

    @Test
    fun `the same ingredient adds up across recipes and languages`() {
        val garlic = item(
            "Garlic",
            RecipeOnList(recipe("a", null, "2 garlic cloves, minced"), null),
            RecipeOnList(recipe("b", null, "3 gousses d'ail"), null),
        )
        assertEquals("5 cloves", garlic.amount)
        assertEquals(2, garlic.contributions.size)
    }

    @Test
    fun `varieties stay apart and sit together`() {
        val names = consolidate(
            listOf(RecipeOnList(recipe("a", null, "1 red onion", "1 carrot", "2 onions"), null)),
            emptyList(),
        ).single { it.aisle == Aisle.PRODUCE }.items.map { it.name }
        assertEquals(listOf("Carrot", "Onion", "Red onion"), names)
    }

    /** Settled with the user: one line saying both, never a number nobody measured. */
    @Test
    fun `amounts that cannot be added share one line`() {
        val carrots = item(
            "Carrot",
            RecipeOnList(recipe("a", null, "2 carrots, peeled and chopped"), null),
            RecipeOnList(recipe("b", null, "400 g de carottes"), null),
        )
        assertEquals("2, and 400 g", carrots.amount)
    }

    @Test
    fun `a vetted density lets spoons of butter join grams of butter`() {
        val butter = item(
            "Butter",
            RecipeOnList(recipe("a", null, "3 tbsp butter"), null),
            RecipeOnList(recipe("b", null, "100g butter"), null),
        )
        assertEquals("145 g", butter.amount)
    }

    @Test
    fun `small amounts of spice stay in spoons`() {
        val cumin = item(
            "Ground cumin",
            RecipeOnList(recipe("a", null, "1 tsp ground cumin"), null),
            RecipeOnList(recipe("b", null, "1/2 tsp cumin"), null),
        )
        assertEquals("1 1/2 tsp", cumin.amount)
    }

    @Test
    fun `water never reaches a list`() {
        assertEquals(emptyList(), items(RecipeOnList(recipe("a", null, "500 ml water"), null)))
    }

    @Test
    fun `tomato puree is not tomatoes and is with the tins`() {
        val all = items(RecipeOnList(recipe("a", null, "2 tomatoes", "2 tbsp tomato purée"), null))
        assertEquals(2, all.size)
        assertEquals(Aisle.TINS_JARS, all.single { it.name == "Tomato purée" }.aisle)
    }

    @Test
    fun `a recipe on a list is scaled to its servings there`() {
        val flour = item("Flour", RecipeOnList(recipe("a", 4.0, "200 g flour"), 8.0))
        assertEquals("400 g", flour.amount)
        val line = flour.contributions.single() as Contribution.FromRecipe
        assertEquals("400 g flour", line.line)
        assertEquals(8.0, line.servings)
    }

    @Test
    fun `your own line joins the same ingredient as its own share`() {
        val cream = item(
            "Crème fraîche",
            RecipeOnList(recipe("quiche", null, "20 cl de crème fraîche"), null),
            own = listOf(OwnLine("mine", "20 cl crème fraîche")),
        )
        assertEquals("400 ml", cream.amount)
        assertTrue(cream.contributions.any { it is Contribution.FromYou })
    }

    @Test
    fun `a typed amount stands only while the item is unchanged`() {
        val recipes = listOf(RecipeOnList(recipe("a", null, "100g butter"), null))
        val basis = consolidate(recipes, emptyList()).flatMap { it.items }.single().basis

        val kept = consolidate(recipes, emptyList(), typed = mapOf("butter" to TypedAmount("250 g", basis)))
            .flatMap { it.items }.single()
        assertEquals("250 g", kept.amount)
        assertTrue(kept.setByHand)

        val stale = consolidate(recipes, emptyList(), typed = mapOf("butter" to TypedAmount("250 g", "g=1.0;")))
            .flatMap { it.items }.single()
        assertEquals("100 g", stale.amount)
        assertFalse(stale.setByHand)
    }

    @Test
    fun `a french list reads in french`() {
        val groups = consolidate(
            listOf(
                RecipeOnList(recipe("a", null, "2 carottes"), null),
                RecipeOnList(recipe("b", null, "400 g de carottes"), null),
            ),
            emptyList(),
            language = "fr",
        )
        assertEquals("Fruits et légumes", groups.single().label)
        val carrot = groups.single().items.single()
        assertEquals("Carotte", carrot.name)
        assertEquals("2 et 400 g", carrot.amount)
    }

    /** From a real library, where "4 cups (400 g)" of cheese came out as 2.84 litres. */
    @Test
    fun `a line that states its amount in grams too is bought in grams`() {
        val cheese = item(
            "Mozzarella",
            RecipeOnList(recipe("a", null, "4 cups (400 g) mozzarella cheese, grated"), null),
        )
        assertEquals("400 g", cheese.amount)
    }

    @Test
    fun `a bracket after a container is the size of one, not the amount`() {
        val chickpeas = item(
            "Chickpeas",
            RecipeOnList(recipe("a", null, "2 cans (400 g each) chickpeas"), null),
        )
        assertEquals("2 cans", chickpeas.amount)
    }

    @Test
    fun `tins behind their size are counted as tins`() {
        val beans = item(
            "Kidney beans",
            RecipeOnList(recipe("a", null, "1 (14-ounce) can kidney beans"), null),
            RecipeOnList(recipe("b", null, "1 (14-ounce) can kidney beans, drained"), null),
        )
        assertEquals("2 cans", beans.amount)
    }

    @Test
    fun `a stick of butter is weighed, because a stick is a standard weight`() {
        val butter = item(
            "Butter",
            RecipeOnList(recipe("a", null, "2 sticks (1 cup) unsalted butter, melted"), null),
        )
        assertEquals("225 g", butter.amount)
    }

    @Test
    fun `larger spoonfuls stay readable`() {
        val ginger = item(
            "Ginger",
            RecipeOnList(recipe("a", null, "1 1/2 tbsp fresh ginger, minced"), null),
            RecipeOnList(recipe("b", null, "1 1/2 tbsp fresh ginger, minced"), null),
            RecipeOnList(recipe("c", null, "1 1/2 tbsp fresh ginger, minced"), null),
        )
        assertEquals("4 1/2 tbsp", ginger.amount)
    }

    /** Grams in brackets after a spoon are not what anybody measures baking soda with. */
    @Test
    fun `a spoon with grams beside it stays a spoon`() {
        val soda = item(
            "Baking soda",
            RecipeOnList(recipe("a", null, "¾ tsp. (4 g) baking soda"), null),
            RecipeOnList(recipe("b", null, "1 tsp baking soda"), null),
        )
        assertEquals("1 3/4 tsp", soda.amount)
    }

    @Test
    fun `a knob of butter is not an amount`() {
        val butter = item(
            "Butter",
            RecipeOnList(recipe("a", null, "1 knob of butter"), null),
            RecipeOnList(recipe("b", null, "25g butter"), null),
        )
        assertEquals("25 g", butter.amount)
    }

    @Test
    fun `a line with no amount still puts the thing on the list`() {
        val salt = item("Salt", RecipeOnList(recipe("a", null, "sel"), null))
        assertEquals("", salt.amount)
    }
}

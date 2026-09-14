package app.cuisson.data

import app.cuisson.data.db.CuissonDatabase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * How a Shopping List behaves as it is built, changed and shopped. These run the
 * repository against a real SQLite database, because the rules here are about what happens
 * between reads, and a pure function cannot show that.
 */
class ShoppingRepositoryTest {

    private val database = CuissonDatabase(DatabaseDriverFactory().create())
    private val recipes = RecipeRepository(database).also { it.ensureUnfiled() }
    private val shopping = ShoppingRepository(database, recipes)

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

    private fun items(listId: String = "week") =
        shopping.view(listId, "en")!!.groups.flatMap { it.items }

    private fun item(name: String, listId: String = "week") = items(listId).single { it.name == name }

    private fun lasagneOnAList() {
        recipes.save(recipe("lasagne", 4.0, "100 g butter", "200 g flour"))
        shopping.createList("week", "This week", now = 1)
        shopping.addRecipe("week", RecipeId("lasagne"), 4.0, now = 2)
    }

    @Test
    fun `adding a recipe already on the list updates its servings`() {
        lasagneOnAList()
        shopping.addRecipe("week", RecipeId("lasagne"), 8.0, now = 3)

        val view = shopping.view("week", "en")!!
        assertEquals(1, view.recipes.size)
        assertEquals(8.0, view.recipes.single().servings)
        assertEquals("400 g", item("Flour").amount)
    }

    /** Settled with the user: a list is built, then shopped. Any change resets the ticks. */
    @Test
    fun `any change to what a list holds clears every tick`() {
        lasagneOnAList()
        shopping.setTicked("week", "butter", true)
        shopping.setTicked("week", "flour", true)
        assertTrue(item("Butter").ticked)

        shopping.addOwnLine("week", "o1", "bin bags", now = 3)

        assertTrue(items().none { it.ticked })
    }

    @Test
    fun `ticking is not a change`() {
        lasagneOnAList()
        shopping.setTicked("week", "butter", true)
        shopping.setTicked("week", "flour", true)
        assertTrue(item("Butter").ticked)
        assertTrue(item("Flour").ticked)
    }

    /**
     * Last change wins, per item. Adding something unrelated must not undo an amount the
     * user typed, but changing the recipe that item comes from must.
     */
    @Test
    fun `a typed amount survives changes elsewhere and not changes to itself`() {
        lasagneOnAList()
        shopping.setAmount("week", item("Butter"), "250 g")
        assertEquals("250 g", item("Butter").amount)

        shopping.addOwnLine("week", "o1", "bin bags", now = 3)
        assertEquals("250 g", item("Butter").amount, "an unrelated change undid a typed amount")

        shopping.setServings("week", RecipeId("lasagne"), 8.0)
        assertEquals("200 g", item("Butter").amount)
        assertFalse(item("Butter").setByHand)

        // And it does not come back when the servings return to where they were.
        shopping.setServings("week", RecipeId("lasagne"), 4.0)
        assertEquals("100 g", item("Butter").amount)
    }

    @Test
    fun `removing a recipe takes what it asked for and leaves your own lines`() {
        lasagneOnAList()
        shopping.addOwnLine("week", "o1", "1 lemon", now = 3)

        shopping.removeRecipe("week", RecipeId("lasagne"))

        assertEquals(listOf("Lemon"), items().map { it.name })
    }

    @Test
    fun `deleting a recipe from the library takes it off every list`() {
        lasagneOnAList()
        shopping.createList("party", "Party", now = 1)
        shopping.addRecipe("party", RecipeId("lasagne"), 4.0, now = 2)

        recipes.delete(RecipeId("lasagne"))

        assertEquals(emptyList(), items("week"))
        assertEquals(emptyList(), items("party"))
    }

    @Test
    fun `editing a recipe on a list clears that list's ticks`() {
        lasagneOnAList()
        shopping.setTicked("week", "butter", true)

        val lasagne = recipes.find(RecipeId("lasagne"))!!
        recipes.replace(lasagne.copy(title = "Lasagne al forno"))

        assertTrue(items().none { it.ticked })
    }

    @Test
    fun `an archived list comes back with nothing ticked`() {
        lasagneOnAList()
        shopping.setTicked("week", "butter", true)

        shopping.archiveList("week", now = 5)
        assertTrue(shopping.lists().single().archived)

        shopping.reviveList("week", now = 6)
        assertFalse(shopping.lists().single().archived)
        assertTrue(items().none { it.ticked })
    }

    @Test
    fun `a deleted list is gone with everything on it`() {
        lasagneOnAList()
        shopping.addOwnLine("week", "o1", "bin bags", now = 3)
        shopping.deleteList("week")
        assertNull(shopping.view("week", "en"))
        assertEquals(emptyList(), shopping.lists())
    }

    @Test
    fun `the list used last comes first and archived lists come last`() {
        shopping.createList("a", "A", now = 1)
        shopping.createList("b", "B", now = 2)
        shopping.createList("c", "C", now = 3)
        shopping.archiveList("c", now = 4)
        shopping.touchList("a", now = 5)
        assertEquals(listOf("a", "b", "c"), shopping.lists().map { it.id })
    }

    /** A correction is remembered on the phone and applies to every list from then on. */
    @Test
    fun `saying two things are the same is remembered`() {
        recipes.save(recipe("stew", null, "200 g yuzu kosho"))
        shopping.createList("week", "This week", now = 1)
        shopping.addRecipe("week", RecipeId("stew"), null, now = 2)
        val unknown = items().single()
        assertTrue(unknown.learned)

        shopping.sameAs("week", unknown, "chilli-flakes", now = 3)

        shopping.createList("next", "Next week", now = 4)
        shopping.addRecipe("next", RecipeId("stew"), null, now = 5)
        assertEquals("chilli-flakes", items("next").single().key)
    }

    @Test
    fun `moving an item to another aisle is remembered`() {
        recipes.save(recipe("stew", null, "1 jar yuzu kosho"))
        shopping.createList("week", "This week", now = 1)
        shopping.addRecipe("week", RecipeId("stew"), null, now = 2)

        shopping.moveTo("week", items().single(), Aisle.TINS_JARS)

        assertEquals(Aisle.TINS_JARS, items().single().aisle)
    }
}

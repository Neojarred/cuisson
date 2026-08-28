package app.cuisson.importer

import app.cuisson.domain.ExtractionWarning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextRecipeParserTest {

    @Test
    fun `reads a recipe written with headings`() {
        val draft = TextRecipeParser.parse(
            """
            Pancakes

            Ingredients
            200 g plain flour
            2 eggs
            300 ml milk

            Method
            1. Whisk everything together.
            2. Rest the batter for 30 minutes.
            3. Fry in a hot pan.
            """.trimIndent()
        )
        assertEquals("Pancakes", draft.title)
        assertEquals(listOf("200 g plain flour", "2 eggs", "300 ml milk"), draft.ingredientLines)
        assertEquals(
            listOf(
                "Whisk everything together.",
                "Rest the batter for 30 minutes.",
                "Fry in a hot pan.",
            ),
            draft.steps.map { it.text },
        )
    }

    @Test
    fun `reads french headings`() {
        val draft = TextRecipeParser.parse(
            """
            Crêpes

            Ingrédients
            250 g de farine
            3 oeufs

            Préparation
            Mélanger la farine et les oeufs dans un saladier.
            Laisser reposer une heure avant de cuire.
            """.trimIndent()
        )
        assertEquals("Crêpes", draft.title)
        assertEquals(2, draft.ingredientLines.size)
        assertEquals(2, draft.steps.size)
        assertTrue(draft.steps.first().text.startsWith("Mélanger"))
    }

    @Test
    fun `works with no headings at all`() {
        val draft = TextRecipeParser.parse(
            """
            Garlic bread
            1 baguette
            100 g butter
            3 garlic cloves
            Mash the butter with the crushed garlic and a little salt.
            Spread it into cuts made along the loaf and bake for 15 minutes.
            """.trimIndent()
        )
        assertEquals("Garlic bread", draft.title)
        assertEquals(3, draft.ingredientLines.size)
        assertEquals(2, draft.steps.size)
    }

    @Test
    fun `an amount beats punctuation`() {
        // "2 eggs." ends like a sentence and is still an ingredient.
        val draft = TextRecipeParser.parse("Thing\n2 eggs.\nBeat them well and pour into the tin.")
        assertEquals(listOf("2 eggs."), draft.ingredientLines)
        assertEquals(1, draft.steps.size)
    }

    @Test
    fun `bullets and step numbers are the writer's, not the recipe's`() {
        val draft = TextRecipeParser.parse(
            """
            Soup
            - 2 onions
            * 1 litre stock
            Step 1: Soften the onions gently in a little oil.
            2) Add the stock and simmer for twenty minutes.
            """.trimIndent()
        )
        assertEquals(listOf("2 onions", "1 litre stock"), draft.ingredientLines)
        assertEquals(
            listOf(
                "Soften the onions gently in a little oil.",
                "Add the stock and simmer for twenty minutes.",
            ),
            draft.steps.map { it.text },
        )
    }

    @Test
    fun `a title given by the user is not taken from the text`() {
        val draft = TextRecipeParser.parse("2 eggs\nBeat them.", providedTitle = "Mum's eggs")
        assertEquals("Mum's eggs", draft.title)
        assertEquals(listOf("2 eggs"), draft.ingredientLines)
    }

    @Test
    fun `a sentence mentioning preparation is a step, not a heading`() {
        val draft = TextRecipeParser.parse(
            "Stew\n1 kg beef\nThe preparation of the beef matters more than the cooking of it."
        )
        assertEquals(1, draft.steps.size)
        assertEquals(1, draft.ingredientLines.size)
    }

    @Test
    fun `no line is ever dropped`() {
        val text = """
            Something
            1 thing
            another thing
            Do the thing carefully and then wait.
        """.trimIndent()
        val draft = TextRecipeParser.parse(text)
        val kept = draft.ingredientLines.size + draft.steps.size + 1
        assertEquals(text.lines().count { it.isNotBlank() }, kept)
    }

    @Test
    fun `says what is missing`() {
        val draft = TextRecipeParser.parse("Just a title and nothing else")
        assertTrue(ExtractionWarning.NO_INGREDIENTS in draft.warnings)
        assertTrue(ExtractionWarning.NO_STEPS in draft.warnings)
    }
}

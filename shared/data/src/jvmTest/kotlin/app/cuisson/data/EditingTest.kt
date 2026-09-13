package app.cuisson.data

import app.cuisson.data.db.CuissonDatabase
import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Source
import app.cuisson.domain.SourceKind
import app.cuisson.domain.SourceNote
import app.cuisson.domain.Step
import app.cuisson.domain.Timings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Editing goes through the repository rather than through the screen, so these run off
 * the device. What they are guarding is the rule that an edit never destroys the
 * publisher's wording, and the one bug that rule invites: a line deleted on screen
 * surviving in the database because the save merged rather than replaced.
 */
class EditingTest {

    private fun repository() = RecipeRepository(CuissonDatabase(DatabaseDriverFactory().create()))

    private fun recipe(
        ingredients: List<String> = listOf("2 onions", "300 g beef"),
        steps: List<String> = listOf("Brown the beef.", "Simmer for 20 minutes."),
    ) = Recipe(
        id = RecipeId("r"),
        title = "Beef stew",
        rawTitle = "Beef Stew Recipe (Easy, One Pot!)",
        source = Source(SourceKind.WEB, url = "https://example.com/stew"),
        servings = null,
        timings = Timings(),
        ingredients = ingredients.mapIndexed { i, text ->
            IngredientLine(id = "r-i$i", position = i, rawText = text)
        },
        steps = steps.mapIndexed { i, text -> Step(id = "r-s$i", position = i, sourceText = text) },
        notes = null,
        sourceNotes = listOf(SourceNote("1", "Use chuck.")),
        imagePath = null,
        chapterId = null,
        language = "en",
        extraction = Extraction(ExtractionTier.STRUCTURED, 1f, false),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(1),
    )

    @Test
    fun `an amended line keeps what the publisher wrote`() {
        val repo = repository()
        val original = recipe()
        repo.save(original)

        repo.replace(
            original.copy(
                ingredients = original.ingredients.mapIndexed { i, line ->
                    if (i == 0) line.copy(amendment = "3 onions") else line
                }
            )
        )

        val stored = repo.ingredientsFor(RecipeId("r"))
        assertEquals("3 onions", stored[0].text, "the reader should see their own wording")
        assertEquals("2 onions", stored[0].rawText, "the publisher's line must survive it")
        assertTrue(stored[0].isAmended)
        assertTrue(!stored[1].isAmended)
    }

    @Test
    fun `a deleted line does not survive its own deletion`() {
        val repo = repository()
        val original = recipe()
        repo.save(original)

        repo.replace(original.copy(ingredients = original.ingredients.take(1)))

        assertEquals(
            listOf("2 onions"),
            repo.ingredientsFor(RecipeId("r")).map { it.text },
        )
    }

    /**
     * The failure this guards against is silent: the timer would go on offering twenty
     * minutes for a step that now says twenty-five, and nothing would look wrong until
     * somebody was standing at the hob.
     */
    @Test
    fun `rewriting a step's time moves its timer`() {
        val repo = repository()
        val original = recipe()
        repo.save(original)
        assertEquals(1200, repo.stepsFor(RecipeId("r"))[1].durationSeconds)

        repo.replace(
            original.copy(
                steps = original.steps.mapIndexed { i, step ->
                    if (i == 1) step.copy(amendment = "Simmer for 25 minutes.") else step
                }
            )
        )

        assertEquals(1500, repo.stepsFor(RecipeId("r"))[1].durationSeconds)
    }

    @Test
    fun `a line the user wrote themselves is its own original`() {
        val repo = repository()
        val original = recipe()
        repo.save(original)

        repo.replace(
            original.copy(
                ingredients = original.ingredients +
                    IngredientLine(id = "r-i2", position = 2, rawText = "A bay leaf"),
            )
        )

        val added = repo.ingredientsFor(RecipeId("r")).last()
        assertEquals("A bay leaf", added.text)
        assertNull(added.amendment, "a line with no publisher has nothing to amend")
    }

    @Test
    fun `deleting a recipe takes everything hanging off it`() {
        val repo = repository()
        repo.save(recipe())
        repo.logCook(RecipeId("r"), at = 1, entryId = "c1")

        repo.delete(RecipeId("r"))

        assertEquals(0, repo.count())
        assertEquals(emptyList(), repo.ingredientsFor(RecipeId("r")))
        assertEquals(emptyList(), repo.stepsFor(RecipeId("r")))
        assertEquals(emptyList(), repo.notesFor(RecipeId("r")))
        assertEquals(0, repo.cookCount(RecipeId("r")))
        assertEquals(emptyList(), repo.search("rendang"))
        assertEquals(emptyList(), repo.search("stew"))
    }

    /**
     * The backfill runs at every launch over every step. If it read the author's wording
     * it would undo an amended time once a day, for ever, and the change would look like
     * it never saved.
     */
    @Test
    fun `the startup backfill leaves an amended time alone`() {
        val repo = repository()
        val original = recipe()
        repo.save(original)
        repo.replace(
            original.copy(
                steps = original.steps.mapIndexed { i, step ->
                    if (i == 1) step.copy(amendment = "Simmer for 25 minutes.") else step
                }
            )
        )

        repo.backfillStepDurations(all = true)

        assertEquals(1500, repo.stepsFor(RecipeId("r"))[1].durationSeconds)
    }
}

package app.cuisson.data

import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Source
import app.cuisson.domain.SourceKind
import app.cuisson.domain.SourceNote
import app.cuisson.domain.Timings
import app.cuisson.data.db.CuissonDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SearchTest {

    private fun repository(): RecipeRepository {
        val driver = DatabaseDriverFactory().create()
        return RecipeRepository(CuissonDatabase(driver))
    }

    private fun recipe(
        id: String,
        title: String,
        ingredients: List<String> = emptyList(),
        notes: List<String> = emptyList(),
    ) = Recipe(
        id = RecipeId(id),
        title = title,
        rawTitle = null,
        source = Source(SourceKind.WEB, url = "https://example.com/$id"),
        servings = null,
        timings = Timings(),
        ingredients = ingredients.mapIndexed { i, text ->
            IngredientLine(id = "$id-i$i", position = i, rawText = text)
        },
        steps = emptyList(),
        notes = null,
        sourceNotes = notes.map { SourceNote(null, it) },
        imagePath = null,
        language = "en",
        extraction = Extraction(ExtractionTier.STRUCTURED, 1f, false),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(1),
    )

    @Test
    fun `finds a recipe by its name`() {
        val repo = repository()
        repo.save(recipe("a", "Beef Rendang"))
        repo.save(recipe("b", "Vegetarian Chili"))
        assertEquals(listOf(RecipeId("a")), repo.search("rendang"))
    }

    @Test
    fun `finds a recipe by what goes in it`() {
        val repo = repository()
        repo.save(recipe("a", "Something", ingredients = listOf("2 tbsp white miso paste")))
        repo.save(recipe("b", "Other", ingredients = listOf("500 g beef mince")))
        assertEquals(listOf(RecipeId("a")), repo.search("miso"))
    }

    @Test
    fun `accents do not have to be typed`() {
        // Half the library is French and the keyboard is not. "creme" must find "crème".
        val repo = repository()
        repo.save(recipe("a", "Quiche lorraine", ingredients = listOf("20 cl de crème fraîche")))
        assertEquals(listOf(RecipeId("a")), repo.search("creme"))
        assertEquals(listOf(RecipeId("a")), repo.search("crème"))
        assertEquals(listOf(RecipeId("a")), repo.search("fraiche"))
    }

    @Test
    fun `a part of a word is enough`() {
        val repo = repository()
        repo.save(recipe("a", "Chocolate Chip Cookies"))
        assertEquals(listOf(RecipeId("a")), repo.search("choc"))
    }

    @Test
    fun `several words all have to match`() {
        val repo = repository()
        repo.save(recipe("a", "Chocolate cake", ingredients = listOf("200 g dark chocolate")))
        repo.save(recipe("b", "Lemon cake", ingredients = listOf("2 lemons")))
        assertEquals(listOf(RecipeId("a")), repo.search("chocolate cake"))
    }

    @Test
    fun `the author's notes are searchable too`() {
        val repo = repository()
        repo.save(recipe("a", "Rendang", notes = listOf("Galangal is like ginger but sourer")))
        assertEquals(listOf(RecipeId("a")), repo.search("galangal"))
    }

    @Test
    fun `re-saving a recipe does not leave a stale copy in the index`() {
        val repo = repository()
        repo.save(recipe("a", "Old name"))
        repo.save(recipe("a", "New name"))
        assertTrue(repo.search("old").isEmpty())
        assertEquals(listOf(RecipeId("a")), repo.search("new"))
    }

    @Test
    fun `punctuation and empty queries do not throw`() {
        val repo = repository()
        repo.save(recipe("a", "Something"))
        assertTrue(repo.search("").isEmpty())
        assertTrue(repo.search("   ").isEmpty())
        assertTrue(repo.search("\"*(").isEmpty())
    }

    @Test
    fun `recipes saved before search existed can be indexed afterwards`() {
        val repo = repository()
        repo.save(recipe("a", "Beef Rendang"))
        repo.database().recipeQueries.deleteSearchRow("a")
        assertTrue(repo.search("rendang").isEmpty())
        repo.backfillSearchIfEmpty()
        assertEquals(listOf(RecipeId("a")), repo.search("rendang"))
    }
}

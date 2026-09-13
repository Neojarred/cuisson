package app.cuisson.data

import app.cuisson.data.db.CuissonDatabase
import app.cuisson.domain.Cookbook
import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Source
import app.cuisson.domain.SourceKind
import app.cuisson.domain.Timings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A chapter is a heading. What these guard is that it behaves like one: removing it takes
 * the heading and leaves the recipes, and the unnamed chapter every cookbook is created
 * with cannot be removed at all, because it is where those recipes land.
 */
class ChapterTest {

    private fun repository(): RecipeRepository {
        val repo = RecipeRepository(CuissonDatabase(DatabaseDriverFactory().create()))
        repo.ensureUnfiled()
        return repo
    }

    private fun recipe(id: String, chapterId: String?) = Recipe(
        id = RecipeId(id),
        title = id,
        rawTitle = null,
        source = Source(SourceKind.MANUAL),
        servings = null,
        timings = Timings(),
        ingredients = emptyList(),
        steps = emptyList(),
        notes = null,
        sourceNotes = emptyList(),
        imagePath = null,
        chapterId = chapterId,
        language = "en",
        extraction = Extraction(ExtractionTier.HAND_WRITTEN, 1f, false),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(1),
    )

    /**
     * The first launch. A fresh schema runs no migrations, so nothing had created the one
     * cookbook the app assumes exists, and a recipe saved on a new install went nowhere.
     */
    @Test
    fun `a recipe saved on a brand new database is not homeless`() {
        val repo = RecipeRepository(CuissonDatabase(DatabaseDriverFactory().create()))
        repo.ensureUnfiled()

        repo.save(recipe("r", chapterId = null))

        assertEquals(repo.defaultChapterOf(Cookbook.UNFILED), repo.all().single().chapterId)
        assertTrue(repo.cookbooks().any { it.isUnfiled })
    }

    @Test
    fun `creating Unfiled twice does not make two of it`() {
        val repo = repository()
        repo.ensureUnfiled()
        assertEquals(1, repo.cookbooks().count { it.isUnfiled })
        assertEquals(1, repo.chaptersOf(Cookbook.UNFILED).size)
    }

    @Test
    fun `a new cookbook has one chapter with no name`() {
        val repo = repository()
        repo.createCookbook("b", "Weeknights", now = 1)
        val chapters = repo.chaptersOf("b")
        assertEquals(1, chapters.size)
        assertTrue(chapters.single().isDefault, "the chapter a cookbook starts with is unnamed")
    }

    @Test
    fun `removing a chapter keeps what was in it`() {
        val repo = repository()
        repo.createCookbook("b", "Weeknights", now = 1)
        repo.addChapter("b-soups", "b", "Soups")
        repo.save(recipe("r", chapterId = "b-soups"))

        repo.deleteChapter("b-soups", "b")

        assertEquals(1, repo.count(), "the recipe should outlive the heading")
        assertEquals(
            repo.defaultChapterOf("b"),
            repo.all().single().chapterId,
            "it should fall back into the cookbook, not out of it",
        )
        assertTrue(repo.chaptersOf("b").none { it.name == "Soups" })
    }

    @Test
    fun `the unnamed chapter cannot be removed`() {
        val repo = repository()
        repo.createCookbook("b", "Weeknights", now = 1)
        val home = repo.defaultChapterOf("b")!!

        repo.deleteChapter(home, "b")

        assertEquals(listOf(home), repo.chaptersOf("b").map { it.id })
    }

    @Test
    fun `filing puts a recipe in the chapter chosen, not the first one`() {
        val repo = repository()
        repo.createCookbook("b", "Weeknights", now = 1)
        repo.addChapter("b-soups", "b", "Soups")
        repo.addChapter("b-pasta", "b", "Pasta")
        repo.save(recipe("r", chapterId = null))

        repo.fileRecipe(RecipeId("r"), "b-pasta", now = 2)

        assertEquals("b-pasta", repo.all().single().chapterId)
    }

    @Test
    fun `deleting a cookbook leaves its recipes in Unfiled`() {
        val repo = repository()
        repo.createCookbook("b", "Weeknights", now = 1)
        repo.addChapter("b-soups", "b", "Soups")
        repo.save(recipe("r", chapterId = "b-soups"))

        repo.deleteCookbook("b")

        assertEquals(1, repo.count())
        assertEquals(repo.defaultChapterOf(Cookbook.UNFILED), repo.all().single().chapterId)
    }
}

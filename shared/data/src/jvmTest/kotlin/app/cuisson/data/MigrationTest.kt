package app.cuisson.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuisson.data.db.CuissonDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the migrations against a real SQLite database.
 *
 * This exists because a schema change shipped without a migration made the app unable to
 * open on the only device that had ever held real recipes. A migration nobody has run is
 * not a migration, and the device is the wrong place to find that out.
 */
class MigrationTest {

    /** The schema as version 1 shipped it, written out so the migration has something to migrate. */
    private val version1 = """
        CREATE TABLE recipe (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            source_kind TEXT NOT NULL,
            source_url TEXT,
            source_name TEXT,
            servings_count REAL,
            servings_unit TEXT,
            prep_minutes INTEGER,
            cook_minutes INTEGER,
            total_minutes INTEGER,
            notes TEXT,
            image_path TEXT,
            language TEXT NOT NULL DEFAULT 'en',
            extraction_tier TEXT NOT NULL,
            extraction_conf REAL NOT NULL DEFAULT 0.0,
            needs_review INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE ingredient_line (
            id TEXT NOT NULL PRIMARY KEY,
            recipe_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            raw_text TEXT NOT NULL,
            group_label TEXT,
            quantity_min REAL,
            quantity_max REAL,
            unit_canonical TEXT,
            unit_system TEXT,
            unit_alt_canonical TEXT,
            unit_alt_system TEXT,
            item_text TEXT,
            preparation TEXT,
            optional INTEGER NOT NULL DEFAULT 0,
            canonical_item_id TEXT,
            parse_confidence REAL NOT NULL DEFAULT 0.0
        );
        CREATE TABLE step (
            id TEXT NOT NULL PRIMARY KEY,
            recipe_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            text TEXT NOT NULL,
            duration_seconds INTEGER,
            unresolved_refs TEXT
        );
    """.trimIndent()

    private fun driver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @Test
    fun `a version 1 database migrates without losing its recipes`() {
        val driver = driver()
        version1.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            driver.execute(null, "$it;", 0)
        }
        driver.execute(
            null,
            """
            INSERT INTO recipe (id, title, source_kind, extraction_tier, created_at, updated_at)
            VALUES ('keep-me', 'Quiche lorraine maison', 'WEB', 'STRUCTURED', 1, 1);
            """.trimIndent(),
            0,
        )

        CuissonDatabase.Schema.migrate(driver, 1L, CuissonDatabase.Schema.version)

        val database = CuissonDatabase(driver)
        val recipes = database.recipeQueries.selectAllRecipes().executeAsList()
        assertEquals(1, recipes.size, "the migration lost the recipe it was meant to preserve")
        assertEquals("Quiche lorraine maison", recipes.single().title)
        // The whole point of version 2.
        assertEquals(null, recipes.single().title_raw)
    }

    @Test
    fun `a fresh database is created at the current version and is queryable`() {
        val driver = driver()
        CuissonDatabase.Schema.create(driver)
        val database = CuissonDatabase(driver)
        assertEquals(0, database.recipeQueries.countRecipes().executeAsOne())
        assertTrue(CuissonDatabase.Schema.version >= 2L)
    }
}

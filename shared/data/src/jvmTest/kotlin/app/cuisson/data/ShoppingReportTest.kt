package app.cuisson.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuisson.data.db.CuissonDatabase
import java.io.File
import kotlin.test.Test

/**
 * Every recipe in a real library put on one shopping list, printed rather than asserted.
 *
 * The unit tests say what the rules are. This says what they make of twenty-five recipes
 * nobody wrote for them: which lines merged, which amounts came out odd, and what landed in
 * an aisle it does not belong in. Each parser in this project has had its worst bugs found
 * by reading output like this, after its own tests had all passed.
 *
 * Reads a copy of a device database from the gitignored corpus and skips without one.
 */
class ShoppingReportTest {

    @Test
    fun `report a shopping list made from a whole real library`() {
        val file = File("../../corpus/library.db")
        if (!file.exists()) {
            println("no library database, skipping")
            return
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        val recipes = RecipeRepository(CuissonDatabase(driver)).all()

        listOf("en", "fr").forEach { language ->
            val groups = consolidate(
                recipes = recipes.map { RecipeOnList(it, it.servings?.count) },
                own = emptyList(),
                language = language,
            )
            val items = groups.flatMap { it.items }
            println("=== ${recipes.size} recipes on one list, in $language: ${items.size} items")
            groups.forEach { group ->
                println()
                println("  ${group.label.uppercase()}")
                group.items.forEach { item ->
                    val from = item.contributions.size
                    val learned = if (item.learned) "  [learned]" else ""
                    println("    ${item.name.padEnd(28)} ${item.amount.padEnd(26)} x$from$learned")
                }
            }
            println()
        }

        println("--- every line behind an item with an odd-looking amount")
        consolidate(recipes.map { RecipeOnList(it, it.servings?.count) }, emptyList())
            .flatMap { it.items }
            .filter { it.amount.contains(", and") || it.amount.isEmpty() }
            .forEach { item ->
                println("  ${item.name}: \"${item.amount}\"")
                item.contributions.forEach { println("      ${it.line}") }
            }
        driver.close()
    }
}

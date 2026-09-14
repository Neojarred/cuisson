package app.cuisson.text

import java.io.File
import kotlin.test.Test

/**
 * What the catalogue makes of every ingredient in a real library, printed rather than
 * asserted.
 *
 * The number worth watching is how many distinct ingredients end up learned rather than
 * known. A learned ingredient still works, but it only merges with itself, so every one of
 * them is a place where a shopping list could say the same thing twice.
 *
 * Skips when the corpus is absent, which it is in the repository.
 */
class ResolverReportTest {

    @Test
    fun `report what the catalogue makes of a real library`() {
        val file = File("../../corpus/ingredient-lines.txt")
        if (!file.exists()) {
            println("no corpus, skipping")
            return
        }
        val items = file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { parseIngredient(it).item }
            .distinct()
        val resolved = items.map { it to resolveIngredient(it) }

        val known = resolved.filter { !it.second.learned }
        val learned = resolved.filter { it.second.learned }
        val notShoppable = resolved.filter { !it.second.shoppable }

        println("=== resolving ${items.size} distinct ingredient names from a real library")
        println("known to the catalogue: ${known.size}")
        println("learned:                ${learned.size}")
        println("never shoppable:        ${notShoppable.size}")
        println("distinct ingredients:   ${resolved.map { it.second.id }.distinct().size}")
        println()
        println("--- learned, with the aisle guessed for them")
        learned.forEach { (item, r) -> println("  $item   ->   ${r.name}  [${r.aisle}]") }
        println()
        println("--- a sample of known ones")
        known.filterIndexed { i, _ -> i % 9 == 0 }.forEach { (item, r) ->
            println("  $item   ->   ${r.id}  [${r.aisle}]")
        }
    }
}

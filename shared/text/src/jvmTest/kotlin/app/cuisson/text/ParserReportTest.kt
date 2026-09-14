package app.cuisson.text

import java.io.File
import kotlin.test.Test

/**
 * What the parser makes of a real library, printed rather than asserted.
 *
 * The unit tests say what the rules are. This says what they do to four hundred lines
 * written by people who had never heard of them, which is the only way to find out that a
 * rule is right about the cases it was built from and wrong about the rest.
 *
 * Skips when the corpus is absent, which it is in the repository: those lines are other
 * people's recipes. Recreate it from the ingredient_line table of a device database.
 */
class ParserReportTest {

    @Test
    fun `report what the parser makes of a real library`() {
        val file = File("../../corpus/ingredient-lines.txt")
        if (!file.exists()) {
            println("no corpus, skipping")
            return
        }
        val lines = file.readLines().filter { it.isNotBlank() }.distinct()
        val parsed = lines.map { it to parseIngredient(it) }

        val full = parsed.count { it.second.confidence >= 1f }
        val partial = parsed.count { it.second.confidence in 0.6f..0.99f }
        val nameOnly = parsed.count { it.second.confidence in 0.1f..0.59f }
        val nothing = parsed.count { it.second.confidence < 0.1f }

        println("=== parsing ${lines.size} distinct real ingredient lines")
        println("amount, unit and name: $full")
        println("amount and name:       $partial")
        println("name only:             $nameOnly")
        println("nothing:               $nothing")

        println()
        println("--- names, most common first, for spotting the ones that are not names")
        parsed.mapNotNull { it.second.item }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(20)
            .forEach { println("  ${it.value}x  ${it.key}") }

        println()
        println("--- lines the parser could not name at all")
        parsed.filter { it.second.item.isNullOrBlank() }.take(15).forEach { println("  ${it.first}") }

        println()
        println("--- a sample, to read")
        parsed.filterIndexed { index, _ -> index % 17 == 0 }.take(22).forEach { (line, p) ->
            println("  $line")
            println(
                "      qty=${p.quantityMin} unit=${p.unit} item=${p.item} " +
                    "prep=${p.preparation} optional=${p.optional}"
            )
        }
    }
}

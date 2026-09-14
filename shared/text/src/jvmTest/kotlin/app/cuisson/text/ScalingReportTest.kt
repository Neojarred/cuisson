package app.cuisson.text

import java.io.File
import kotlin.test.Test

/**
 * Runs the scaler over every ingredient line in a real library and prints what it did.
 *
 * Not an assertion, a measurement. The unit tests say what the rules are; this says what
 * the rules do to four hundred lines nobody wrote for us, which is the only way to find
 * out that a rule is right about the cases it was built from and wrong about the rest.
 *
 * Skips when the corpus is absent, which it is in the repository: those lines are other
 * people's recipes. Recreate it with the ingredient_line table from a device database.
 */
class ScalingReportTest {

    @Test
    fun `report what doubling does to a real library`() {
        val file = File("../../corpus/ingredient-lines.txt")
        if (!file.exists()) {
            println("no corpus, skipping")
            return
        }
        val lines = file.readLines().filter { it.isNotBlank() }

        var unchanged = 0
        var scaled = 0
        val leftovers = mutableListOf<String>()

        lines.forEach { line ->
            val doubled = scaleIngredient(line, 2.0)
            if (doubled == line) {
                unchanged++
            } else {
                scaled++
                // A number still in the line that was not touched. Most are innocent, a
                // note reference or the size of a tin; this is how the guilty ones get
                // found.
                val rest = doubled.substring(
                    doubled.indexOfFirst { !it.isDigit() && it != ' ' }.coerceAtLeast(0)
                )
                if (Regex("""\d""").containsMatchIn(rest.substringAfter(' '))) {
                    leftovers += "$line   ->   $doubled"
                }
            }
        }

        println("=== doubling ${lines.size} real ingredient lines")
        println("scaled:    $scaled")
        println("untouched: $unchanged   (a line with no quantity is correct to leave)")
        println()
        println("--- lines that still hold a number after scaling, for eyeballing")
        leftovers.take(40).forEach { println("  $it") }
        if (leftovers.size > 40) println("  ... and ${leftovers.size - 40} more")
    }
}

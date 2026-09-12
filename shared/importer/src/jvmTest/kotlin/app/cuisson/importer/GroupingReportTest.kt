package app.cuisson.importer

import java.io.File
import kotlin.test.Test

/** Prints which corpus pages yield an ingredient grouping, and what the groups are. */
class GroupingReportTest {

    @Test
    fun `report ingredient grouping across the corpus`() {
        val corpus = findCorpus() ?: run { println("No corpus; skipping."); return }
        val pages = corpus.listFiles { f -> f.extension == "html" }?.sortedBy { it.name }.orEmpty()
        println()
        var grouped = 0
        pages.forEach { file ->
            val draft = StructuredExtractor.extract(file.readText()) ?: return@forEach
            val groups = draft.ingredientLines.mapNotNull { it.group }.distinct()
            if (groups.isEmpty()) {
                println("  -   ${file.nameWithoutExtension}")
                if (file.nameWithoutExtension in setOf("ricardo-lasagna", "loveandlemons")) {
                    println("        ${IngredientGrouper.explain(file.readText(), draft.ingredientTexts)}")
                }
            } else {
                grouped++
                println("  OK  ${file.nameWithoutExtension}: ${groups.joinToString(" | ")}")
                draft.ingredientLines.take(4).forEach {
                    println("        [${it.group ?: "-"}] ${it.text.take(48)}")
                }
            }
        }
        println()
        println("  $grouped pages grouped")
        println()
    }

    private fun findCorpus(): File? {
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "corpus")
            if (candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}

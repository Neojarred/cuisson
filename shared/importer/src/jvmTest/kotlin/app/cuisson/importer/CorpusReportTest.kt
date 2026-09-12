package app.cuisson.importer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs Structured Extraction over real pages saved in `corpus/`, and prints what came out.
 *
 * The corpus is other people's content, so it is not in the repository and this test
 * quietly does nothing when it is absent. Its job is to answer the question phase 1 of the
 * roadmap exists to answer: on ordinary recipe sites, how often does the main import path
 * actually work, and how good is the result.
 */
class CorpusReportTest {

    @Test
    fun `report extraction quality across the corpus`() {
        val corpus = findCorpus() ?: run {
            println("No corpus directory; skipping.")
            return
        }
        val pages = corpus.listFiles { f -> f.extension == "html" }?.sortedBy { it.name }.orEmpty()
        if (pages.isEmpty()) {
            println("Corpus is empty; skipping.")
            return
        }

        var usable = 0
        println()
        println("=".repeat(78))
        pages.forEach { file ->
            val draft = StructuredExtractor.extract(file.readText(), "file://${file.name}")
            if (draft == null) {
                println("${file.nameWithoutExtension.padEnd(16)} NO RECIPE FOUND")
                return@forEach
            }
            if (draft.looksUsable) usable++
            println(
                buildString {
                    append(file.nameWithoutExtension.padEnd(16))
                    append(if (draft.looksUsable) "OK   " else "THIN ")
                    append("ing=${draft.ingredientTexts.size.toString().padEnd(3)}")
                    append("steps=${draft.steps.size.toString().padEnd(3)}")
                    append("yield=${(draft.servingsText ?: "-").take(14).padEnd(15)}")
                    append("total=${(draft.totalMinutes?.toString() ?: "-").padEnd(5)}")
                    if (draft.warnings.isNotEmpty()) append(draft.warnings.joinToString(","))
                }
            )
            println("    title:  ${draft.title.take(66)}")
            draft.ingredientTexts.take(2).forEach { println("    ing:    $it") }
            draft.steps.firstOrNull()?.let { println("    step 1: ${it.text.take(66)}") }
        }
        println("=".repeat(78))
        println("usable: $usable of ${pages.size}")
        println()

        assertTrue(usable > 0, "Structured extraction produced nothing usable from any page")
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

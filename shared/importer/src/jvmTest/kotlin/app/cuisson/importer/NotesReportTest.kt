package app.cuisson.importer

import java.io.File
import kotlin.test.Test

/** Prints which corpus pages yield Source Notes, and the first of each. */
class NotesReportTest {

    @Test
    fun `report source notes across the corpus`() {
        val corpus = findCorpus() ?: run { println("No corpus; skipping."); return }
        val pages = corpus.listFiles { f -> f.extension == "html" }?.sortedBy { it.name }.orEmpty()
        println()
        var found = 0
        pages.forEach { file ->
            val notes = NotesExtractor.extract(file.readText())
            if (notes.isEmpty()) {
                println("  -   ${file.nameWithoutExtension}")
            } else {
                found++
                val labels = notes.mapNotNull { it.label }
                println("  OK  ${file.nameWithoutExtension}: ${notes.size} notes, labels=${labels.take(6)}")
                notes.take(2).forEach { println("        ${it.label ?: "-"} | ${it.text.take(70)}") }
            }
        }
        println()
        println("  $found pages with notes")
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

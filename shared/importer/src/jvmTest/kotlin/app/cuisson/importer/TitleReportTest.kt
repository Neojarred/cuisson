package app.cuisson.importer

import java.io.File
import kotlin.test.Test

/**
 * Prints what the title trimmer does to every title in the corpus.
 *
 * Not an assertion, a look. Rules that trim text are easy to write and easy to make too
 * greedy, and seeing thirty real titles side by side with their trimmed versions is the
 * fastest way to notice one that lost something it needed.
 */
class TitleReportTest {

    @Test
    fun `report titles before and after trimming`() {
        val corpus = findCorpus() ?: run { println("No corpus directory; skipping."); return }
        val pages = corpus.listFiles { f -> f.extension == "html" }?.sortedBy { it.name }.orEmpty()
        if (pages.isEmpty()) return

        println()
        var changed = 0
        pages.forEach { file ->
            val draft = StructuredExtractor.extract(file.readText(), "https://${file.nameWithoutExtension}.com")
                ?: return@forEach
            val before = draft.rawTitle.orEmpty()
            val after = draft.title
            if (before == after) {
                println("  =  $before")
            } else {
                changed++
                println("  ~  $before")
                println("     -> $after")
            }
        }
        println()
        println("  $changed of ${pages.size} titles trimmed")
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

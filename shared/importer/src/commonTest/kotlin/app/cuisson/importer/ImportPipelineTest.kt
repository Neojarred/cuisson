package app.cuisson.importer

import app.cuisson.domain.ExtractionTier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportPipelineTest {

    private val recipePage = """
        <!doctype html><html><head>
        <script type="application/ld+json">
        {"@type":"Recipe","name":"Onion soup","recipeYield":"4",
         "recipeIngredient":["1 kg onions","50 g butter"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Caramelise the onions slowly."}]}
        </script></head><body><p>Story about onions.</p></body></html>
    """.trimIndent()

    private fun serving(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }

    @Test
    fun `a normal recipe page is ready with no work needed`() = runTest {
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(serving(recipePage))))
            .importUrl("https://example.com/onion-soup")
        val ready = assertIs<ImportOutcome.Ready>(outcome)
        assertEquals("Onion soup", ready.draft.title)
        assertEquals(2, ready.draft.ingredientTexts.size)
    }

    @Test
    fun `a site that refuses us is reported as blocked, not as a failure`() = runTest {
        // Two large publishers answered 402 to a perfectly ordinary request while this
        // was being written. The user can do nothing about it, so saying "import failed"
        // would send them looking for a problem at their end.
        val engine = MockEngine { respondError(HttpStatusCode.PaymentRequired) }
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(engine)))
            .importUrl("https://example.com/blocked")
        val blocked = assertIs<ImportOutcome.Blocked>(outcome)
        assertEquals(402, blocked.status)
    }

    @Test
    fun `403 and 429 are blocks too`() = runTest {
        listOf(HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests).forEach { status ->
            val engine = MockEngine { respondError(status) }
            val outcome = ImportPipeline(RecipeFetcher(HttpClient(engine)))
                .importUrl("https://example.com/x")
            assertIs<ImportOutcome.Blocked>(outcome, "expected $status to be a block")
        }
    }

    @Test
    fun `a missing page is an ordinary failure`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(engine)))
            .importUrl("https://example.com/gone")
        assertIs<ImportOutcome.Failed>(outcome)
    }

    @Test
    fun `a page with no recipe keeps its text rather than losing the attempt`() = runTest {
        val page = """
            <!doctype html><html><head><title>Someone's blog</title></head>
            <body><h1>My weekend</h1><p>I made a stew.</p><p>It was good.</p></body></html>
        """.trimIndent()
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(serving(page))))
            .importUrl("https://example.com/blog")
        val needsWork = assertIs<ImportOutcome.NeedsWork>(outcome)
        assertEquals(ExtractionTier.PAGE_TEXT, needsWork.draft.tier)
        assertEquals("My weekend", needsWork.draft.title)
        assertTrue(needsWork.draft.steps.any { it.text.contains("stew") })
    }

    @Test
    fun `a thin recipe is kept over the page text, because its markup was real`() = runTest {
        val page = """
            <!doctype html><html><head>
            <script type="application/ld+json">
            {"@type":"Recipe","name":"Half a recipe","recipeIngredient":["2 eggs"]}
            </script></head><body><p>Filler.</p></body></html>
        """.trimIndent()
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(serving(page))))
            .importUrl("https://example.com/thin")
        val needsWork = assertIs<ImportOutcome.NeedsWork>(outcome)
        assertEquals(ExtractionTier.STRUCTURED, needsWork.draft.tier)
        assertEquals(listOf("2 eggs"), needsWork.draft.ingredientTexts)
    }

    @Test
    fun `text that is not a url is said so plainly`() = runTest {
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(serving(""))))
            .importUrl("just some words")
        assertIs<ImportOutcome.NotAUrl>(outcome)
    }

    @Test
    fun `a shared link surrounded by other text still works`() = runTest {
        // Share sheets deliver "Look at this https://example.com/x via SomeApp".
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(serving(recipePage))))
            .importUrl("Check this out https://example.com/onion-soup shared via Chrome")
        assertIs<ImportOutcome.Ready>(outcome)
    }

    @Test
    fun `an address without a scheme is accepted`() = runTest {
        val outcome = ImportPipeline(RecipeFetcher(HttpClient(serving(recipePage))))
            .importUrl("example.com/onion-soup")
        assertIs<ImportOutcome.Ready>(outcome)
    }
}

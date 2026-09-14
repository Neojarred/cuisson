package app.cuisson.data

import app.cuisson.data.db.CuissonDatabase
import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Servings
import app.cuisson.domain.Source
import app.cuisson.domain.SourceKind
import app.cuisson.domain.SourceNote
import app.cuisson.domain.Step
import app.cuisson.domain.Timings
import app.cuisson.text.Aisle
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Phase 5's exit condition, as tests: wipe the app, restore from an Export File, lose nothing.
 * And the rule that makes restoring safe to do on a phone that already has recipes on it:
 * nothing is ever overwritten without the other version being kept.
 */
class ExportFileTest {

    private class Images {
        val files = mutableMapOf<String, ByteArray>()
        fun read(path: String): ByteArray? = files[path]
        fun write(id: String, bytes: ByteArray): String = "images/$id.img".also { files[it] = bytes }
    }

    private class Library {
        val database = CuissonDatabase(DatabaseDriverFactory().create())
        val recipes = RecipeRepository(database).also { it.ensureUnfiled() }
        val shopping = ShoppingRepository(database, recipes)
        val exports = LibraryExport(database, recipes)
        val images = Images()
    }

    private fun recipe(
        id: String,
        title: String,
        updatedAt: Long = 1,
        imagePath: String? = null,
    ) = Recipe(
        id = RecipeId(id),
        title = title,
        rawTitle = "$title, The Best Recipe",
        source = Source(SourceKind.WEB, url = "https://example.com/$id", name = "Example"),
        servings = Servings(4.0),
        timings = Timings(totalMinutes = 90),
        ingredients = listOf(
            IngredientLine(id = "$id-i0", position = 0, rawText = "2 onions", amendment = "3 onions", groupLabel = "Paste"),
            IngredientLine(id = "$id-i1", position = 1, rawText = "300 g beef"),
        ),
        steps = listOf(
            Step(id = "$id-s0", position = 0, sourceText = "Simmer for 20 minutes.", amendment = "Simmer for 25 minutes."),
        ),
        notes = "Better the next day.",
        sourceNotes = listOf(SourceNote("1a", "Use dried chillies.")),
        imagePath = imagePath,
        chapterId = null,
        language = "en",
        extraction = Extraction(ExtractionTier.STRUCTURED, 1f, false),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )

    @Test
    fun `sha256 agrees with the platform at every length the padding cares about`() {
        listOf(0, 1, 3, 55, 56, 57, 63, 64, 65, 119, 120, 1000).forEach { length ->
            val bytes = ByteArray(length) { (it * 31 + 7).toByte() }
            val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, Sha256.hex(bytes), "length $length")
        }
    }

    @Test
    fun `crc32 agrees with the platform`() {
        val bytes = "The data belongs to the person who saved it.".encodeToByteArray()
        val platform = CRC32().apply { update(bytes) }.value.toInt()
        assertEquals(platform, Crc32.of(bytes))
    }

    /** An Export File that only Cuisson can open would not be much of a promise. */
    @Test
    fun `an Export File is an ordinary zip that any unzip tool opens`() {
        val entries = listOf("manifest.json" to "{}".encodeToByteArray(), "media/abc" to ByteArray(300) { it.toByte() })
        val zip = StoredZip.write(entries)

        val seen = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { seen[it.name] = zip.readBytes() }
        }
        assertEquals(entries.map { it.first }.toSet(), seen.keys)
        entries.forEach { (name, bytes) -> assertContentEquals(bytes, seen[name]) }
        assertEquals(seen.keys, StoredZip.read(zip).keys)
    }

    @Test
    fun `wiping the phone and restoring loses nothing`() {
        val before = Library()
        before.images.files["images/rendang.img"] = byteArrayOf(1, 2, 3, 4)
        before.recipes.save(recipe("rendang", "Beef Rendang", imagePath = "images/rendang.img"))
        before.recipes.createCookbook("b", "Weeknights", now = 1)
        before.recipes.addChapter("b-soups", "b", "Soups")
        before.recipes.fileRecipe(RecipeId("rendang"), "b-soups", now = 1)
        before.recipes.logCook(RecipeId("rendang"), at = 5, entryId = "c1")
        before.shopping.createList("week", "This week", now = 1)
        before.shopping.addRecipe("week", RecipeId("rendang"), 8.0, now = 2)
        before.shopping.addOwnLine("week", "o1", "1 lemon", now = 3)
        val onions = before.shopping.view("week", "en")!!.groups.flatMap { it.items }.single { it.name == "Onion" }
        before.shopping.setAmount("week", onions, "a big bag")
        before.database.shoppingQueries.upsertAlias("yuzu kosho", "chilli-flakes", 1)
        before.database.shoppingQueries.upsertAisle("lemon", Aisle.OTHER.name)
        // The recipe was filed after saving, so compare against what is actually stored.
        val original = before.recipes.find(RecipeId("rendang"))!!

        val zip = before.exports.export("0.1.0", now = 10, readImage = before.images::read)

        val after = Library()
        val report = after.exports.restore(zip, after.images::read, after.images::write)
        assertEquals(1, report.added)

        val restored = after.recipes.find(RecipeId("rendang"))
        assertNotNull(restored)
        assertEquals(original.title, restored.title)
        assertEquals(original.rawTitle, restored.rawTitle)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.servings, restored.servings)
        assertEquals(original.sourceNotes, restored.sourceNotes)
        assertEquals(listOf("3 onions", "300 g beef"), restored.ingredients.map { it.text })
        assertEquals("2 onions", restored.ingredients.first().rawText, "the publisher's line survived")
        assertEquals("Paste", restored.ingredients.first().groupLabel)
        assertEquals("Simmer for 25 minutes.", restored.steps.single().text)
        assertEquals(1500, restored.steps.single().durationSeconds, "timers are rebuilt, not lost")
        assertEquals("b-soups", restored.chapterId)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), after.images.read(restored.imagePath!!))
        assertEquals(1, after.recipes.cookCount(RecipeId("rendang")))
        assertTrue(after.recipes.chaptersOf("b").any { it.name == "Soups" })

        val list = after.shopping.view("week", "en")!!
        assertEquals(8.0, list.recipes.single().servings)
        assertEquals(listOf("1 lemon"), list.own.map { it.text })
        val items = list.groups.flatMap { it.items }
        assertEquals("a big bag", items.single { it.name == "Onion" }.amount)
        assertEquals(Aisle.OTHER, items.single { it.name == "Lemon" }.aisle)
        assertEquals(1, after.database.shoppingQueries.selectAliases().executeAsList().size)
    }

    @Test
    fun `a newer recipe in the Export File wins and the phone's version is kept as a copy`() {
        val source = Library()
        source.recipes.save(recipe("r", "Newer", updatedAt = 5))
        val zip = source.exports.export("0.1.0", now = 10, readImage = source.images::read)

        val phone = Library()
        phone.recipes.save(recipe("r", "Older", updatedAt = 1))
        val report = phone.exports.restore(zip, phone.images::read, phone.images::write)

        assertEquals("Newer", phone.recipes.find(RecipeId("r"))!!.title)
        assertEquals(setOf("Newer", "Older (copy)"), phone.recipes.all().map { it.title }.toSet())
        assertEquals(1, report.updated)
        assertEquals(1, report.keptAsCopies)
    }

    @Test
    fun `an older recipe in the Export File is kept as a copy beside the phone's newer one`() {
        val source = Library()
        source.recipes.save(recipe("r", "Older", updatedAt = 1))
        val zip = source.exports.export("0.1.0", now = 10, readImage = source.images::read)

        val phone = Library()
        phone.recipes.save(recipe("r", "Newer", updatedAt = 5))
        phone.exports.restore(zip, phone.images::read, phone.images::write)

        assertEquals("Newer", phone.recipes.find(RecipeId("r"))!!.title)
        assertEquals(setOf("Newer", "Older (copy)"), phone.recipes.all().map { it.title }.toSet())
    }

    @Test
    fun `restoring the same Export File twice changes nothing the second time`() {
        val source = Library()
        source.recipes.save(recipe("r", "Rendang"))
        val zip = source.exports.export("0.1.0", now = 10, readImage = source.images::read)

        val phone = Library()
        phone.exports.restore(zip, phone.images::read, phone.images::write)
        val second = phone.exports.restore(zip, phone.images::read, phone.images::write)

        assertEquals(0, second.added)
        assertEquals(1, second.unchanged)
        assertEquals(1, phone.recipes.count())
    }

    @Test
    fun `an Export File from a newer version of the format is refused rather than half read`() {
        val manifest = """{"format":"cuisson","formatVersion":99,"appVersion":"9","exportedAt":1,
            "recipes":0,"images":0,"cookbooks":0,"shoppingLists":0}"""
        val zip = StoredZip.write(listOf("manifest.json" to manifest.encodeToByteArray()))
        val phone = Library()
        val error = assertFailsWith<ExportException> {
            phone.exports.restore(zip, phone.images::read, phone.images::write)
        }
        assertTrue(error.message!!.contains("newer version"))
    }

    @Test
    fun `something that is not an Export File is refused`() {
        val phone = Library()
        assertFailsWith<ExportException> {
            phone.exports.restore("not a zip at all".encodeToByteArray(), phone.images::read, phone.images::write)
        }
        assertEquals(0, phone.recipes.count())
    }

    @Test
    fun `a damaged Export File is refused`() {
        val source = Library()
        source.recipes.save(recipe("r", "Rendang"))
        val zip = source.exports.export("0.1.0", now = 10, readImage = source.images::read)
        // Flip a byte inside the stored JSON.
        val damaged = zip.copyOf().also { it[200] = (it[200] + 1).toByte() }
        val phone = Library()
        assertFailsWith<ExportException> {
            phone.exports.restore(damaged, phone.images::read, phone.images::write)
        }
        assertEquals(0, phone.recipes.count())
    }
}

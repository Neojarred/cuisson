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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What a restore did, so the app can say so in a sentence. */
data class RestoreReport(
    val added: Int,
    val updated: Int,
    val keptAsCopies: Int,
    val unchanged: Int,
)

/**
 * The whole library in one file, and back.
 *
 * The test that matters is the phase 5 exit condition: wipe the app, restore, and lose
 * nothing. Recipes with their amendments and pictures, cookbooks and chapters, the cooking
 * record, shopping lists and every correction the user made to how an ingredient is read.
 *
 * Restoring never destroys anything. Where a recipe exists both on the phone and in the
 * Export File with different edits, the more recently changed one keeps its place and the other
 * is kept beside it as a copy, as settled in the architecture. Everything else is added
 * only where it is missing.
 */
class LibraryExport(
    private val database: CuissonDatabase,
    private val recipes: RecipeRepository,
) {
    private val recipeQueries = database.recipeQueries
    private val shoppingQueries = database.shoppingQueries
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // A newer app adding a field must not make its Export Files unreadable to this one.
        ignoreUnknownKeys = true
    }

    /** Builds the zip. [readImage] turns a stored image path into its bytes. */
    fun export(appVersion: String, now: Long, readImage: (String) -> ByteArray?): ByteArray {
        val media = linkedMapOf<String, ByteArray>()
        val chapters = recipeQueries.selectAllChapters().executeAsList()
        val listRecipes = shoppingQueries.selectAllListRecipes().executeAsList()
        val ownLines = shoppingQueries.selectAllOwnItems().executeAsList()
        val typed = shoppingQueries.selectAllOverrides().executeAsList()

        val library = ExportLibrary(
            recipes = recipes.all().map { recipe ->
                val image = recipe.imagePath?.let(readImage)?.let { bytes ->
                    "media/${Sha256.hex(bytes)}".also { media[it] = bytes }
                }
                recipe.toExport(image)
            },
            cookbooks = recipeQueries.selectCookbooks().executeAsList().map { book ->
                ExportCookbook(
                    id = book.id,
                    name = book.name,
                    position = book.position,
                    createdAt = book.created_at,
                    chapters = chapters.filter { it.cookbook_id == book.id }
                        .map { ExportChapter(it.id, it.name, it.position) },
                )
            },
            cookEntries = recipeQueries.selectAllCookEntries().executeAsList().map {
                ExportCookEntry(it.id, it.recipe_id, it.cooked_at, it.rating, it.note)
            },
            shoppingLists = shoppingQueries.selectLists().executeAsList().map { list ->
                ExportShoppingList(
                    id = list.id,
                    name = list.name,
                    createdAt = list.created_at,
                    lastUsedAt = list.last_used_at,
                    archivedAt = list.archived_at,
                    recipes = listRecipes.filter { it.list_id == list.id }
                        .map { ExportListRecipe(it.recipe_id, it.servings, it.added_at) },
                    ownLines = ownLines.filter { it.list_id == list.id }
                        .map { ExportOwnLine(it.id, it.text, it.added_at) },
                    typedAmounts = typed.filter { it.list_id == list.id }
                        .map { ExportTypedAmount(it.item_key, it.amount, it.basis) },
                )
            },
            corrections = shoppingQueries.selectAliases().executeAsList().map {
                ExportCorrection(it.alias_key, it.canonical_id, it.created_at)
            },
            aisleCorrections = shoppingQueries.selectAisles().executeAsList().map {
                ExportAisle(it.canonical_id, it.aisle)
            },
        )
        val manifest = ExportManifest(
            appVersion = appVersion,
            exportedAt = now,
            recipes = library.recipes.size,
            images = media.size,
            cookbooks = library.cookbooks.size,
            shoppingLists = library.shoppingLists.size,
        )
        return StoredZip.write(
            listOf(
                "manifest.json" to json.encodeToString(ExportManifest.serializer(), manifest)
                    .encodeToByteArray(),
                "recipes.json" to json.encodeToString(ExportLibrary.serializer(), library)
                    .encodeToByteArray(),
            ) + media.toList()
        )
    }

    /**
     * Puts an Export File's contents into this library.
     *
     * [readImage] reads a stored image, so a recipe kept as a copy keeps its picture.
     * [writeImage] stores a picture for a recipe id and returns its path. Throws
     * [ExportException] with a sentence fit to show the user if the file cannot be read, and
     * in that case writes nothing at all.
     */
    fun restore(
        zip: ByteArray,
        readImage: (String) -> ByteArray?,
        writeImage: (recipeId: String, bytes: ByteArray) -> String,
    ): RestoreReport {
        val entries = StoredZip.read(zip)
        val manifest = decode(
            ExportManifest.serializer(),
            entries["manifest.json"] ?: throw ExportException(StoredZip.NOT_AN_EXPORT),
        )
        if (manifest.format != EXPORT_FORMAT) throw ExportException(StoredZip.NOT_AN_EXPORT)
        if (manifest.formatVersion > EXPORT_FORMAT_VERSION) {
            throw ExportException(
                "This export file was made by a newer version of Cuisson. Update the app to restore it."
            )
        }
        val library = decode(
            ExportLibrary.serializer(),
            entries["recipes.json"] ?: throw ExportException(StoredZip.DAMAGED),
        )

        var added = 0
        var updated = 0
        var copies = 0
        var unchanged = 0

        database.transaction {
            library.cookbooks.forEach { book ->
                recipeQueries.insertCookbookIfMissing(book.id, book.name, book.position, book.createdAt)
                book.chapters.forEach {
                    recipeQueries.insertChapterIfMissing(it.id, book.id, it.name, it.position)
                }
            }

            library.recipes.forEach { entry ->
                val chapter = entry.chapterId
                    ?.takeIf { recipeQueries.chapterExists(it).executeAsOne() > 0 }
                fun imageFor(id: String): String? =
                    entry.image?.let(entries::get)?.let { writeImage(id, it) }

                val local = recipes.find(RecipeId(entry.id))
                val localTime = local?.updatedAt?.toEpochMilliseconds()
                when {
                    local == null -> {
                        recipes.save(entry.toRecipe(imagePath = imageFor(entry.id), chapterId = chapter))
                        added++
                    }
                    localTime == entry.updatedAt -> unchanged++
                    entry.updatedAt > localTime!! -> {
                        // The Export File's version is newer. The phone's version stays, as a copy.
                        val copyId = newId()
                        val copyImage = local.imagePath?.let(readImage)?.let { writeImage(copyId, it) }
                        recipes.save(local.asCopy(copyId, copyImage))
                        recipes.replace(
                            entry.toRecipe(
                                imagePath = imageFor(entry.id) ?: local.imagePath,
                                chapterId = chapter ?: local.chapterId,
                            )
                        )
                        updated++
                        copies++
                    }
                    else -> {
                        // The phone's version is newer. The Export File's version stays, as a copy.
                        val copyId = newId()
                        recipes.save(
                            entry.toRecipe(
                                id = copyId,
                                title = "${entry.title} (copy)",
                                imagePath = imageFor(copyId),
                                chapterId = chapter,
                            )
                        )
                        copies++
                    }
                }
            }

            library.cookEntries.forEach {
                recipeQueries.insertCookEntryIfMissing(it.id, it.recipeId, it.cookedAt, it.rating, it.note)
            }

            library.shoppingLists.forEach { list ->
                shoppingQueries.insertListIfMissing(
                    list.id, list.name, list.createdAt, list.lastUsedAt, list.archivedAt,
                )
                list.recipes.forEach {
                    shoppingQueries.insertListRecipeIfMissing(list.id, it.recipeId, it.servings, it.addedAt)
                }
                list.ownLines.forEach {
                    shoppingQueries.insertOwnItemIfMissing(it.id, list.id, it.text, it.addedAt)
                }
                list.typedAmounts.forEach {
                    shoppingQueries.insertOverrideIfMissing(list.id, it.itemKey, it.amount, it.basis)
                }
            }

            library.corrections.forEach {
                shoppingQueries.insertAliasIfMissing(it.writtenAs, it.ingredient, it.createdAt)
            }
            library.aisleCorrections.forEach {
                shoppingQueries.insertAisleIfMissing(it.ingredient, it.aisle)
            }
        }

        return RestoreReport(added, updated, copies, unchanged)
    }

    private fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T = try {
        json.decodeFromString(serializer, bytes.decodeToString())
    } catch (e: Exception) {
        throw ExportException(StoredZip.DAMAGED)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()
}

private fun Recipe.toExport(image: String?) = ExportRecipe(
    id = id.value,
    title = title,
    titleAsPublished = rawTitle,
    sourceKind = source.kind.name,
    sourceUrl = source.url,
    sourceName = source.name,
    servings = servings?.count,
    servingsUnit = servings?.unit,
    prepMinutes = timings.prepMinutes,
    cookMinutes = timings.cookMinutes,
    totalMinutes = timings.totalMinutes,
    note = notes,
    image = image,
    chapterId = chapterId,
    language = language,
    extractionTier = extraction.tier.name,
    extractionConfidence = extraction.confidence,
    needsReview = extraction.needsReview,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    ingredients = ingredients.map { ExportLine(it.id, it.rawText, it.amendment, it.groupLabel) },
    steps = steps.map { ExportStep(it.id, it.sourceText, it.amendment, it.references) },
    sourceNotes = sourceNotes.map { ExportSourceNote(it.label, it.text) },
)

/**
 * A recipe from a entry. Given a different [id], its lines get new ids too, since line ids
 * are unique across the whole library and a copy must not collide with the original.
 */
private fun ExportRecipe.toRecipe(
    id: String = this.id,
    title: String = this.title,
    imagePath: String?,
    chapterId: String?,
): Recipe {
    val sameId = id == this.id
    return Recipe(
        id = RecipeId(id),
        title = title,
        rawTitle = titleAsPublished,
        source = Source(
            kind = runCatching { SourceKind.valueOf(sourceKind) }.getOrDefault(SourceKind.MANUAL),
            url = sourceUrl,
            name = sourceName,
        ),
        servings = servings?.let { Servings(it, servingsUnit) },
        timings = Timings(prepMinutes, cookMinutes, totalMinutes),
        ingredients = ingredients.mapIndexed { index, line ->
            IngredientLine(
                id = if (sameId) line.id else "$id-i$index",
                position = index,
                rawText = line.rawText,
                amendment = line.amendment,
                groupLabel = line.group,
            )
        },
        steps = steps.mapIndexed { index, step ->
            Step(
                id = if (sameId) step.id else "$id-s$index",
                position = index,
                sourceText = step.text,
                amendment = step.amendment,
                references = step.references,
            )
        },
        notes = note,
        sourceNotes = sourceNotes.map { SourceNote(it.label, it.text) },
        imagePath = imagePath,
        chapterId = chapterId,
        language = language,
        extraction = Extraction(
            tier = runCatching { ExtractionTier.valueOf(extractionTier) }
                .getOrDefault(ExtractionTier.HAND_WRITTEN),
            confidence = extractionConfidence,
            needsReview = needsReview,
        ),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )
}

private fun Recipe.asCopy(newId: String, imagePath: String?) = copy(
    id = RecipeId(newId),
    title = "$title (copy)",
    imagePath = imagePath,
    ingredients = ingredients.mapIndexed { index, line ->
        line.copy(id = "$newId-i$index", parseConfidence = 0f)
    },
    steps = steps.mapIndexed { index, step -> step.copy(id = "$newId-s$index") },
)

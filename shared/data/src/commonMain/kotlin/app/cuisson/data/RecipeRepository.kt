package app.cuisson.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuisson.data.db.CuissonDatabase
import app.cuisson.data.db.Recipe as Recipe_
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cuisson.domain.Chapter
import app.cuisson.domain.Cookbook
import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.MeasureUnit
import app.cuisson.domain.QuantityRange
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Servings
import app.cuisson.domain.Source
import app.cuisson.domain.SourceNote
import app.cuisson.domain.sourceUrlKey
import app.cuisson.domain.SourceKind
import app.cuisson.domain.Step
import app.cuisson.domain.Timings
import app.cuisson.text.durationInStep
import app.cuisson.domain.UnitSystem
import kotlin.time.Instant

internal data class StepText(val id: String, val text: String)

/** How many times a recipe has been cooked, and when it last was. */
data class CookRecord(val count: Int, val lastCooked: Long?)

/** Just enough of an already-saved recipe to offer it instead of a duplicate. */
data class SavedSource(val id: RecipeId, val title: String)

private const val REF_SEPARATOR = "\n"

class RecipeRepository(private val database: CuissonDatabase) {

    private val queries = database.recipeQueries

    /** Exposed for tests that need to disturb the index deliberately. */
    internal fun database() = database

    fun count(): Long = queries.countRecipes().executeAsOne()

    /**
     * The library, kept current.
     *
     * A recipe's picture is downloaded a moment after the recipe itself is saved, so a
     * screen that reads the database once shows the recipe without its image and has no
     * way to learn otherwise. Observing the table instead of snapshotting it removes that
     * whole class of staleness rather than papering over this one instance of it.
     */
    fun observeAll(): Flow<List<Recipe>> =
        queries.selectAllRecipes()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map(::toRecipe) }

    fun all(): List<Recipe> = queries.selectAllRecipes().executeAsList().map(::toRecipe)

    private fun toRecipe(row: Recipe_): Recipe {
        val id = RecipeId(row.id)
        return Recipe(
            id = id,
            title = row.title,
            rawTitle = row.title_raw,
            source = Source(
                kind = row.source_kind.toSourceKind(),
                url = row.source_url,
                name = row.source_name,
            ),
            servings = row.servings_count?.let { Servings(it, row.servings_unit) },
            timings = Timings(
                prepMinutes = row.prep_minutes?.toInt(),
                cookMinutes = row.cook_minutes?.toInt(),
                totalMinutes = row.total_minutes?.toInt(),
            ),
            ingredients = ingredientsFor(id),
            steps = stepsFor(id),
            notes = row.notes,
            sourceNotes = notesFor(id),
            imagePath = row.image_path,
            chapterId = row.chapter_id,
            language = row.language,
            extraction = Extraction(
                tier = row.extraction_tier.toTier(),
                confidence = row.extraction_conf.toFloat(),
                needsReview = row.needs_review == 1L,
            ),
            createdAt = Instant.fromEpochMilliseconds(row.created_at),
            updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
        )
    }

    /**
     * Finds recipes by name or by what goes in them.
     *
     * Every term is matched as a prefix, because someone typing "cho" for chocolate
     * expects to see it before they have finished the word. A query that FTS5 cannot
     * parse, which mostly means stray punctuation, returns nothing rather than throwing.
     */
    fun search(query: String): List<RecipeId> {
        val terms = query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { term -> term.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" ") { "$it*" }
        // FTS columns are nullable in the generated types, so a row can in principle
        // come back without an id. One that does is not a recipe we can open.
        return runCatching {
            queries.searchRecipes(match).executeAsList()
                .mapNotNull { row -> row.recipe_id?.let(::RecipeId) }
        }.getOrDefault(emptyList<RecipeId>())
    }

    /**
     * Puts recipes saved before search existed into the index.
     *
     * A migration cannot do this: the index is built from the recipe and its ingredient
     * lines together, which is a join rather than a column default.
     */
    fun backfillSearchIfEmpty() {
        runCatching {
            if (queries.countSearchRows().executeAsOne() > 0L) return
            all().forEach { recipe -> database.transaction { indexRecipe(recipe) } }
        }
    }

    /**
     * Best effort on purpose. A recipe the user just saved matters more than whether it
     * can be found by searching, so a problem with the index is swallowed rather than
     * allowed to fail the save.
     */
    private fun indexRecipe(recipe: Recipe) = runCatching {
        queries.deleteSearchRow(recipe.id.value)
        queries.insertSearchRow(
            title = listOfNotNull(recipe.title, recipe.rawTitle).joinToString(" "),
            // Both wordings, so a line renamed from "scallions" to "spring onions" is
            // still found by either.
            ingredients = recipe.ingredients
                .flatMap { listOfNotNull(it.rawText, it.amendment) }
                .joinToString(" "),
            notes = listOfNotNull(
                recipe.notes,
                recipe.sourceNotes.joinToString(" ") { it.text }.takeIf { it.isNotBlank() },
            ).joinToString(" "),
            recipe_id = recipe.id.value,
        )
    }

    fun save(recipe: Recipe) {
        database.transaction {
            indexRecipe(recipe)
            queries.insertRecipe(
                id = recipe.id.value,
                title = recipe.title,
                title_raw = recipe.rawTitle,
                source_kind = recipe.source.kind.name,
                source_url = recipe.source.url,
                source_name = recipe.source.name,
                servings_count = recipe.servings?.count,
                servings_unit = recipe.servings?.unit,
                prep_minutes = recipe.timings.prepMinutes?.toLong(),
                cook_minutes = recipe.timings.cookMinutes?.toLong(),
                total_minutes = recipe.timings.totalMinutes?.toLong(),
                notes = recipe.notes,
                image_path = recipe.imagePath,
                // Nothing is ever homeless: a recipe nobody has filed goes into the
                // default chapter of Unfiled.
                chapter_id = recipe.chapterId ?: defaultChapterOf(Cookbook.UNFILED),
                language = recipe.language,
                extraction_tier = recipe.extraction.tier.name,
                extraction_conf = recipe.extraction.confidence.toDouble(),
                needs_review = if (recipe.extraction.needsReview) 1L else 0L,
                created_at = recipe.createdAt.toEpochMilliseconds(),
                updated_at = recipe.updatedAt.toEpochMilliseconds(),
            )
            recipe.ingredients.forEach { line ->
                queries.insertIngredientLine(
                    id = line.id,
                    recipe_id = recipe.id.value,
                    position = line.position.toLong(),
                    raw_text = line.rawText,
                    amendment = line.amendment,
                    group_label = line.groupLabel,
                    quantity_min = line.quantity?.min,
                    quantity_max = line.quantity?.max,
                    unit_canonical = line.unit?.canonical,
                    unit_system = line.unit?.system?.name,
                    unit_alt_canonical = line.unit?.alternate?.canonical,
                    unit_alt_system = line.unit?.alternate?.system?.name,
                    item_text = line.itemText,
                    preparation = line.preparation,
                    optional = if (line.optional) 1L else 0L,
                    canonical_item_id = line.canonicalItemId,
                    parse_confidence = line.parseConfidence.toDouble(),
                )
            }
            recipe.sourceNotes.forEachIndexed { index, note ->
                queries.insertSourceNote(
                    id = "${recipe.id.value}-n$index",
                    recipe_id = recipe.id.value,
                    position = index.toLong(),
                    label = note.label,
                    text = note.text,
                )
            }
            recipe.steps.forEach { step ->
                queries.insertStep(
                    id = step.id,
                    recipe_id = recipe.id.value,
                    position = step.position.toLong(),
                    text = step.sourceText,
                    amendment = step.amendment,
                    // Read here as well as at import, so no path can produce a recipe
                    // whose steps have no timers. A step that already carries one keeps
                    // it: the extractor may have read a duration this cannot see.
                    duration_seconds = (step.durationSeconds ?: durationInStep(step.text))
                        ?.toLong(),
                    unresolved_refs = step.references
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(REF_SEPARATOR),
                )
            }
        }
    }

    /**
     * Writes an edited recipe over the one already stored.
     *
     * [save] alone is not enough: it inserts or replaces each line by id, so a line the
     * user deleted would sit there untouched, having survived its own deletion. The old
     * lines go first and the new set is written whole.
     *
     * Durations are read again from every step as it goes in. A step someone rewrote from
     * twenty minutes to twenty-five has to offer the timer they meant, and the reading is
     * cheap enough to do for all of them rather than guess which ones changed.
     */
    fun replace(recipe: Recipe) {
        // Cleared rather than kept, because save fills in only what is missing and an
        // edited step needs its old reading thrown away before the new one is taken.
        val timed = recipe.copy(steps = recipe.steps.map { it.copy(durationSeconds = null) })
        database.transaction {
            queries.deleteIngredientsOf(recipe.id.value)
            queries.deleteStepsOf(recipe.id.value)
            save(timed)
        }
    }

    /**
     * Removes a recipe and everything hanging off it.
     *
     * Written out rather than left to ON DELETE CASCADE, because SQLite enforces foreign
     * keys only when the connection asks it to and Android's driver does not by default.
     * A cascade that silently does nothing leaves orphan rows nobody ever looks at again.
     */
    fun delete(id: RecipeId) {
        database.transaction {
            runCatching { queries.deleteSearchRow(id.value) }
            queries.deleteIngredientsOf(id.value)
            queries.deleteStepsOf(id.value)
            queries.deleteNotesOf(id.value)
            queries.deleteCookEntriesOf(id.value)
            queries.deleteRecipe(id.value)
        }
    }

    /**
     * The recipe already saved from this address, if there is one.
     *
     * Addresses are compared by their identifying part rather than as written, because
     * the same page arrives with and without tracking parameters. See [sourceUrlKey].
     */
    fun findBySourceUrl(url: String?): SavedSource? {
        val key = sourceUrlKey(url) ?: return null
        return queries.selectSources().executeAsList()
            .firstOrNull { sourceUrlKey(it.source_url) == key }
            ?.let { SavedSource(RecipeId(it.id), it.title) }
    }

    fun cookbooks(): List<Cookbook> =
        queries.selectCookbooks().executeAsList()
            .map { Cookbook(it.id, it.name, it.recipes.toInt()) }

    fun observeCookbooks(): Flow<List<Cookbook>> =
        queries.selectCookbooks()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { Cookbook(it.id, it.name, it.recipes.toInt()) }
            }

    fun chaptersOf(cookbookId: String): List<Chapter> =
        queries.selectChapters(cookbookId).executeAsList()
            .map { Chapter(it.id, it.cookbook_id, it.name) }

    /**
     * A cookbook's chapters, kept current, so one just added appears without leaving the
     * screen and coming back.
     */
    fun observeChapters(cookbookId: String): Flow<List<Chapter>> =
        queries.selectChapters(cookbookId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { Chapter(it.id, it.cookbook_id, it.name) } }

    fun renameChapter(id: String, name: String) = queries.renameChapter(name.trim(), id)

    /**
     * Removes a chapter and keeps what was in it.
     *
     * The recipes move to the cookbook's unnamed chapter rather than going with it. A
     * chapter is a heading, and deleting a heading is not deleting what was under it.
     */
    fun deleteChapter(id: String, cookbookId: String) {
        database.transaction {
            val home = defaultChapterOf(cookbookId) ?: return@transaction
            if (home == id) return@transaction
            queries.recipesInChapter(id).executeAsList().forEach {
                queries.fileRecipe(home, it.updated_at, it.id)
            }
            queries.deleteChapter(id)
        }
    }

    /**
     * Creates Unfiled on a database that has never had it.
     *
     * Migration 4 creates it for databases that predate cookbooks, and a schema created
     * fresh at the current version runs no migrations, so a new install had no cookbooks
     * at all: every recipe saved landed with no chapter, the Cookbooks tab was empty, and
     * "File in" never appeared because there was nowhere to file. Nothing is ever
     * homeless, and that has to be true on the first launch as well as the hundredth.
     *
     * The two halves are checked separately because a database can hold one without the
     * other, and a cookbook with no chapter is a cookbook nothing can be put in.
     */
    fun ensureUnfiled() {
        database.transaction {
            if (queries.selectCookbooks().executeAsList().none { it.id == Cookbook.UNFILED }) {
                queries.insertCookbook(Cookbook.UNFILED, "Unfiled", 0, 0)
            }
            if (queries.selectChapters(Cookbook.UNFILED).executeAsList().isEmpty()) {
                queries.insertChapter("${Cookbook.UNFILED}-ch", Cookbook.UNFILED, "", 0)
            }
        }
    }

    fun defaultChapterOf(cookbookId: String): String? =
        queries.defaultChapterOf(cookbookId).executeAsOneOrNull()

    /** Creates a Cookbook together with the unnamed Chapter every Cookbook has. */
    fun createCookbook(id: String, name: String, now: Long): Cookbook {
        database.transaction {
            val position = queries.selectCookbooks().executeAsList().size.toLong()
            queries.insertCookbook(id, name.trim(), position, now)
            queries.insertChapter("$id-ch", id, "", 0)
        }
        return Cookbook(id, name.trim(), 0)
    }

    fun addChapter(id: String, cookbookId: String, name: String) {
        val position = queries.selectChapters(cookbookId).executeAsList().size.toLong()
        queries.insertChapter(id, cookbookId, name.trim(), position)
    }

    fun renameCookbook(id: String, name: String) = queries.renameCookbook(name.trim(), id)

    fun deleteCookbook(id: String) {
        if (id == Cookbook.UNFILED) return
        database.transaction {
            // The recipes outlive the cookbook. Deleting a shelf is not deleting books.
            val home = defaultChapterOf(Cookbook.UNFILED) ?: return@transaction
            chaptersOf(id).forEach { chapter ->
                queries.selectAllRecipes().executeAsList()
                    .filter { it.chapter_id == chapter.id }
                    .forEach { queries.fileRecipe(home, it.updated_at, it.id) }
            }
            queries.deleteCookbook(id)
        }
    }

    fun fileRecipe(id: RecipeId, chapterId: String, now: Long) =
        queries.fileRecipe(chapterId, now, id.value)

    /**
     * Reads timers into steps saved before timers existed.
     *
     * Durations are parsed at import, so every recipe imported before that went to Cook
     * Mode with no timers at all. Re-importing a library to gain a feature is not a
     * reasonable thing to ask of anyone.
     */
    fun backfillStepDurations(all: Boolean = false) {
        runCatching {
            val pending = if (all) {
                queries.selectAllSteps().executeAsList().map { it.id to it.text }
                    .map { (id, text) -> StepText(id, text) }
            } else {
                queries.stepsWithoutDuration().executeAsList()
                    .map { StepText(it.id, it.text) }
            }
            if (pending.isEmpty()) return
            database.transaction {
                pending.forEach { step ->
                    queries.setStepDuration(durationInStep(step.text)?.toLong(), step.id)
                }
            }
        }
    }

    /** Written with one tap at the end of Cook Mode. */
    fun logCook(id: RecipeId, at: Long, entryId: String) =
        queries.insertCookEntry(entryId, id.value, at, null, null)

    /**
     * The cooking record, kept current.
     *
     * Reading it once was not enough: logging a cook writes to cook_entry and the library
     * observes recipe, so nothing recomposed and the recipe went on claiming it had never
     * been cooked.
     */
    fun observeCookRecord(id: RecipeId): Flow<CookRecord> =
        queries.cookEntriesFor(id.value)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entries -> CookRecord(entries.size, entries.firstOrNull()?.cooked_at) }

    fun cookCount(id: RecipeId): Int =
        queries.cookEntriesFor(id.value).executeAsList().size

    fun lastCooked(id: RecipeId): Long? =
        queries.cookEntriesFor(id.value).executeAsList().firstOrNull()?.cooked_at

    fun setImagePath(id: RecipeId, path: String) {
        queries.setImagePath(path, id.value)
    }

    fun ingredientsFor(id: RecipeId): List<IngredientLine> =
        queries.selectIngredientsForRecipe(id.value).executeAsList().map { row ->
            IngredientLine(
                id = row.id,
                position = row.position.toInt(),
                rawText = row.raw_text,
                amendment = row.amendment,
                groupLabel = row.group_label,
                quantity = row.quantity_min?.let {
                    QuantityRange(it, row.quantity_max ?: it)
                },
                unit = row.unit_canonical?.let { canonical ->
                    MeasureUnit(
                        canonical = canonical,
                        system = row.unit_system.toUnitSystem(),
                        alternate = row.unit_alt_canonical?.let { alt ->
                            MeasureUnit(alt, row.unit_alt_system.toUnitSystem())
                        },
                    )
                },
                itemText = row.item_text,
                preparation = row.preparation,
                optional = row.optional == 1L,
                canonicalItemId = row.canonical_item_id,
                parseConfidence = row.parse_confidence.toFloat(),
            )
        }

    fun notesFor(id: RecipeId): List<SourceNote> =
        queries.selectNotesForRecipe(id.value).executeAsList().map {
            SourceNote(label = it.label, text = it.text)
        }

    fun stepsFor(id: RecipeId): List<Step> =
        queries.selectStepsForRecipe(id.value).executeAsList().map { row ->
            Step(
                id = row.id,
                position = row.position.toInt(),
                sourceText = row.text,
                amendment = row.amendment,
                durationSeconds = row.duration_seconds?.toInt(),
                references = row.unresolved_refs
                    ?.split(REF_SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
            )
        }
}

private fun String?.toUnitSystem(): UnitSystem =
    this?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.NONE

private fun String.toSourceKind(): SourceKind =
    runCatching { SourceKind.valueOf(this) }.getOrDefault(SourceKind.MANUAL)

private fun String.toTier(): ExtractionTier =
    runCatching { ExtractionTier.valueOf(this) }.getOrDefault(ExtractionTier.HAND_WRITTEN)

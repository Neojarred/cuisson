package app.cuisson.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuisson.data.db.CuissonDatabase
import app.cuisson.data.db.Recipe as Recipe_
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.MeasureUnit
import app.cuisson.domain.QuantityRange
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Servings
import app.cuisson.domain.Source
import app.cuisson.domain.sourceUrlKey
import app.cuisson.domain.SourceKind
import app.cuisson.domain.Step
import app.cuisson.domain.Timings
import app.cuisson.domain.UnitSystem
import kotlin.time.Instant

/** Just enough of an already-saved recipe to offer it instead of a duplicate. */
data class SavedSource(val id: RecipeId, val title: String)

private const val REF_SEPARATOR = "\n"

class RecipeRepository(private val database: CuissonDatabase) {

    private val queries = database.recipeQueries

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
            imagePath = row.image_path,
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

    fun save(recipe: Recipe) {
        database.transaction {
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
            recipe.steps.forEach { step ->
                queries.insertStep(
                    id = step.id,
                    recipe_id = recipe.id.value,
                    position = step.position.toLong(),
                    text = step.text,
                    duration_seconds = step.durationSeconds?.toLong(),
                    unresolved_refs = step.references
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(REF_SEPARATOR),
                )
            }
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

    fun setImagePath(id: RecipeId, path: String) {
        queries.setImagePath(path, id.value)
    }

    fun ingredientsFor(id: RecipeId): List<IngredientLine> =
        queries.selectIngredientsForRecipe(id.value).executeAsList().map { row ->
            IngredientLine(
                id = row.id,
                position = row.position.toInt(),
                rawText = row.raw_text,
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

    fun stepsFor(id: RecipeId): List<Step> =
        queries.selectStepsForRecipe(id.value).executeAsList().map { row ->
            Step(
                id = row.id,
                position = row.position.toInt(),
                text = row.text,
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

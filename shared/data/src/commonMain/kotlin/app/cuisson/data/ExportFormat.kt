package app.cuisson.data

import kotlinx.serialization.Serializable

/**
 * The `.cuisson` Export File format, version 1. Documented in docs/export-format.md.
 *
 * These classes are the format, so they change only with a new format version and a
 * reader for the old one. They deliberately do not reuse the app's own model classes: those
 * change whenever the app does, and an Export File made today has to open in a version of Cuisson
 * written years from now.
 *
 * Nothing that can be worked out again is stored. Parsed ingredients, step timers and the
 * search index are rebuilt when an Export File is restored, so a better parser in a later version
 * improves an old Export File too.
 */
const val EXPORT_FORMAT = "cuisson"
const val EXPORT_FORMAT_VERSION = 1

@Serializable
data class ExportManifest(
    val format: String = EXPORT_FORMAT,
    val formatVersion: Int = EXPORT_FORMAT_VERSION,
    val appVersion: String,
    val exportedAt: Long,
    val recipes: Int,
    val images: Int,
    val cookbooks: Int,
    val shoppingLists: Int,
)

@Serializable
data class ExportLibrary(
    val recipes: List<ExportRecipe>,
    val cookbooks: List<ExportCookbook>,
    val cookEntries: List<ExportCookEntry>,
    val shoppingLists: List<ExportShoppingList>,
    val corrections: List<ExportCorrection>,
    val aisleCorrections: List<ExportAisle>,
)

@Serializable
data class ExportRecipe(
    val id: String,
    val title: String,
    val titleAsPublished: String? = null,
    val sourceKind: String,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val servings: Double? = null,
    val servingsUnit: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val note: String? = null,
    /** The entry in the zip holding the picture, under media/. */
    val image: String? = null,
    val chapterId: String? = null,
    val language: String,
    val extractionTier: String,
    val extractionConfidence: Float,
    val needsReview: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val ingredients: List<ExportLine>,
    val steps: List<ExportStep>,
    val sourceNotes: List<ExportSourceNote>,
)

/** A line as published, and the user's own wording of it where they changed it. */
@Serializable
data class ExportLine(
    val id: String,
    val rawText: String,
    val amendment: String? = null,
    val group: String? = null,
)

@Serializable
data class ExportStep(
    val id: String,
    val text: String,
    val amendment: String? = null,
    val references: List<String> = emptyList(),
)

@Serializable
data class ExportSourceNote(val label: String? = null, val text: String)

@Serializable
data class ExportCookbook(
    val id: String,
    val name: String,
    val position: Long,
    val createdAt: Long,
    val chapters: List<ExportChapter>,
)

@Serializable
data class ExportChapter(val id: String, val name: String, val position: Long)

@Serializable
data class ExportCookEntry(
    val id: String,
    val recipeId: String,
    val cookedAt: Long,
    val rating: Long? = null,
    val note: String? = null,
)

@Serializable
data class ExportShoppingList(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val archivedAt: Long? = null,
    val recipes: List<ExportListRecipe>,
    val ownLines: List<ExportOwnLine>,
    val typedAmounts: List<ExportTypedAmount>,
)

@Serializable
data class ExportListRecipe(val recipeId: String, val servings: Double? = null, val addedAt: Long)

@Serializable
data class ExportOwnLine(val id: String, val text: String, val addedAt: Long)

@Serializable
data class ExportTypedAmount(val itemKey: String, val amount: String, val basis: String)

/** "This written form is that ingredient", as the user corrected it. */
@Serializable
data class ExportCorrection(val writtenAs: String, val ingredient: String, val createdAt: Long)

@Serializable
data class ExportAisle(val ingredient: String, val aisle: String)

package app.cuisson.domain

/**
 * A named book of recipes, divided into Chapters. A Recipe lives in exactly one.
 *
 * One home per recipe is deliberate. Filing something in several places feels flexible
 * and then makes "where is this?" a question with no answer. Tags cut across instead.
 */
data class Cookbook(
    val id: String,
    val name: String,
    val recipeCount: Int,
) {
    /** Unfiled is a Cookbook like any other, except that it cannot be removed. */
    val isUnfiled: Boolean get() = id == UNFILED

    companion object {
        const val UNFILED = "unfiled"
    }
}

/**
 * A named division inside one Cookbook.
 *
 * Every Cookbook has one with no name, created with it, which is what makes chapters
 * optional: a cookbook of six recipes needs none. It is only shown once a second exists.
 */
data class Chapter(
    val id: String,
    val cookbookId: String,
    val name: String,
) {
    val isDefault: Boolean get() = name.isBlank()
}

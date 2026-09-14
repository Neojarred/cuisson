package app.cuisson.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuisson.data.db.CuissonDatabase
import app.cuisson.data.db.Shopping_list
import app.cuisson.domain.RecipeId
import app.cuisson.text.Aisle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Shopping Lists, and the corrections that shape them.
 *
 * Every read works the items out again from the recipes on the list, so a list is always
 * what its recipes currently ask for. See Shopping.sq for why nothing else is stored.
 */
class ShoppingRepository(
    private val database: CuissonDatabase,
    private val recipes: RecipeRepository,
) {
    private val queries = database.shoppingQueries

    fun observeLists(): Flow<List<ShoppingListSummary>> =
        queries.selectLists()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map {
                    ShoppingListSummary(
                        it.id, it.name, it.created_at, it.last_used_at, it.archived_at,
                        it.recipes.toInt(),
                    )
                }
            }

    fun lists(): List<ShoppingListSummary> =
        queries.selectLists().executeAsList().map {
            ShoppingListSummary(
                it.id, it.name, it.created_at, it.last_used_at, it.archived_at, it.recipes.toInt(),
            )
        }

    /**
     * One list, worked out afresh whenever anything it depends on changes: its recipes, its
     * own lines, its ticks and typed amounts, the corrections, and the recipes themselves.
     */
    fun observeList(listId: String, language: String): Flow<ShoppingListView?> =
        merge(
            queries.selectList(listId).asFlow().map { },
            queries.selectListRecipes(listId).asFlow().map { },
            queries.selectOwnItems(listId).asFlow().map { },
            queries.selectTicks(listId).asFlow().map { },
            queries.selectOverrides(listId).asFlow().map { },
            queries.selectAliases().asFlow().map { },
            queries.selectAisles().asFlow().map { },
            database.recipeQueries.selectAllRecipes().asFlow().map { },
        )
            .conflate()
            .map { view(listId, language) }
            .flowOn(Dispatchers.Default)

    fun view(listId: String, language: String): ShoppingListView? {
        val row = queries.selectList(listId).executeAsOneOrNull() ?: return null
        val entries = queries.selectListRecipes(listId).executeAsList()
        val onList = entries.mapNotNull { entry ->
            recipes.find(RecipeId(entry.recipe_id))?.let { RecipeOnList(it, entry.servings) }
        }
        val own = queries.selectOwnItems(listId).executeAsList().map { OwnLine(it.id, it.text) }
        val groups = consolidate(
            recipes = onList,
            own = own,
            ticks = queries.selectTicks(listId).executeAsList().toSet(),
            typed = typedAmounts(listId),
            corrections = corrections(),
            aisles = aisleCorrections(),
            language = language,
        )
        return ShoppingListView(
            summary = summaryOf(row, onList.size),
            recipes = onList.map {
                ListRecipe(it.recipe.id, it.recipe.title, it.servings, it.recipe.servings?.count)
            },
            own = own,
            groups = groups,
        )
    }

    fun createList(id: String, name: String, now: Long) {
        queries.insertList(id, name.trim(), now, now)
    }

    fun renameList(id: String, name: String) = queries.renameList(name.trim(), id)

    fun touchList(id: String, now: Long) = queries.touchList(now, id)

    fun archiveList(id: String, now: Long) = queries.archiveList(now, id)

    /** Brought back from the archive with everything unticked, ready to shop again. */
    fun reviveList(id: String, now: Long) = database.transaction {
        queries.reviveList(now, id)
        queries.clearTicks(id)
    }

    fun deleteList(id: String) = database.transaction {
        queries.deleteListRecipes(id)
        queries.deleteListOwnItems(id)
        queries.clearTicks(id)
        queries.clearOverrides(id)
        queries.deleteList(id)
    }

    /**
     * Puts a recipe on a list at the servings on screen. A recipe already there has its
     * servings updated to those, rather than appearing twice.
     */
    fun addRecipe(listId: String, recipeId: RecipeId, servings: Double?, now: Long) =
        changing(listId) {
            val present = queries.selectListRecipe(listId, recipeId.value).executeAsOneOrNull()
            if (present != null) {
                queries.updateServings(servings, listId, recipeId.value)
            } else {
                queries.insertListRecipe(listId, recipeId.value, servings, now)
            }
            queries.touchList(now, listId)
        }

    fun setServings(listId: String, recipeId: RecipeId, servings: Double) =
        changing(listId) { queries.updateServings(servings, listId, recipeId.value) }

    /** Everything the recipe asked for goes with it. The user's own lines stay. */
    fun removeRecipe(listId: String, recipeId: RecipeId) =
        changing(listId) { queries.removeListRecipe(listId, recipeId.value) }

    fun addOwnLine(listId: String, id: String, text: String, now: Long) {
        if (text.isBlank()) return
        changing(listId) { queries.insertOwnItem(id, listId, text.trim(), now) }
    }

    fun removeOwnLine(listId: String, id: String) =
        changing(listId) { queries.deleteOwnItem(id) }

    fun setTicked(listId: String, key: String, ticked: Boolean) {
        if (ticked) queries.insertTick(listId, key) else queries.deleteTick(listId, key)
    }

    /** Typing an amount is changing the list, so the ticks go, as they do for any change. */
    fun setAmount(listId: String, item: ShoppingItem, amount: String) = database.transaction {
        if (amount.isBlank()) {
            queries.deleteOverride(listId, item.key)
        } else {
            queries.upsertOverride(listId, item.key, amount.trim(), item.basis)
        }
        queries.clearTicks(listId)
    }

    fun resetAmount(listId: String, key: String) = database.transaction {
        queries.deleteOverride(listId, key)
        queries.clearTicks(listId)
    }

    /**
     * "This is the same as that." Remembered against every written form the item was
     * recognised by, so the same line in any future recipe lands in the right place.
     */
    fun sameAs(listId: String, item: ShoppingItem, targetId: String, now: Long) =
        changing(listId) {
            item.recognisedAs.forEach { queries.upsertAlias(it, targetId, now) }
        }

    fun moveTo(listId: String, item: ShoppingItem, aisle: Aisle) =
        changing(listId) { queries.upsertAisle(item.key, aisle.name) }

    /**
     * A change to what a list holds.
     *
     * Every tick goes: a list is built, then shopped, and a tick on something whose amount
     * just moved is a tick that lies. An amount typed by hand goes only when the item it
     * was typed over has itself changed, which is last change wins applied per item, so
     * adding bin bags never undoes the butter you corrected.
     */
    private fun changing(listId: String, block: () -> Unit) {
        val before = basesOf(listId)
        database.transaction {
            block()
            queries.clearTicks(listId)
        }
        val after = basesOf(listId)
        val stale = queries.selectOverrides(listId).executeAsList()
            .filter { before[it.item_key] != after[it.item_key] }
        if (stale.isNotEmpty()) {
            database.transaction { stale.forEach { queries.deleteOverride(listId, it.item_key) } }
        }
    }

    private fun basesOf(listId: String): Map<String, String> =
        view(listId, "en")?.groups?.flatMap { it.items }?.associate { it.key to it.basis }
            ?: emptyMap()

    private fun typedAmounts(listId: String): Map<String, TypedAmount> =
        queries.selectOverrides(listId).executeAsList()
            .associate { it.item_key to TypedAmount(it.amount, it.basis) }

    private fun corrections(): Map<String, String> =
        queries.selectAliases().executeAsList().associate { it.alias_key to it.canonical_id }

    private fun aisleCorrections(): Map<String, Aisle> =
        queries.selectAisles().executeAsList().mapNotNull { row ->
            runCatching { Aisle.valueOf(row.aisle) }.getOrNull()?.let { row.canonical_id to it }
        }.toMap()

    private fun summaryOf(row: Shopping_list, recipeCount: Int) = ShoppingListSummary(
        row.id, row.name, row.created_at, row.last_used_at, row.archived_at, recipeCount,
    )
}

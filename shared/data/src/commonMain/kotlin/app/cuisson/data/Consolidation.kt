package app.cuisson.data

import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.text.Aisle
import app.cuisson.text.IngredientCatalogue
import app.cuisson.text.Resolution
import app.cuisson.text.SoldBy
import app.cuisson.text.UnitKind
import app.cuisson.text.formatAmount
import app.cuisson.text.parseIngredient
import app.cuisson.text.resolveIngredient
import app.cuisson.text.scaleIngredient
import kotlin.math.roundToLong

data class ShoppingListSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val archivedAt: Long?,
    val recipeCount: Int,
) {
    val archived: Boolean get() = archivedAt != null
}

/** A recipe on a list, at the servings it was added for. */
data class RecipeOnList(val recipe: Recipe, val servings: Double?)

data class ListRecipe(
    val recipeId: RecipeId,
    val title: String,
    val servings: Double?,
    val writtenFor: Double?,
)

/** A line the user added to a list themselves. */
data class OwnLine(val id: String, val text: String)

/** One share of a Shopping Item: a recipe's line, or the user's own. */
sealed interface Contribution {
    /** The line as the reader would see it, scaled to the servings on the list. */
    val line: String

    data class FromRecipe(
        val recipeId: RecipeId,
        val title: String,
        val servings: Double?,
        override val line: String,
    ) : Contribution

    data class FromYou(val ownId: String, override val line: String) : Contribution
}

data class ShoppingItem(
    /** The ingredient's id, which is also what ticks and typed amounts are kept against. */
    val key: String,
    val name: String,
    /** What the list shows: the user's own figure where one still stands. */
    val amount: String,
    /** What the contributions add up to. */
    val computed: String,
    /** [computed] without language, so a typed figure can tell when it has gone stale. */
    val basis: String,
    val setByHand: Boolean,
    val ticked: Boolean,
    val aisle: Aisle,
    val learned: Boolean,
    /** The keys each contribution was recognised by, which is what a correction rewrites. */
    val recognisedAs: Set<String>,
    val contributions: List<Contribution>,
)

data class AisleGroup(val aisle: Aisle, val label: String, val items: List<ShoppingItem>)

data class ShoppingListView(
    val summary: ShoppingListSummary,
    val recipes: List<ListRecipe>,
    val own: List<OwnLine>,
    val groups: List<AisleGroup>,
)

/** An amount the user typed, and the computed amount it was typed over. */
data class TypedAmount(val amount: String, val basis: String)

private val GRAMS = mapOf("g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "oz" to 28.3495, "lb" to 453.592)

private val MILLILITRES = mapOf(
    "ml" to 1.0, "cl" to 10.0, "l" to 1000.0, "tsp" to 4.92892, "tbsp" to 14.7868,
    "cup" to 236.588, "floz" to 29.5735, "pt" to 473.176, "qt" to 946.353,
)

private val SPOONS = setOf("tsp", "tbsp")

private class Tally(val resolution: Resolution) {
    var grams = 0.0
    var hasGrams = false
    var millilitres = 0.0
    var hasMillilitres = false
    var spoonsOnly = true
    val counts = linkedMapOf<String, Double>()
    val recognisedAs = linkedSetOf<String>()
    val contributions = mutableListOf<Contribution>()

    fun count(unit: String, amount: Double) {
        counts[unit] = (counts[unit] ?: 0.0) + amount
    }
}

/**
 * Turns the recipes on a list, and the user's own lines, into what to buy.
 *
 * Lines add up only when they are the same ingredient. "Garlic cloves" and "gousses d'ail"
 * are one ingredient; "onions" and "red onions" are two, and sit next to each other because
 * they share a family. Amounts that cannot be added share one line rather than being
 * converted with a number nobody measured: "2, and 400 g" of carrots. Volume and weight meet
 * only where the catalogue holds a vetted density.
 *
 * Pure, so it can be tested without a database and run again whenever anything changes.
 */
fun consolidate(
    recipes: List<RecipeOnList>,
    own: List<OwnLine>,
    ticks: Set<String> = emptySet(),
    typed: Map<String, TypedAmount> = emptyMap(),
    corrections: Map<String, String> = emptyMap(),
    aisles: Map<String, Aisle> = emptyMap(),
    language: String = "en",
): List<AisleGroup> {
    val tallies = linkedMapOf<String, Tally>()

    fun add(text: String, factor: Double, contribution: Contribution) {
        val parsed = parseIngredient(text)
        val item = parsed.item ?: return
        val resolution = resolveIngredient(item, corrections)
        // Water, and anything else nobody goes to a shop for.
        if (!resolution.shoppable) return

        val tally = tallies.getOrPut(resolution.id) { Tally(resolution) }
        tally.recognisedAs += resolution.key
        tally.contributions += contribution

        val quantity = parsed.quantityMin ?: return
        val amount = quantity * factor
        val unit = parsed.unit
        when {
            unit == null -> tally.count(resolution.ingredient?.countAs ?: "", amount)
            unit in GRAMS -> {
                tally.grams += amount * GRAMS.getValue(unit)
                tally.hasGrams = true
            }
            unit in MILLILITRES -> {
                tally.millilitres += amount * MILLILITRES.getValue(unit)
                tally.hasMillilitres = true
                if (unit !in SPOONS) tally.spoonsOnly = false
            }
            parsed.unitSystem == UnitKind.COUNT -> tally.count(unit, amount)
            // A pinch or a handful does not change what anybody buys.
            else -> Unit
        }
    }

    recipes.forEach { entry ->
        val written = entry.recipe.servings?.count
        val factor = if (written != null && written > 0 && entry.servings != null) {
            entry.servings / written
        } else {
            1.0
        }
        entry.recipe.ingredients.forEach { line ->
            add(
                text = line.text,
                factor = factor,
                contribution = Contribution.FromRecipe(
                    recipeId = entry.recipe.id,
                    title = entry.recipe.title,
                    servings = entry.servings ?: written,
                    line = scaleIngredient(line.text, factor),
                ),
            )
        }
    }
    own.forEach { add(it.text, 1.0, Contribution.FromYou(it.id, it.text)) }

    val items = tallies.map { (key, tally) -> itemFrom(key, tally, ticks, typed, aisles, language) }

    return Aisle.entries.mapNotNull { aisle ->
        val here = items.filter { it.aisle == aisle }
        if (here.isEmpty()) return@mapNotNull null
        AisleGroup(aisle, aisle.label(language), here.sortedWith(byFamilyThenName(language)))
    }
}

private fun itemFrom(
    key: String,
    tally: Tally,
    ticks: Set<String>,
    typed: Map<String, TypedAmount>,
    aisles: Map<String, Aisle>,
    language: String,
): ShoppingItem {
    val ingredient = tally.resolution.ingredient
    val density = ingredient?.gramsPerMl
    if (density != null) {
        when (ingredient.soldBy) {
            SoldBy.MASS -> if (tally.hasMillilitres) {
                tally.grams += tally.millilitres * density
                tally.hasGrams = true
                tally.millilitres = 0.0
                tally.hasMillilitres = false
            }
            SoldBy.VOLUME -> if (tally.hasGrams) {
                tally.millilitres += tally.grams / density
                tally.hasMillilitres = true
                tally.spoonsOnly = false
                tally.grams = 0.0
                tally.hasGrams = false
            }
            null -> Unit
        }
    }

    val french = language == "fr"
    val parts = mutableListOf<String>()
    tally.counts[""]?.let { parts += formatAmount(eighths(it), french) }
    tally.counts.filterKeys { it.isNotEmpty() }.forEach { (unit, amount) ->
        parts += "${formatAmount(eighths(amount), french)} ${countLabel(unit, amount, french)}"
    }
    if (tally.hasGrams) parts += grams(tally.grams, french)
    if (tally.hasMillilitres) parts += millilitres(tally.millilitres, tally.spoonsOnly, french)

    val computed = when (parts.size) {
        0 -> ""
        1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + (if (french) " et " else ", and ") + parts.last()
    }
    val basis = buildString {
        tally.counts.toSortedMap().forEach { (unit, amount) -> append("$unit=${round1(amount)};") }
        if (tally.hasGrams) append("g=${round1(tally.grams)};")
        if (tally.hasMillilitres) append("ml=${round1(tally.millilitres)};")
    }
    val override = typed[key]?.takeIf { it.basis == basis }

    val name = (ingredient?.name(language) ?: tally.resolution.name)
        .replaceFirstChar { it.uppercase() }

    return ShoppingItem(
        key = key,
        name = name,
        amount = override?.amount ?: computed,
        computed = computed,
        basis = basis,
        setByHand = override != null,
        ticked = key in ticks,
        aisle = aisles[key] ?: tally.resolution.aisle,
        learned = tally.resolution.learned,
        recognisedAs = tally.recognisedAs,
        contributions = tally.contributions,
    )
}

/**
 * By family, then by name, so "Onions" and "Red onions" stay together in English where
 * plain alphabetical order would put them far apart.
 */
private fun byFamilyThenName(language: String): Comparator<ShoppingItem> {
    fun family(item: ShoppingItem): String =
        IngredientCatalogue.find(item.key)
            ?.let { IngredientCatalogue.rootOf(it).name(language) }
            ?: item.name
    return compareBy<ShoppingItem>({ family(it).lowercase() }, { it.name.lowercase() })
}

private fun eighths(value: Double): Double = (value * 8).roundToLong() / 8.0

private fun round1(value: Double): String = ((value * 10).roundToLong() / 10.0).toString()

private fun decimal(value: Double, french: Boolean): String {
    val text = ((value * 100).roundToLong() / 100.0).toString().trimEnd('0').trimEnd('.')
    return if (french) text.replace('.', ',') else text
}

private fun grams(value: Double, french: Boolean): String = when {
    value >= 1000 -> "${decimal(value / 1000, french)} kg"
    value >= 100 -> "${(value / 5).roundToLong() * 5} g"
    else -> "${value.roundToLong().coerceAtLeast(1)} g"
}

/** Spoons stay spoons while they are small enough to measure that way. */
private fun millilitres(value: Double, spoonsOnly: Boolean, french: Boolean): String = when {
    spoonsOnly && value < 14.7 ->
        "${formatAmount(eighths(value / 4.92892), french)} ${if (french) "c. à c." else "tsp"}"
    spoonsOnly && value < 45 ->
        "${formatAmount(eighths(value / 14.7868), french)} ${if (french) "c. à s." else "tbsp"}"
    value >= 1000 -> "${decimal(value / 1000, french)} l"
    value >= 100 -> "${(value / 5).roundToLong() * 5} ml"
    else -> "${value.roundToLong().coerceAtLeast(1)} ml"
}

private val COUNT_WORDS = mapOf(
    "clove" to ("clove" to "gousse"), "can" to ("can" to "boîte"),
    "stick" to ("stick" to "bâton"), "sprig" to ("sprig" to "brin"),
    "bunch" to ("bunch" to "botte"), "head" to ("head" to "tête"),
    "slice" to ("slice" to "tranche"), "piece" to ("piece" to "morceau"),
    "stalk" to ("stalk" to "branche"), "ball" to ("ball" to "boule"),
    "packet" to ("packet" to "sachet"),
)

private fun countLabel(unit: String, amount: Double, french: Boolean): String {
    val (english, frenchWord) = COUNT_WORDS[unit] ?: (unit to unit)
    val word = if (french) frenchWord else english
    if (amount <= 1.0) return word
    return when {
        french && word.endsWith("eau") -> word + "x"
        !french && word.endsWith("ch") -> word + "es"
        else -> word + "s"
    }
}

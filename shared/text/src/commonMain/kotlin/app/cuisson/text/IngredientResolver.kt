package app.cuisson.text

/**
 * Which ingredient a line of a recipe is asking for.
 *
 * [ingredient] is null for a learned one: a thing Cuisson had never heard of, which
 * becomes its own ingredient rather than being merged into the nearest guess. The worst
 * case of that is "kosher salt" sitting next to "salt" on a list. The worst case of
 * guessing is a line that silently swallows something you needed.
 */
data class Resolution(
    val id: String,
    val ingredient: CanonicalIngredient?,
    /** What a learned ingredient is called on a list. Catalogue ones use their own names. */
    val name: String,
    val aisle: Aisle,
    /** The key this was matched on, which is what a correction is remembered against. */
    val key: String,
) {
    val learned: Boolean get() = ingredient == null
    val shoppable: Boolean get() = ingredient?.shoppable ?: true
}

const val LEARNED_PREFIX = "learned:"

/**
 * Finds the ingredient behind a parsed item name, such as "finely chopped fresh parsley"
 * or "c. a s. d'huile d'arachide".
 *
 * Tries the text as written first and only then starts removing words, so a product whose
 * name contains a preparation word, "chopped tomatoes" or "boeuf hache", is found before
 * the word is stripped and it turns into plain tomatoes or plain beef.
 *
 * [userAliases] are corrections the user made, and they are consulted before anything
 * the app ships: a correction is the user telling us we were wrong.
 */
fun resolveIngredient(
    item: String,
    userAliases: Map<String, String> = emptyMap(),
): Resolution {
    val alternatives = splitAlternatives(cutTail(tokenise(item)))
    val candidates = mutableListOf<List<Token>>()

    alternatives.forEachIndexed { index, alternative ->
        val variants = variantsOf(alternative)
        candidates += variants
        // "brown or green lentils": a one-word first alternative shares the noun of the
        // one after it.
        val shortest = variants.last()
        val next = alternatives.getOrNull(index + 1)?.let { variantsOf(it).last() }
        if (shortest.size == 1 && next != null && next.isNotEmpty()) {
            candidates += listOf(shortest + next.last())
        }
    }

    val seen = mutableSetOf<String>()
    for (candidate in candidates) {
        val key = ingredientKey(candidate.joinToString(" ") { it.text })
        if (key.isEmpty() || !seen.add(key)) continue
        userAliases[key]?.let { return resolved(it, key) }
        IngredientCatalogue.index[key]?.let { return resolved(it, key) }
    }

    return learnedFrom(item, alternatives)
}

private fun resolved(id: String, key: String): Resolution {
    val ingredient = IngredientCatalogue.find(id)
    if (ingredient != null) {
        return Resolution(id, ingredient, ingredient.english, IngredientCatalogue.aisleOf(ingredient), key)
    }
    // A correction pointing at another learned ingredient.
    val name = id.removePrefix(LEARNED_PREFIX)
    return Resolution(id, null, name, guessAisle(name), key)
}

private fun learnedFrom(item: String, alternatives: List<List<Token>>): Resolution {
    val tokens = alternatives
        .map { variantsOf(it) }
        .firstNotNullOfOrNull { variants -> variants.lastOrNull { it.isNotEmpty() } }
        ?: tokenise(item)
    val name = tokens.joinToString(" ") { it.text }.trim(' ', '\'', '.', ',').ifBlank { item.trim() }
    val key = ingredientKey(name).ifEmpty { ingredientKey(item) }
    return Resolution(LEARNED_PREFIX + key, null, name, guessAisle(key), key)
}

/**
 * The folded, singular form two written names have to share to be the same ingredient.
 *
 * "Gousses d'ail" and "gousse d'ail" meet here, and so do "œuf" and "oeufs". Elisions and
 * the little connecting words go, because "jus de citron" and "jus d'un citron" are the
 * same thing written two ways.
 */
internal fun ingredientKey(text: String): String =
    fold(text)
        .replace('\'', ' ')
        .replace(Regex("""[^a-z0-9 ]"""), " ")
        .split(' ')
        .filter { it.isNotBlank() && it !in KEY_DROPS }
        .joinToString(" ") { singular(it) }

private val KEY_DROPS = setOf("de", "du", "of", "the", "d", "l", "un", "une")

private fun singular(word: String): String = when {
    word.length <= 3 -> word
    word.endsWith("ies") -> word.dropLast(3) + "y"
    word.endsWith("oes") -> word.dropLast(2)
    word.endsWith("sses") || word.endsWith("ches") || word.endsWith("shes") ||
        word.endsWith("xes") -> word.dropLast(2)
    word.endsWith("eaux") -> word.dropLast(1)
    word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") -> word.dropLast(1)
    else -> word
}

private data class Token(val text: String, val folded: String)

private fun tokenise(text: String): List<Token> =
    text.lowercase()
        .replace('’', '\'')
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""\(.*$"""), " ")
        .replace("-", " ")
        .replace(",", " , ")
        .replace("/", " / ")
        .replace("&", " & ")
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }
        .map { Token(it, fold(it)) }

/** Where the name stops and the instructions start: "cut into cubes", "to taste". */
private val TAIL_STARTS = listOf(
    listOf("cut"), listOf("from"), listOf("optional"), listOf("facultatif"),
    listOf("for", "serving"), listOf("to", "serve"), listOf("to", "taste"),
    listOf("more", "to", "taste"), listOf("for", "garnish"), listOf("to", "garnish"),
    listOf("if", "desired"), listOf("pour", "servir"), listOf("au", "gout"),
    listOf("selon", "gout"), listOf("si", "desire"),
)

private fun cutTail(tokens: List<Token>): List<Token> {
    for (i in tokens.indices) {
        val starts = TAIL_STARTS.any { phrase ->
            phrase.indices.all { j -> tokens.getOrNull(i + j)?.folded?.trim('.', ',') == phrase[j] }
        }
        if (starts && i > 0) return tokens.take(i)
    }
    return tokens
}

private val SEPARATORS = setOf("or", "ou", "&", "and", "et", "/", ",")

private fun splitAlternatives(tokens: List<Token>): List<List<Token>> {
    val groups = mutableListOf<MutableList<Token>>(mutableListOf())
    tokens.forEach { token ->
        if (token.folded in SEPARATORS) groups.add(mutableListOf()) else groups.last().add(token)
    }
    return groups.filter { it.isNotEmpty() }
}

/**
 * The same alternative with progressively more taken away: as written, without a leading
 * amount or container, without preparation words, and without size, quality and brand.
 */
private fun variantsOf(alternative: List<Token>): List<List<Token>> {
    val asWritten = alternative
    val lead = leadStripped(alternative)
    val unprepared = lead.filterNot { isPreparation(it.folded) || isNumberLike(it.folded) }
    val plain = withoutBrands(unprepared).filterNot { it.folded in QUALITY_WORDS }
    return listOf(asWritten, lead, unprepared, plain)
}

private val LEAD_JUNK = setOf(
    "a", "an", "the", "of", "de", "du", "des", "d", "l", "le", "la", "les", "un", "une",
    "plus", "about", "environ", "some", "quelques", "x", "c", "s", "grosse", "gros",
    "petite", "petit", "large", "small", "medium", "big", "bouteille", "conserve", "pot",
    "sheet", "sheets", "knob", "splash", "dash", "trait",
)

private fun leadStripped(tokens: List<Token>): List<Token> {
    var list = tokens
    while (list.size > 1) {
        val word = list.first().folded.trim('.', '\'')
        val junk = word.isEmpty() || isNumberLike(list.first().folded) ||
            word in LEAD_JUNK || word in UNIT_WORDS
        if (!junk) break
        list = list.drop(1)
    }
    return list
}

private fun isNumberLike(folded: String): Boolean =
    folded.any { it.isDigit() } || folded.all { it in "½¼¾⅓⅔⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞/.,%" }

private val PREPARATION_WORDS = setOf(
    "chopped", "minced", "sliced", "grated", "peeled", "deseeded", "seeded", "crushed",
    "melted", "softened", "beaten", "cubed", "halved", "quartered", "trimmed", "rinsed",
    "drained", "shredded", "torn", "smashed", "pressed", "divided", "packed", "sifted",
    "finely", "roughly", "thinly", "coarsely", "freshly", "lightly", "well", "room",
    "temperature", "cooled", "slightly", "cooked", "uncooked", "raw", "optional",
    "facultatif",
)

/** French past participles, matched by stem so gender and number do not matter. */
private val PREPARATION_STEMS = listOf(
    "emince", "hache", "tranche", "coupe", "pele", "rape", "fondu", "ecrase", "epluche",
    "cisele",
)

private fun isPreparation(folded: String): Boolean {
    val word = folded.trim('.', ',')
    return word in PREPARATION_WORDS || PREPARATION_STEMS.any { word.startsWith(it) }
}

private val QUALITY_WORDS = setOf(
    "fresh", "frais", "fraiche", "fraiches", "large", "small", "medium", "big", "lean",
    "whole", "boneless", "skinless", "unsalted", "salted", "low", "sodium", "reduced", "fat",
    "free", "organic", "bio", "extra", "virgin", "vierge", "pure", "unbleached", "bleached",
    "plain", "good", "quality", "homemade", "maison", "commerce", "store", "bought", "fire",
    "roasted", "demi", "half", "grosse", "gros", "petit", "petite", "moyen", "moyenne",
    "heaping", "level", "ripe", "mur", "mure", "firm", "un", "une", "du",
)

private val BRANDS = listOf(
    listOf("king", "arthur"), listOf("diamond", "crystal"), listOf("morton"),
    listOf("maille"), listOf("president"), listOf("barilla"),
)

private fun withoutBrands(tokens: List<Token>): List<Token> {
    val result = tokens.toMutableList()
    BRANDS.forEach { brand ->
        var i = 0
        while (i <= result.size - brand.size) {
            if (brand.indices.all { result[i + it].folded == brand[it] }) {
                repeat(brand.size) { result.removeAt(i) }
            } else {
                i++
            }
        }
    }
    return result
}

/**
 * A best guess at the aisle for an ingredient nobody has filed.
 *
 * Checked in this order on purpose: "chicken tikka masala paste" is in the jar aisle, not
 * the meat counter, and "chicken broth" is with the stock cubes.
 */
private val AISLE_HINTS: List<Pair<Aisle, Set<String>>> = listOf(
    Aisle.FROZEN to setOf("frozen", "surgele", "surgelee"),
    Aisle.DRINKS to setOf(
        "wine", "vin", "beer", "biere", "brandy", "cognac", "rum", "rhum", "whisky",
        "whiskey", "vodka", "cider", "cidre", "liqueur",
    ),
    Aisle.TINS_JARS to setOf(
        "can", "canned", "tin", "tinned", "conserve", "sauce", "paste", "puree", "chutney",
        "pickled", "jar", "bocal", "concentre", "coulis", "pesto",
    ),
    Aisle.SPICES to setOf(
        "powder", "poudre", "ground", "moulu", "moulue", "seed", "graine", "spice", "epice",
        "seasoning", "masala", "salt", "sel", "peppercorn",
    ),
    Aisle.DRY_GOODS to setOf(
        "flour", "farine", "sugar", "sucre", "pasta", "pate", "rice", "riz", "noodle",
        "nouille", "oil", "huile", "vinegar", "vinaigre", "stock", "bouillon", "broth",
        "lentil", "lentille", "chocolate", "chocolat", "nut", "almond", "amande", "oat",
        "syrup", "sirop", "extract", "extrait", "chip",
    ),
    Aisle.DAIRY to setOf(
        "cheese", "fromage", "cream", "creme", "yogurt", "yoghurt", "yaourt", "butter",
        "beurre", "milk", "lait", "egg", "oeuf",
    ),
    Aisle.MEAT_FISH to setOf(
        "chicken", "poulet", "beef", "boeuf", "pork", "porc", "lamb", "agneau", "veal", "veau",
        "sausage", "saucisse", "fish", "poisson", "salmon", "saumon", "shrimp", "prawn",
        "crevette", "bacon", "ham", "jambon", "duck", "canard", "turkey", "dinde", "steak",
        "mince",
    ),
    Aisle.BAKERY to setOf("bread", "pain", "bun", "brioche", "tortilla", "baguette"),
    Aisle.PRODUCE to setOf(
        "leaf", "leave", "feuille", "herb", "herbe", "lettuce", "salad", "fruit", "berry",
        "squash", "root",
    ),
)

internal fun guessAisle(key: String): Aisle {
    val words = ingredientKey(key).split(' ')
    return AISLE_HINTS.firstOrNull { (_, hints) -> words.any { it in hints } }?.first
        ?: Aisle.OTHER
}

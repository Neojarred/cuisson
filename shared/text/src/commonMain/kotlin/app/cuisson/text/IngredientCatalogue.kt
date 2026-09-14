package app.cuisson.text

/**
 * The section of a French supermarket something is found in, in the order you walk them.
 *
 * Aisles follow the shop, not the botany. Tomato purée lives with the tins even though it
 * is made from tomatoes, because that is where it is on the shelf.
 */
enum class Aisle(private val english: String, private val french: String) {
    PRODUCE("Fruit and vegetables", "Fruits et légumes"),
    MEAT_FISH("Meat and fish", "Viande et poisson"),
    DAIRY("Dairy and eggs", "Crèmerie"),
    DRY_GOODS("Dry goods", "Épicerie"),
    SPICES("Spices and condiments", "Épices et condiments"),
    TINS_JARS("Tins and jars", "Conserves"),
    FROZEN("Frozen", "Surgelés"),
    BAKERY("Bakery", "Boulangerie"),
    DRINKS("Drinks", "Boissons"),
    OTHER("Other", "Autre");

    fun label(language: String): String = if (language == "fr") french else english
}

/** Two forms of the same thing are two different things to buy. */
enum class Form { ANY, FRESH, DRIED, FROZEN, CANNED, GROUND }

/** Whether a shop sells it by weight or by volume, for the few with a vetted density. */
enum class SoldBy { MASS, VOLUME }

/**
 * One thing that many written forms refer to.
 *
 * [family] points at the more general ingredient this one is a kind of: a red onion is a
 * kind of onion. It means "a kind of" and nothing looser, so tomato purée is not a kind of
 * tomato and has no family here. Ingredients inherit their family's aisle unless they name
 * their own.
 *
 * [gramsPerMl] exists only where the figure is well established, flour, sugar, butter,
 * milk and their relatives, because a guessed density is how a baking recipe gets ruined.
 * [countAs] is the unit a bare count means: "3 garlic" means three cloves.
 */
data class CanonicalIngredient(
    val id: String,
    val english: String,
    val french: String,
    val aisle: Aisle? = null,
    val family: String? = null,
    val form: Form = Form.ANY,
    val gramsPerMl: Double? = null,
    val soldBy: SoldBy? = null,
    val countAs: String? = null,
    /**
     * Grams in one of a counting unit, only where that is standard: an American stick of
     * butter is 113 g by definition, not by guess.
     */
    val gramsPer: Map<String, Double> = emptyMap(),
    /** Water is in every recipe and on no shopping list. */
    val shoppable: Boolean = true,
    val aliases: List<String> = emptyList(),
) {
    fun name(language: String): String = if (language == "fr") french else english
}

/**
 * The ingredients Cuisson ships knowing about.
 *
 * Deliberately a list of records rather than a class per ingredient. Ingredients Cuisson
 * has never heard of are created on the phone as the user imports recipes, and a phone can
 * add a row but cannot write new code, so the shipped ones have to be the same kind of
 * thing. See ADR-0010.
 *
 * Built from the ingredients that actually turn up in a real library of recipes, English
 * and French, rather than from a food encyclopedia. Coverage of everything is not the aim;
 * getting the things in every recipe right on the first day is.
 */
object IngredientCatalogue {

    val all: List<CanonicalIngredient> = buildList { entries() }

    private val byId: Map<String, CanonicalIngredient> = all.associateBy { it.id }

    fun find(id: String): CanonicalIngredient? = byId[id]

    /** The aisle, taken from the nearest ancestor that names one. */
    fun aisleOf(ingredient: CanonicalIngredient): Aisle {
        var current: CanonicalIngredient? = ingredient
        var guard = 0
        while (current != null && guard++ < 10) {
            current.aisle?.let { return it }
            current = current.family?.let(byId::get)
        }
        return Aisle.OTHER
    }

    /** The most general ancestor, which is what keeps varieties together on a list. */
    fun rootOf(ingredient: CanonicalIngredient): CanonicalIngredient {
        var current = ingredient
        var guard = 0
        while (guard++ < 10) {
            current = current.family?.let(byId::get) ?: return current
        }
        return current
    }

    /** Every written form, folded into a key, pointing at the ingredient it names. */
    internal val index: Map<String, String> = buildMap {
        all.forEach { ingredient ->
            (listOf(ingredient.english, ingredient.french) + ingredient.aliases).forEach {
                val key = ingredientKey(it)
                if (key.isNotEmpty()) putIfAbsent(key, ingredient.id)
            }
        }
    }

    /** Keys claimed by two different ingredients. A test keeps this empty. */
    internal fun collisions(): List<Triple<String, String, String>> {
        val seen = mutableMapOf<String, String>()
        val clashes = mutableListOf<Triple<String, String, String>>()
        all.forEach { ingredient ->
            (listOf(ingredient.english, ingredient.french) + ingredient.aliases).forEach {
                val key = ingredientKey(it)
                val owner = seen[key]
                if (owner != null && owner != ingredient.id) {
                    clashes += Triple(key, owner, ingredient.id)
                } else {
                    seen[key] = ingredient.id
                }
            }
        }
        return clashes
    }
}

private fun MutableList<CanonicalIngredient>.i(
    id: String,
    en: String,
    fr: String,
    aisle: Aisle? = null,
    family: String? = null,
    form: Form = Form.ANY,
    density: Double? = null,
    soldBy: SoldBy? = null,
    countAs: String? = null,
    per: Map<String, Double> = emptyMap(),
    shoppable: Boolean = true,
    also: String = "",
) {
    add(
        CanonicalIngredient(
            id = id,
            english = en,
            french = fr,
            aisle = aisle,
            family = family,
            form = form,
            gramsPerMl = density,
            soldBy = soldBy,
            countAs = countAs,
            gramsPer = per,
            shoppable = shoppable,
            aliases = also.split('|').map { it.trim() }.filter { it.isNotEmpty() },
        )
    )
}

private fun MutableList<CanonicalIngredient>.entries() {
    val produce = Aisle.PRODUCE
    val meat = Aisle.MEAT_FISH
    val dairy = Aisle.DAIRY
    val dry = Aisle.DRY_GOODS
    val spices = Aisle.SPICES
    val tins = Aisle.TINS_JARS
    val frozen = Aisle.FROZEN
    val bakery = Aisle.BAKERY
    val drinks = Aisle.DRINKS

    // Fruit and vegetables
    i("onion", "onion", "oignon", produce,
        also = "onions|oignons|yellow onion|white onion|brown onion|oignon jaune|oignon blanc")
    i("red-onion", "red onion", "oignon rouge", family = "onion", also = "red onions|oignons rouges")
    i("shallot", "shallot", "échalote", family = "onion", also = "shallots|echalotes")
    i("spring-onion", "spring onion", "oignon nouveau", family = "onion",
        also = "spring onions|scallion|scallions|green onion|green onions|cebette|oignon vert")
    i("garlic", "garlic", "ail", produce, countAs = "clove",
        also = "garlic clove|garlic cloves|clove of garlic|cloves of garlic|gousse d'ail|" +
            "gousses d'ail|head of garlic")
    i("leek", "leek", "poireau", produce, also = "leeks|poireaux")
    i("carrot", "carrot", "carotte", produce, also = "carrots|carottes")
    i("celery", "celery", "céleri", produce, countAs = "stalk",
        also = "celery stalk|celery stalks|celery rib|celery ribs|branche de celeri|" +
            "branches de celeri")
    i("celeriac", "celeriac", "céleri-rave", produce)
    i("parsley-root", "parsley root", "racine de persil", produce)
    i("potato", "potato", "pomme de terre", produce, also = "potatoes|pommes de terre")
    i("new-potato", "new potato", "pomme de terre nouvelle", family = "potato",
        also = "new potatoes|baby potatoes|grenaille|pommes de terre grenaille")
    i("sweet-potato", "sweet potato", "patate douce", family = "potato",
        also = "sweet potatoes|patates douces")
    i("tomato", "tomato", "tomate", produce, form = Form.FRESH, also = "tomatoes|tomates")
    i("cherry-tomato", "cherry tomato", "tomate cerise", family = "tomato",
        also = "cherry tomatoes|tomates cerises")
    i("bell-pepper", "bell pepper", "poivron", produce,
        also = "bell peppers|poivrons|sweet pepper|capsicum")
    i("red-pepper", "red pepper", "poivron rouge", family = "bell-pepper",
        also = "red bell pepper|red peppers|red bell peppers|poivrons rouges")
    i("green-pepper", "green pepper", "poivron vert", family = "bell-pepper",
        also = "green bell pepper|green peppers")
    i("yellow-pepper", "yellow pepper", "poivron jaune", family = "bell-pepper")
    i("chilli", "chilli", "piment", produce, form = Form.FRESH,
        also = "chili|chile|chilli pepper|chili pepper|chillies|chilies|red chilli|" +
            "green chilli|piment frais|piments")
    i("jalapeno", "jalapeño", "jalapeño", family = "chilli",
        also = "jalapeno pepper|jalapeno peppers|jalapenos")
    i("serrano", "serrano pepper", "piment serrano", family = "chilli",
        also = "serrano|serrano peppers")
    i("dried-chilli", "dried chilli", "piment séché", spices, family = "chilli", form = Form.DRIED,
        also = "dried chili|dried chilies|dried chillies|dried chile|piment sec|piments secs|" +
            "piments seches")
    i("chilli-flakes", "chilli flakes", "flocons de piment", spices, family = "chilli",
        form = Form.DRIED,
        also = "chili flakes|red pepper flakes|crushed red pepper|crushed red pepper flakes|" +
            "flocons de poivre de cayenne|piment en flocons")
    i("cayenne", "cayenne pepper", "piment de Cayenne", spices, family = "chilli",
        form = Form.GROUND, also = "cayenne|poivre de cayenne")
    i("chilli-powder", "chilli powder", "piment en poudre", spices, family = "chilli",
        form = Form.GROUND, also = "chili powder|chile powder|piment moulu")
    i("chipotle-adobo", "chipotle in adobo", "piments chipotle en sauce adobo", tins,
        family = "chilli",
        also = "chipotle|chipotles|chipotle pepper|chipotle peppers|chipotles in adobo|" +
            "chipotle peppers in adobo|adobo sauce|sauce adobo")
    i("courgette", "courgette", "courgette", produce, also = "zucchini|courgettes")
    i("aubergine", "aubergine", "aubergine", produce, also = "eggplant|aubergines")
    i("mushroom", "mushroom", "champignon", produce,
        also = "mushrooms|button mushrooms|champignons|champignons de paris")
    i("spinach", "spinach", "épinards", produce, also = "baby spinach|epinard")
    i("cabbage", "cabbage", "chou", produce, also = "white cabbage|savoy cabbage")
    i("kale", "kale", "chou kale", family = "cabbage", also = "curly kale|cavolo nero")
    i("collard-greens", "collard greens", "chou cavalier", family = "cabbage",
        also = "collards|collard green")
    i("cauliflower", "cauliflower", "chou-fleur", family = "cabbage")
    i("broccoli", "broccoli", "brocoli", produce)
    i("lettuce", "lettuce", "laitue", produce, also = "salade|romaine|iceberg lettuce")
    i("cucumber", "cucumber", "concombre", produce)
    i("avocado", "avocado", "avocat", produce, also = "avocados|avocats")
    i("green-beans", "green beans", "haricots verts", produce)
    i("peas", "peas", "petits pois", produce, also = "garden peas")
    i("pumpkin", "squash", "courge", produce, also = "butternut squash|butternut|potiron|pumpkin")
    i("beetroot", "beetroot", "betterave", produce, also = "beet|beets")
    i("radish", "radish", "radis", produce, also = "radishes")
    i("fennel", "fennel", "fenouil", produce, form = Form.FRESH, also = "fennel bulb")
    i("lemon", "lemon", "citron", produce,
        also = "lemons|citrons|citron jaune|lemon juice|lemon zest|juice of a lemon|" +
            "jus de citron|zeste de citron")
    i("lime", "lime", "citron vert", produce,
        also = "limes|citrons verts|lime juice|lime zest|jus de citron vert|zeste de citron vert")
    i("orange", "orange", "orange", produce,
        also = "oranges|orange juice|orange zest|jus d'orange|zeste d'orange")
    i("apple", "apple", "pomme", produce, also = "apples|pommes")
    i("pear", "pear", "poire", produce, also = "pears|poires")
    i("banana", "banana", "banane", produce, also = "bananas|bananes")
    i("strawberry", "strawberry", "fraise", produce, also = "strawberries|fraises")
    i("raspberry", "raspberry", "framboise", produce, also = "raspberries|framboises")
    i("blueberry", "blueberry", "myrtille", produce, also = "blueberries|myrtilles")
    i("mango", "mango", "mangue", produce, also = "mangoes|mangues")
    i("lemongrass", "lemongrass", "citronnelle", produce, countAs = "stalk",
        also = "lemongrass stalk|lemongrass stalks|lemongrass stick|lemon grass|" +
            "tige de citronnelle")
    i("galangal", "galangal", "galanga", produce, form = Form.FRESH, also = "galangal root")
    i("ginger", "ginger", "gingembre", produce, form = Form.FRESH,
        also = "ginger root|root ginger|gingembre frais")
    i("ground-ginger", "ground ginger", "gingembre en poudre", spices, family = "ginger",
        form = Form.GROUND, also = "gingembre moulu")
    i("kaffir-lime-leaf", "kaffir lime leaf", "feuille de combava", produce,
        also = "kaffir lime leaves|lime leaf|lime leaves|makrut lime leaves|feuilles de combava")
    i("herb", "fresh herbs", "herbes fraîches", produce, also = "herbs|herbes")
    i("parsley", "parsley", "persil", family = "herb",
        also = "flat-leaf parsley|flat leaf parsley|italian parsley|curly parsley|persil plat|" +
            "parsley leaves")
    i("coriander", "coriander", "coriandre", family = "herb",
        also = "cilantro|coriander leaves|cilantro leaves|coriandre fraiche")
    i("basil", "basil", "basilic", family = "herb", also = "basil leaves")
    i("chives", "chives", "ciboulette", family = "herb")
    i("rosemary", "rosemary", "romarin", family = "herb", also = "rosemary sprigs")
    i("thyme", "thyme", "thym", family = "herb", form = Form.FRESH,
        also = "thyme sprigs|thym frais")
    i("mint", "mint", "menthe", family = "herb", also = "mint leaves")
    i("dill", "dill", "aneth", family = "herb")
    i("tarragon", "tarragon", "estragon", family = "herb")

    // Meat and fish
    i("chicken", "chicken", "poulet", meat, also = "whole chicken|poulet entier")
    i("chicken-breast", "chicken breast", "blanc de poulet", family = "chicken",
        also = "chicken breasts|chicken breast fillets|filet de poulet|filets de poulet|" +
            "blancs de poulet|escalope de poulet")
    i("chicken-thigh", "chicken thigh", "cuisse de poulet", family = "chicken",
        also = "chicken thighs|haut de cuisse de poulet")
    i("beef", "beef", "bœuf", meat,
        also = "stewing beef|braising beef|beef chuck|boeuf a braiser")
    i("beef-mince", "beef mince", "bœuf haché", family = "beef",
        also = "minced beef|ground beef|viande hachee|steak hache")
    i("chuck-steak", "chuck steak", "paleron", family = "beef", also = "paleron de boeuf")
    i("beef-cheek", "beef cheek", "joue de bœuf", family = "beef",
        also = "beef cheeks|joue de boeuf jumeau|joues de boeuf")
    i("beef-fillet", "beef fillet", "filet de bœuf", family = "beef",
        also = "fillet of beef|beef tenderloin")
    i("pork", "pork", "porc", meat, also = "pork shoulder|echine de porc")
    i("pork-mince", "pork mince", "porc haché", family = "pork",
        also = "minced pork|ground pork|chair a saucisse")
    i("lardons", "lardons", "lardons", family = "pork",
        also = "poitrine fumee|poitrine fumee en lardons|bacon lardons|smoked lardons|" +
            "lardons fumes")
    i("bacon", "bacon", "bacon", family = "pork", also = "streaky bacon|bacon rashers")
    i("ham", "ham", "jambon", family = "pork", also = "jambon blanc|cooked ham")
    i("sausage", "sausage", "saucisse", family = "pork", also = "sausages|saucisses")
    i("lamb", "lamb", "agneau", meat, also = "lamb shoulder|gigot d'agneau")
    i("suet", "suet", "graisse de rognon", meat, also = "beef suet|shredded suet")
    i("salmon", "salmon", "saumon", meat, also = "salmon fillet|salmon fillets|pave de saumon")
    i("cod", "cod", "cabillaud", meat, also = "cod fillet|cod fillets|dos de cabillaud")
    i("prawns", "prawns", "crevettes", meat, also = "prawn|shrimp|shrimps|crevette")

    // Dairy and eggs. Ready-made pastry is here too, because in France it is in the
    // chilled aisle beside the butter.
    i("butter", "butter", "beurre", dairy, density = 0.96, soldBy = SoldBy.MASS,
        per = mapOf("stick" to 113.4),
        also = "unsalted butter|salted butter|beurre doux|beurre demi-sel|beurre mou")
    i("milk", "milk", "lait", dairy, density = 1.03, soldBy = SoldBy.VOLUME,
        also = "whole milk|semi-skimmed milk|skimmed milk|lait entier|lait demi-ecreme|" +
            "full-fat milk")
    i("cream", "cream", "crème liquide", dairy, density = 1.0, soldBy = SoldBy.VOLUME,
        also = "double cream|heavy cream|whipping cream|heavy whipping cream|single cream|" +
            "creme entiere|creme fleurette|creme liquide entiere")
    i("creme-fraiche", "crème fraîche", "crème fraîche", family = "cream", density = 1.0,
        soldBy = SoldBy.VOLUME, also = "creme fraiche epaisse")
    i("light-creme-fraiche", "light crème fraîche", "crème fraîche allégée",
        family = "creme-fraiche", density = 1.0, soldBy = SoldBy.VOLUME,
        also = "half-fat creme fraiche")
    i("sour-cream", "sour cream", "crème aigre", family = "cream", also = "soured cream")
    i("yogurt", "natural yogurt", "yaourt nature", dairy,
        also = "plain yogurt|yogurt|yoghurt|natural yoghurt|yaourt")
    i("greek-yogurt", "Greek yogurt", "yaourt grec", family = "yogurt", also = "greek yoghurt")
    i("egg", "egg", "œuf", dairy,
        also = "eggs|oeuf|oeufs|egg yolk|egg yolks|egg white|egg whites|jaune d'oeuf|" +
            "jaunes d'oeufs|blanc d'oeuf|blancs d'oeufs")
    i("cheese", "cheese", "fromage", dairy, also = "fromage rape|grated cheese")
    i("parmesan", "parmesan", "parmesan", family = "cheese",
        also = "parmesan cheese|parmigiano reggiano|parmigiano|grana padano")
    i("mozzarella", "mozzarella", "mozzarella", family = "cheese",
        also = "mozzarella cheese|ball of mozzarella|boule de mozzarella")
    i("caciocavallo", "caciocavallo", "caciocavallo", family = "cheese")
    i("cheddar", "cheddar", "cheddar", family = "cheese", also = "cheddar cheese|mature cheddar")
    i("gruyere", "gruyère", "gruyère", family = "cheese")
    i("emmental", "emmental", "emmental", family = "cheese")
    i("comte", "comté", "comté", family = "cheese")
    i("feta", "feta", "feta", family = "cheese")
    i("goats-cheese", "goat's cheese", "fromage de chèvre", family = "cheese",
        also = "goat cheese|chevre")
    i("ricotta", "ricotta", "ricotta", family = "cheese")
    i("shortcrust-pastry", "shortcrust pastry", "pâte brisée", dairy,
        also = "pate brisee|pates brisees|pie crust|pie dough|pate a tarte|tart pastry")
    i("puff-pastry", "puff pastry", "pâte feuilletée", dairy,
        also = "pate feuilletee|puff pastry sheet|ready-rolled puff pastry")

    // Dry goods
    i("flour", "flour", "farine", dry, density = 0.53, soldBy = SoldBy.MASS,
        also = "all-purpose flour|plain flour|white flour|farine de ble|farine t45|farine t55")
    i("self-raising-flour", "self-raising flour", "farine à gâteaux", family = "flour",
        density = 0.53, soldBy = SoldBy.MASS, also = "self-rising flour")
    i("cornflour", "cornflour", "fécule de maïs", family = "flour", density = 0.54,
        soldBy = SoldBy.MASS, also = "cornstarch|corn starch|maizena|fecule")
    i("sugar", "sugar", "sucre", dry, density = 0.85, soldBy = SoldBy.MASS,
        also = "granulated sugar|caster sugar|white sugar|sucre en poudre|sucre semoule|" +
            "superfine sugar")
    i("brown-sugar", "brown sugar", "cassonade", family = "sugar", density = 0.90,
        soldBy = SoldBy.MASS, also = "sucre roux|sucre brun|demerara sugar|soft brown sugar")
    i("light-brown-sugar", "light brown sugar", "vergeoise blonde", family = "brown-sugar",
        density = 0.90, soldBy = SoldBy.MASS, also = "light soft brown sugar")
    i("dark-brown-sugar", "dark brown sugar", "vergeoise brune", family = "brown-sugar",
        density = 0.90, soldBy = SoldBy.MASS, also = "dark soft brown sugar|muscovado")
    i("icing-sugar", "icing sugar", "sucre glace", family = "sugar", density = 0.51,
        soldBy = SoldBy.MASS, also = "powdered sugar|confectioners sugar")
    i("palm-sugar", "palm sugar", "sucre de palme", family = "sugar",
        also = "grated palm sugar|coconut sugar|jaggery")
    i("honey", "honey", "miel", dry, density = 1.42, soldBy = SoldBy.MASS)
    i("maple-syrup", "maple syrup", "sirop d'érable", dry)
    i("baking-soda", "baking soda", "bicarbonate de soude", dry,
        also = "bicarbonate of soda|bicarbonate alimentaire")
    i("baking-powder", "baking powder", "levure chimique", dry)
    i("yeast", "yeast", "levure boulangère", dry,
        also = "dried yeast|instant yeast|active dry yeast|levure de boulanger")
    i("vanilla-extract", "vanilla extract", "extrait de vanille", dry,
        also = "vanilla|vanilla essence|arome vanille")
    i("vanilla-pod", "vanilla pod", "gousse de vanille", dry, also = "vanilla bean|vanilla beans")
    i("dark-chocolate", "dark chocolate", "chocolat noir", dry,
        also = "bittersweet chocolate|chocolat noir a patisser|plain chocolate|" +
            "chocolat patissier|semisweet chocolate")
    i("chocolate-chips", "chocolate chips", "pépites de chocolat", family = "dark-chocolate",
        also = "semisweet chocolate chips|dark chocolate chips")
    i("cocoa", "cocoa powder", "cacao en poudre", dry, also = "cocoa|unsweetened cocoa powder")
    i("pasta", "pasta", "pâtes", dry)
    i("spaghetti", "spaghetti", "spaghetti", family = "pasta")
    i("penne", "penne", "penne", family = "pasta")
    i("fettuccine", "fettuccine", "fettuccine", family = "pasta", also = "fettucine")
    i("tagliatelle", "tagliatelle", "tagliatelles", family = "pasta")
    i("lasagne-sheets", "lasagne sheets", "feuilles de lasagne", family = "pasta",
        also = "lasagna noodles|lasagna sheets|lasagne|lasagna|plaques de lasagne")
    i("rice", "rice", "riz", dry, also = "white rice|long grain rice")
    i("basmati", "basmati rice", "riz basmati", family = "rice", also = "basmati")
    i("arborio", "risotto rice", "riz arborio", family = "rice", also = "arborio rice|carnaroli")
    i("lentils", "lentils", "lentilles", dry)
    i("green-lentils", "green lentils", "lentilles vertes", family = "lentils",
        also = "brown lentils|lentilles brunes|puy lentils|lentilles du puy")
    i("red-lentils", "red lentils", "lentilles corail", family = "lentils",
        also = "split red lentils")
    i("couscous", "couscous", "semoule", dry)
    i("oats", "oats", "flocons d'avoine", dry, also = "rolled oats|porridge oats")
    i("breadcrumbs", "breadcrumbs", "chapelure", dry, also = "panko|bread crumbs")
    i("olive-oil", "olive oil", "huile d'olive", dry, density = 0.91, soldBy = SoldBy.VOLUME,
        also = "extra virgin olive oil|extra-virgin olive oil|huile d'olive extra vierge|" +
            "huile d'olive vierge extra")
    i("vegetable-oil", "vegetable oil", "huile végétale", dry, density = 0.92,
        soldBy = SoldBy.VOLUME,
        also = "oil|neutral oil|sunflower oil|canola oil|rapeseed oil|huile de tournesol|" +
            "huile de colza|huile neutre|cooking oil|huile")
    i("peanut-oil", "peanut oil", "huile d'arachide", family = "vegetable-oil", density = 0.92,
        soldBy = SoldBy.VOLUME, also = "groundnut oil")
    i("sesame-oil", "sesame oil", "huile de sésame", dry, density = 0.92, soldBy = SoldBy.VOLUME)
    i("vinegar", "vinegar", "vinaigre", dry,
        also = "white vinegar|vinaigre blanc|white wine vinegar|vinaigre de vin blanc")
    i("red-wine-vinegar", "red wine vinegar", "vinaigre de vin rouge", family = "vinegar")
    i("balsamic", "balsamic vinegar", "vinaigre balsamique", family = "vinegar")
    i("cider-vinegar", "cider vinegar", "vinaigre de cidre", family = "vinegar",
        also = "apple cider vinegar")
    i("soy-sauce", "soy sauce", "sauce soja", dry, also = "soya sauce|tamari")
    i("fish-sauce", "fish sauce", "nuoc-mâm", dry, also = "nam pla")
    i("worcestershire", "Worcestershire sauce", "sauce Worcestershire", dry,
        also = "worcestershire")
    i("stock", "stock", "bouillon", dry, also = "broth")
    i("chicken-stock", "chicken stock", "bouillon de volaille", family = "stock",
        also = "chicken broth|bouillon de poulet|fond de volaille")
    i("beef-stock", "beef stock", "bouillon de bœuf", family = "stock",
        also = "beef broth|fond de boeuf")
    i("vegetable-stock", "vegetable stock", "bouillon de légumes", family = "stock",
        also = "vegetable broth")
    i("stock-cube", "stock cube", "cube de bouillon", family = "stock",
        also = "bouillon cube|bouillon cubes|stock cubes|bouillon instantane")
    i("desiccated-coconut", "desiccated coconut", "noix de coco râpée", dry,
        also = "shredded coconut|dried coconut")
    i("tamarind", "tamarind", "tamarin", dry,
        also = "tamarind paste|tamarind puree|tamarind pulp|pate de tamarin")
    i("almonds", "almonds", "amandes", dry, also = "whole almonds")
    i("blanched-almonds", "blanched almonds", "amandes émondées", family = "almonds")
    i("ground-almonds", "ground almonds", "poudre d'amande", family = "almonds",
        form = Form.GROUND, also = "almond flour|almond meal|amandes en poudre")
    i("walnuts", "walnuts", "noix", dry, also = "cerneaux de noix|walnut halves")
    i("pine-nuts", "pine nuts", "pignons de pin", dry, also = "pignons")
    i("hazelnuts", "hazelnuts", "noisettes", dry)
    i("peanuts", "peanuts", "cacahuètes", dry)
    i("nuts", "nuts", "fruits à coque", dry, also = "mixed nuts|chopped nuts")
    i("raisins", "raisins", "raisins secs", dry, also = "sultanas|golden raisins")
    i("currants", "currants", "raisins de Corinthe", family = "raisins",
        also = "dried currants|zante currants")
    i("dried-fruit", "dried fruit", "fruits secs", dry, also = "mixed dried fruit")
    i("peanut-butter", "peanut butter", "beurre de cacahuète", dry)
    i("jam", "jam", "confiture", dry)

    // Spices and condiments
    i("salt", "salt", "sel", spices,
        also = "sea salt|kosher salt|table salt|fine salt|sel fin|flaky salt|fleur de sel|" +
            "sel de mer|gros sel|coarse salt|sea salt flakes")
    i("black-pepper", "black pepper", "poivre noir", spices,
        also = "pepper|poivre|ground black pepper|freshly ground black pepper|poivre du moulin|" +
            "black peppercorns|peppercorns|poivre moulu|ground pepper")
    i("cumin", "ground cumin", "cumin moulu", spices, form = Form.GROUND,
        also = "cumin|cumin powder|cumin en poudre")
    i("cumin-seeds", "cumin seeds", "graines de cumin", family = "cumin", form = Form.ANY)
    i("ground-coriander", "ground coriander", "coriandre moulue", spices, family = "coriander",
        form = Form.GROUND, also = "coriander powder|coriandre en poudre")
    i("coriander-seeds", "coriander seeds", "graines de coriandre", spices, family = "coriander")
    i("curry-powder", "curry powder", "curry en poudre", spices, form = Form.GROUND,
        also = "curry|poudre de curry|madras curry powder")
    i("garam-masala", "garam masala", "garam masala", spices)
    i("paprika", "paprika", "paprika", spices, form = Form.GROUND, also = "sweet paprika")
    i("smoked-paprika", "smoked paprika", "paprika fumé", family = "paprika",
        also = "pimenton")
    i("turmeric", "turmeric", "curcuma", spices, form = Form.GROUND, also = "ground turmeric")
    i("cinnamon", "ground cinnamon", "cannelle moulue", spices, form = Form.GROUND,
        also = "cinnamon|cannelle|cannelle en poudre")
    i("cinnamon-stick", "cinnamon stick", "bâton de cannelle", family = "cinnamon",
        also = "cinnamon sticks|batons de cannelle")
    i("cloves", "cloves", "clous de girofle", spices,
        also = "whole cloves|clou de girofle|girofle")
    i("ground-cloves", "ground cloves", "girofle moulu", family = "cloves", form = Form.GROUND,
        also = "clove powder|girofle en poudre")
    i("star-anise", "star anise", "anis étoilé", spices, also = "badiane")
    i("cardamom", "cardamom", "cardamome", spices, also = "cardamom pods|green cardamom")
    i("ground-cardamom", "ground cardamom", "cardamome moulue", family = "cardamom",
        form = Form.GROUND, also = "cardamom powder|cardamon powder|cardamome en poudre")
    i("nutmeg", "nutmeg", "muscade", spices,
        also = "ground nutmeg|noix de muscade|grated nutmeg")
    i("mace", "mace", "macis", spices, also = "ground mace")
    i("allspice", "allspice", "piment de la Jamaïque", spices, also = "ground allspice")
    i("quatre-epices", "quatre-épices", "quatre-épices", spices, also = "mixed spice")
    i("fennel-seeds", "fennel seeds", "graines de fenouil", spices)
    i("mustard-seeds", "mustard seeds", "graines de moutarde", spices)
    i("sesame-seeds", "sesame seeds", "graines de sésame", spices)
    i("oregano", "dried oregano", "origan séché", spices, form = Form.DRIED,
        also = "oregano|origan")
    i("dried-thyme", "dried thyme", "thym séché", spices, family = "thyme", form = Form.DRIED)
    i("dried-herbs", "dried herbs", "herbes de Provence", spices, form = Form.DRIED,
        also = "mixed dried herbs|italian seasoning")
    i("bay-leaf", "bay leaf", "feuille de laurier", spices, form = Form.DRIED,
        also = "bay leaves|laurier|feuilles de laurier")
    i("pickling-spice", "pickling spice", "épices pour marinade", spices)
    i("saffron", "saffron", "safran", spices)
    i("mustard", "Dijon mustard", "moutarde de Dijon", spices,
        also = "dijon|moutarde|mustard|moutarde forte")
    i("wholegrain-mustard", "wholegrain mustard", "moutarde à l'ancienne", family = "mustard",
        also = "whole grain mustard|grainy mustard")

    // Tins and jars. Tomato purée and passata are not a kind of tomato.
    i("tomato-puree", "tomato purée", "concentré de tomates", tins,
        also = "tomato paste|double concentre de tomates|concentre de tomate")
    i("passata", "passata", "coulis de tomates", tins, also = "tomato passata|coulis de tomate")
    i("canned-tomatoes", "canned tomatoes", "tomates en conserve", tins, form = Form.CANNED,
        also = "tinned tomatoes")
    i("chopped-tomatoes", "chopped tomatoes", "tomates concassées", family = "canned-tomatoes",
        form = Form.CANNED,
        also = "diced tomatoes|canned diced tomatoes|diced fire-roasted tomatoes|" +
            "fire-roasted diced tomatoes|crushed tomatoes|tomates en des")
    i("plum-tomatoes", "whole plum tomatoes", "tomates pelées", family = "canned-tomatoes",
        form = Form.CANNED, also = "plum tomatoes|whole peeled tomatoes|tomates entieres pelees")
    i("coconut-milk", "coconut milk", "lait de coco", tins, also = "light coconut milk")
    i("coconut-cream", "coconut cream", "crème de coco", tins)
    i("curry-paste", "curry paste", "pâte de curry", tins,
        also = "red curry paste|green curry paste|thai curry paste")
    i("tikka-paste", "tikka masala paste", "pâte tikka masala", family = "curry-paste",
        also = "chicken tikka masala paste|tikka paste")
    i("mango-chutney", "mango chutney", "chutney de mangue", tins, also = "chutney")
    i("beans", "beans", "haricots", tins, form = Form.CANNED, also = "canned beans")
    i("kidney-beans", "kidney beans", "haricots rouges", family = "beans", form = Form.CANNED,
        also = "red kidney beans")
    i("pinto-beans", "pinto beans", "haricots pinto", family = "beans", form = Form.CANNED)
    i("black-beans", "black beans", "haricots noirs", family = "beans", form = Form.CANNED)
    i("chickpeas", "chickpeas", "pois chiches", family = "beans", form = Form.CANNED,
        also = "garbanzo beans")
    i("white-beans", "cannellini beans", "haricots blancs", family = "beans",
        form = Form.CANNED, also = "white beans|cannellini|butter beans")
    i("sweetcorn", "sweetcorn", "maïs", tins,
        also = "corn|corn kernels|sweet corn|mais doux")
    i("tuna", "tuna", "thon", tins, also = "canned tuna|tinned tuna|thon en boite")
    i("pickled-onions", "pickled onions", "oignons au vinaigre", tins,
        also = "oignons marines|pickled red onions")
    i("olives", "olives", "olives", tins,
        also = "black olives|green olives|olives noires|olives vertes")
    i("capers", "capers", "câpres", tins)
    i("mayonnaise", "mayonnaise", "mayonnaise", tins, also = "mayo")
    i("ketchup", "ketchup", "ketchup", tins)

    // Frozen
    i("frozen-peas", "frozen peas", "petits pois surgelés", frozen, family = "peas",
        form = Form.FROZEN)
    i("ice-cream", "ice cream", "glace", frozen)

    // Bakery
    i("bread", "bread", "pain", bakery, also = "pain de mie|sandwich bread|white bread")
    i("baguette", "baguette", "baguette", family = "bread")
    i("burger-buns", "burger buns", "pains burger", family = "bread",
        also = "hamburger buns|brioche buns")
    i("pitta", "pitta bread", "pain pita", family = "bread", also = "pita|pitta|pita bread")
    i("tortillas", "tortillas", "tortillas", bakery,
        also = "tortilla|flour tortillas|wraps|galettes de ble")

    // Drinks
    i("wine", "wine", "vin", drinks)
    i("red-wine", "red wine", "vin rouge", family = "wine",
        also = "dry red wine|vin de bourgogne rouge|bourgogne rouge")
    i("white-wine", "white wine", "vin blanc", family = "wine", also = "dry white wine|vin blanc sec")
    i("port", "port", "porto", family = "wine", also = "ruby port|tawny port")
    i("madeira", "Madeira", "madère", family = "wine")
    i("brandy", "brandy", "cognac", drinks, also = "armagnac")
    i("beer", "beer", "bière", drinks, also = "lager")
    i("cider", "cider", "cidre", drinks)

    // Not something anybody buys for a recipe. Kept in the recipe, left off every list.
    i("water", "water", "eau", Aisle.OTHER, shoppable = false,
        also = "cold water|warm water|hot water|boiling water|ice water|eau froide|" +
            "eau chaude|eau tiede|eau bouillante|tap water|lukewarm water")
}

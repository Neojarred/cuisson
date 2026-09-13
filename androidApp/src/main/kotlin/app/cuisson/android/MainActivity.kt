package app.cuisson.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisson.data.CookRecord
import app.cuisson.domain.Cookbook
import app.cuisson.domain.RecipeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class Tab { Recipes, Cookbooks }

class MainActivity : ComponentActivity() {

    private var openId by mutableStateOf<RecipeId?>(null)
    private var query by mutableStateOf("")
    private var tab by mutableStateOf(Tab.Recipes)
    private var openCookbook by mutableStateOf<Cookbook?>(null)
    private var editing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Cuisson.repository(this)
        val recipes = repository.observeAll()
        val cookbooks = repository.observeCookbooks()

        Cuisson.background.launch {
            repository.backfillSearchIfEmpty()
            // The duration reading changed, so every step is read again once.
            repository.backfillStepDurations(all = true)
        }

        // Alarms do not survive a reboot, so a timer set before one would count down and
        // then say nothing. Anything still due is armed again here.
        KitchenTimer.revive(applicationContext)

        setContent {
            CuissonTheme {
                val library by recipes.collectAsStateWithLifecycle(emptyList())
                val shelf by cookbooks.collectAsStateWithLifecycle(emptyList())
                val open = library.firstOrNull { it.id == openId }

                val shown by produceState(library, library, query) {
                    value = if (query.isBlank()) {
                        library
                    } else {
                        val ranked = withContext(Dispatchers.IO) { repository.search(query) }
                        val byId = library.associateBy { it.id }
                        ranked.mapNotNull(byId::get)
                    }
                }

                when {
                    open != null && editing -> EditRecipeScreen(
                        recipe = open,
                        onSave = { edited ->
                            repository.replace(edited)
                            editing = false
                        },
                        onDelete = {
                            repository.delete(open.id)
                            editing = false
                            openId = null
                        },
                        onCancel = { editing = false },
                    )

                    open != null -> {
                        val record by remember(open.id) { repository.observeCookRecord(open.id) }
                            .collectAsStateWithLifecycle(CookRecord(0, null))
                        RecipeScreen(
                        recipe = open,
                        cookCount = record.count,
                        lastCooked = record.lastCooked,
                        cookbooks = shelf,
                        onFile = { cookbook ->
                            Cuisson.background.launch {
                                repository.defaultChapterOf(cookbook.id)?.let { chapter ->
                                    repository.fileRecipe(
                                        open.id,
                                        chapter,
                                        System.currentTimeMillis(),
                                    )
                                }
                            }
                        },
                        onCook = { CookModeActivity.start(this@MainActivity, open.id) },
                        onEdit = { editing = true },
                        onBack = { openId = null },
                        )
                    }

                    openCookbook != null -> {
                        val cookbook = openCookbook!!
                        val chapters = repository.chaptersOf(cookbook.id).map { it.id }.toSet()
                        RecipeListScreen(
                            title = cookbook.name,
                            recipes = library.filter { it.chapterId in chapters },
                            query = "",
                            onQueryChange = {},
                            total = cookbook.recipeCount,
                            showSearch = false,
                            onOpen = { openId = it.id },
                            onImport = { openCookbook = null },
                            importLabel = "Back",
                        )
                    }

                    else -> Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == Tab.Recipes,
                                    onClick = { tab = Tab.Recipes },
                                    icon = { Icon(RecipesIcon, contentDescription = null) },
                                    label = { Text("Recipes") },
                                )
                                NavigationBarItem(
                                    selected = tab == Tab.Cookbooks,
                                    onClick = { tab = Tab.Cookbooks },
                                    icon = { Icon(CookbooksIcon, contentDescription = null) },
                                    label = { Text("Cookbooks") },
                                )
                            }
                        },
                    ) { inset ->
                        Body(
                            modifier = Modifier.padding(bottom = inset.calculateBottomPadding()),
                            tab = tab,
                            shown = shown,
                            total = library.size,
                            shelf = shelf,
                            query = query,
                            onQueryChange = { query = it },
                            onOpenRecipe = { openId = it.id },
                            onImport = {
                                startActivity(Intent(this, ImportActivity::class.java))
                            },
                            onOpenCookbook = { openCookbook = it },
                            onCreateCookbook = { name ->
                                Cuisson.background.launch {
                                    repository.createCookbook(
                                        UUID.randomUUID().toString(),
                                        name,
                                        System.currentTimeMillis(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Body(
        modifier: Modifier,
        tab: Tab,
        shown: List<app.cuisson.domain.Recipe>,
        total: Int,
        shelf: List<Cookbook>,
        query: String,
        onQueryChange: (String) -> Unit,
        onOpenRecipe: (app.cuisson.domain.Recipe) -> Unit,
        onImport: () -> Unit,
        onOpenCookbook: (Cookbook) -> Unit,
        onCreateCookbook: (String) -> Unit,
    ) {
        androidx.compose.foundation.layout.Box(modifier) {
            when (tab) {
                Tab.Recipes -> RecipeListScreen(
                    recipes = shown,
                    query = query,
                    onQueryChange = onQueryChange,
                    total = total,
                    onOpen = onOpenRecipe,
                    onImport = onImport,
                )
                Tab.Cookbooks -> CookbooksScreen(
                    cookbooks = shelf,
                    onOpen = onOpenCookbook,
                    onCreate = onCreateCookbook,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_RECIPE)?.let { openId = RecipeId(it) }
    }

    companion object {
        /** Set when arriving from the import screen to show a recipe already saved. */
        const val EXTRA_OPEN_RECIPE = "openRecipe"
    }
}

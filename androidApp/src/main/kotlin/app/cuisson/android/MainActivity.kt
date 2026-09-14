package app.cuisson.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import app.cuisson.data.ExportException
import app.cuisson.data.RestoreReport
import java.io.File
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

private enum class Tab { Recipes, Cookbooks, Shopping }

class MainActivity : ComponentActivity() {

    private var openId by mutableStateOf<RecipeId?>(null)
    private var query by mutableStateOf("")
    private var tab by mutableStateOf(Tab.Recipes)
    private var openCookbook by mutableStateOf<Cookbook?>(null)
    private var editing by mutableStateOf(false)

    /**
     * The system's own save and open dialogs, so an Export File can go to a folder, a USB
     * stick or a sync app without Cuisson knowing about any of them, and without a
     * storage permission.
     */
    private val exportTo = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(::writeExport) }

    private val restoreFrom = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::restore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Cuisson.repository(this)
        val recipes = repository.observeAll()
        val cookbooks = repository.observeCookbooks()
        val shopping = Cuisson.shopping(this)
        val shoppingLists = shopping.observeLists()
        // Ingredient names and aisles on a list follow the phone's language, since they
        // are what you read standing in the shop.
        val language = java.util.Locale.getDefault().language

        Cuisson.background.launch {
            repository.backfillSearchIfEmpty()
            // The duration reading changed, so every step is read again once.
            repository.backfillStepDurations(all = true)
            // Likewise the ingredient parse, which is new and has nothing yet.
            repository.backfillIngredientParse(all = true)
        }

        // Alarms do not survive a reboot, so a timer set before one would count down and
        // then say nothing. Anything still due is armed again here.
        KitchenTimer.revive(applicationContext)

        setContent {
            CuissonTheme {
                val library by recipes.collectAsStateWithLifecycle(emptyList())
                val shelf by cookbooks.collectAsStateWithLifecycle(emptyList())
                val lists by shoppingLists.collectAsStateWithLifecycle(emptyList())
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
                        chaptersOf = { repository.chaptersOf(it.id) },
                        onFile = { chapter ->
                            Cuisson.background.launch {
                                repository.fileRecipe(
                                    open.id,
                                    chapter.id,
                                    System.currentTimeMillis(),
                                )
                            }
                        },
                        onCook = { factor ->
                            CookModeActivity.start(this@MainActivity, open.id, factor)
                        },
                        onEdit = { editing = true },
                        lists = lists,
                        onAddToList = { list, servings ->
                            Toast.makeText(this@MainActivity, "Added to ${list.name}", Toast.LENGTH_SHORT).show()
                            Cuisson.background.launch {
                                shopping.addRecipe(list.id, open.id, servings, System.currentTimeMillis())
                            }
                        },
                        onNewListWith = { name, servings ->
                            Toast.makeText(this@MainActivity, "Added to $name", Toast.LENGTH_SHORT).show()
                            val id = UUID.randomUUID().toString()
                            Cuisson.background.launch {
                                val now = System.currentTimeMillis()
                                shopping.createList(id, name, now)
                                shopping.addRecipe(id, open.id, servings, now)
                            }
                        },
                        onBack = { openId = null },
                        )
                    }

                    openCookbook != null -> {
                        val cookbook = shelf.firstOrNull { it.id == openCookbook?.id }
                            ?: openCookbook!!
                        val chapters by remember(cookbook.id) {
                            repository.observeChapters(cookbook.id)
                        }.collectAsStateWithLifecycle(emptyList())
                        val here = chapters.map { it.id }.toSet()
                        CookbookScreen(
                            cookbook = cookbook,
                            chapters = chapters,
                            recipes = library.filter { it.chapterId in here },
                            onOpen = { openId = it.id },
                            onAddChapter = { name ->
                                Cuisson.background.launch {
                                    repository.addChapter(
                                        UUID.randomUUID().toString(),
                                        cookbook.id,
                                        name,
                                    )
                                }
                            },
                            onRenameChapter = { chapter, name ->
                                Cuisson.background.launch {
                                    repository.renameChapter(chapter.id, name)
                                }
                            },
                            onDeleteChapter = { chapter ->
                                Cuisson.background.launch {
                                    repository.deleteChapter(chapter.id, cookbook.id)
                                }
                            },
                            onRenameCookbook = { name ->
                                Cuisson.background.launch {
                                    repository.renameCookbook(cookbook.id, name)
                                }
                            },
                            onDeleteCookbook = {
                                openCookbook = null
                                Cuisson.background.launch {
                                    repository.deleteCookbook(cookbook.id)
                                }
                            },
                            onBack = { openCookbook = null },
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
                                NavigationBarItem(
                                    selected = tab == Tab.Shopping,
                                    onClick = { tab = Tab.Shopping },
                                    icon = { Icon(ShoppingIcon, contentDescription = null) },
                                    label = { Text("Shopping") },
                                )
                            }
                        },
                    ) { inset ->
                        if (tab == Tab.Shopping) {
                            ShoppingTab(
                                shopping = shopping,
                                language = language,
                                modifier = Modifier.padding(bottom = inset.calculateBottomPadding()),
                                onOpenRecipe = { openId = it },
                            )
                        } else Body(
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
                            onExport = { exportTo.launch("cuisson-${java.time.LocalDate.now()}.cuisson") },
                            onRestore = { restoreFrom.launch(arrayOf("*/*")) },
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
        onExport: () -> Unit,
        onRestore: () -> Unit,
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
                    onExport = onExport,
                    onRestore = onRestore,
                )
                Tab.Cookbooks -> CookbooksScreen(
                    cookbooks = shelf,
                    onOpen = onOpenCookbook,
                    onCreate = onCreateCookbook,
                )
                Tab.Shopping -> Unit
            }
        }
    }

    private fun writeExport(uri: Uri) {
        val exports = Cuisson.exports(this)
        val recipes = Cuisson.repository(this)
        Cuisson.background.launch {
            val message = runCatching {
                val bytes = exports.export(appVersion(), System.currentTimeMillis()) { path ->
                    File(path).takeIf { it.isFile }?.readBytes()
                }
                val stream = contentResolver.openOutputStream(uri) ?: error("no stream")
                stream.use { it.write(bytes) }
                val count = recipes.count()
                if (count == 1L) "Exported 1 recipe." else "Exported $count recipes."
            }.getOrElse { "The export file could not be written." }
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
        }
    }

    /** Restoring never deletes anything, so it needs no confirmation beyond picking the file. */
    private fun restore(uri: Uri) {
        val exports = Cuisson.exports(this)
        val images = ImageStore(applicationContext)
        Cuisson.background.launch {
            val message = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("no stream")
                val report = exports.restore(
                    bytes,
                    readImage = { path -> File(path).takeIf { it.isFile }?.readBytes() },
                    writeImage = images::write,
                )
                restoredSentence(report)
            } catch (e: ExportException) {
                e.message ?: "That file could not be restored."
            } catch (e: Exception) {
                "That file could not be read."
            }
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun appVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
            ?: "unknown"

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

private fun restoredSentence(report: RestoreReport): String {
    val parts = buildList {
        if (report.added > 0) add("${report.added} added")
        if (report.updated > 0) add("${report.updated} updated")
        if (report.keptAsCopies == 1) add("1 kept as a copy")
        if (report.keptAsCopies > 1) add("${report.keptAsCopies} kept as copies")
    }
    return if (parts.isEmpty()) {
        "Nothing new: everything in that file is already here."
    } else {
        "Restored. " + parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }
}

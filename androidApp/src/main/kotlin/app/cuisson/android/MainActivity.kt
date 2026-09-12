package app.cuisson.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisson.domain.RecipeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var openId by mutableStateOf<RecipeId?>(null)
    private var query by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Cuisson.repository(this)
        val recipes = repository.observeAll()

        // Recipes saved before search existed are not in the index. Putting them there
        // is a join rather than a column default, so a migration cannot do it.
        Cuisson.background.launch { repository.backfillSearchIfEmpty() }

        setContent {
            CuissonTheme {
                val library by recipes.collectAsStateWithLifecycle(emptyList())
                val open = library.firstOrNull { it.id == openId }

                // Search runs against the index off the main thread, and its order is
                // kept: FTS ranks by relevance and re-sorting would throw that away.
                val shown by produceState(library, library, query) {
                    value = if (query.isBlank()) {
                        library
                    } else {
                        val ranked = withContext(Dispatchers.IO) { repository.search(query) }
                        val byId = library.associateBy { it.id }
                        ranked.mapNotNull(byId::get)
                    }
                }

                if (open == null) {
                    RecipeListScreen(
                        recipes = shown,
                        query = query,
                        onQueryChange = { query = it },
                        total = library.size,
                        onOpen = { openId = it.id },
                        onImport = { startActivity(Intent(this, ImportActivity::class.java)) },
                    )
                } else {
                    RecipeScreen(recipe = open, onBack = { openId = null })
                }
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

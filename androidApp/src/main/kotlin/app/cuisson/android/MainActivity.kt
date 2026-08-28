package app.cuisson.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cuisson.domain.Recipe

class MainActivity : ComponentActivity() {

    private var recipes by mutableStateOf<List<Recipe>>(emptyList())
    private var open by mutableStateOf<Recipe?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CuissonTheme {
                val current = open
                if (current == null) {
                    RecipeListScreen(
                        recipes = recipes,
                        onOpen = { open = it },
                        onImport = { startActivity(Intent(this, ImportActivity::class.java)) },
                    )
                } else {
                    RecipeScreen(recipe = current, onBack = { open = null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val repository = Cuisson.repository(this)
        recipes = repository.all()
        open = open?.let { previous -> recipes.firstOrNull { it.id == previous.id } }
    }
}


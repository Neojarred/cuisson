package app.cuisson.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId

class MainActivity : ComponentActivity() {

    private var openId by mutableStateOf<RecipeId?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipes = Cuisson.repository(this).observeAll()

        setContent {
            CuissonTheme {
                val library by recipes.collectAsStateWithLifecycle(emptyList())
                // The open recipe is looked up by id on every emission rather than held
                // as an object, so an image arriving after the recipe was saved appears
                // without the user having to leave the screen and come back.
                val open = library.firstOrNull { it.id == openId }

                if (open == null) {
                    RecipeListScreen(
                        recipes = library,
                        onOpen = { openId = it.id },
                        onImport = { startActivity(Intent(this, ImportActivity::class.java)) },
                    )
                } else {
                    RecipeScreen(recipe = open, onBack = { openId = null })
                }
            }
        }
    }
}

package app.cuisson.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cuisson.data.DatabaseDriverFactory
import app.cuisson.data.RecipeRepository
import app.cuisson.data.db.CuissonDatabase
import app.cuisson.domain.Recipe

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = CuissonDatabase(DatabaseDriverFactory(applicationContext).create())
        val repository = RecipeRepository(database)

        // Phase 0 puts one recipe in by hand so the whole path, schema through query
        // through screen, is exercised on a real device rather than asserted.
        if (repository.count() == 0L) {
            repository.save(seedRecipe())
        }
        val recipes: List<Recipe> = repository.all()

        setContent {
            CuissonTheme {
                RecipeScreen(recipes.first())
            }
        }
    }
}

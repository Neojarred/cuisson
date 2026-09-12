package app.cuisson.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import java.util.UUID

/**
 * Cooking, as opposed to reading.
 *
 * Its own activity so the screen can be held awake for as long as it is open and no
 * longer. Everything here assumes greasy hands and a glance from half a metre away.
 */
class CookModeActivity : ComponentActivity() {

    private var recipe by mutableStateOf<Recipe?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra(EXTRA_RECIPE)?.let(::RecipeId) ?: run { finish(); return }
        val repository = Cuisson.repository(this)
        recipe = repository.all().firstOrNull { it.id == id } ?: run { finish(); return }

        setContent {
            CuissonTheme {
                recipe?.let { current ->
                    CookModeScreen(
                        recipe = current,
                        onFinish = { cooked ->
                            if (cooked) {
                                repository.logCook(
                                    current.id,
                                    System.currentTimeMillis(),
                                    UUID.randomUUID().toString(),
                                )
                            }
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_RECIPE = "recipe"

        fun start(context: Context, id: RecipeId) {
            context.startActivity(
                Intent(context, CookModeActivity::class.java)
                    .putExtra(EXTRA_RECIPE, id.value)
            )
        }
    }
}

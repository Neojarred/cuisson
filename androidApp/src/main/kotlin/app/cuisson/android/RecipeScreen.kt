package app.cuisson.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.Step

/**
 * Ingredient lines are rendered from [IngredientLine.rawText], never rebuilt from the
 * parsed quantity and unit. Rebuilding is how "5 garlic cloves" turns into
 * "5 clove garlic". See docs/adr/0004.
 */
@Composable
fun RecipeScreen(recipe: Recipe) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Spacer(Modifier.height(32.dp))
                Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitleFor(recipe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                SectionHeading("Ingredients")
            }

            items(recipe.ingredients) { line ->
                IngredientRow(line)
            }

            item {
                Spacer(Modifier.height(24.dp))
                SectionHeading("Method")
            }

            itemsIndexed(recipe.steps) { index, step ->
                StepRow(index + 1, step)
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun IngredientRow(line: IngredientLine) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(line.rawText, style = MaterialTheme.typography.bodyLarge)
        if (line.optional) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "optional",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, step: Step) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Column {
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
            step.durationSeconds?.let { seconds ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "timer ${seconds / 60} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (step.references.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                // The source pointed at something we did not capture. Saying so beats
                // looking complete and failing the cook halfway through.
                Text(
                    text = "refers to ${step.references.joinToString(", ")}, not captured",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun subtitleFor(recipe: Recipe): String {
    val parts = mutableListOf<String>()
    recipe.servings?.let { parts += "${it.count.toInt()} servings" }
    recipe.timings.totalMinutes?.let { parts += "$it min" }
    parts += recipe.extraction.tier.name.lowercase().replace('_', ' ')
    return parts.joinToString(" · ")
}

package app.cuisson.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cuisson.domain.Recipe

@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    onOpen: (Recipe) -> Unit,
    onImport: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recipes", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onImport) { Text("Import") }
            }
            Spacer(Modifier.height(16.dp))

            if (recipes.isEmpty()) {
                EmptyLibrary()
            } else {
                LazyColumn {
                    items(recipes) { recipe ->
                        RecipeRow(recipe, onClick = { onOpen(recipe) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column {
        Text(
            text = "Nothing here yet.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Share a recipe link to Cuisson from any app, or press Import and " +
                "paste one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecipeRow(recipe: Recipe, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (recipe.extraction.needsReview) {
                Text(
                    text = "needs review",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = summaryOf(recipe),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun summaryOf(recipe: Recipe): String {
    val parts = mutableListOf<String>()
    parts += "${recipe.ingredients.size} ingredients"
    recipe.timings.totalMinutes?.let { parts += "$it min" }
    recipe.source.url?.let { url ->
        parts += url.removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .removePrefix("www.")
    }
    return parts.joinToString(" · ")
}

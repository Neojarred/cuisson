package app.cuisson.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cuisson.domain.Recipe

@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    query: String,
    onQueryChange: (String) -> Unit,
    total: Int,
    onOpen: (Recipe) -> Unit,
    onImport: () -> Unit,
    title: String = "Recipes",
    showSearch: Boolean = true,
    importLabel: String = "Import",
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Button(
                    onClick = onImport,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 18.dp,
                        vertical = 10.dp,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(importLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (showSearch) {
                Spacer(Modifier.height(14.dp))
                SearchField(query, onQueryChange)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    total == 0 -> ""
                    query.isNotBlank() -> "${recipes.size} of $total"
                    else -> "$total saved"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (recipes.isEmpty() && query.isNotBlank()) {
                Text(
                    text = "Nothing matches that.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (recipes.isEmpty()) {
                EmptyLibrary()
            } else {
                LazyColumn {
                    items(recipes) { recipe ->
                        RecipeRow(recipe, onClick = { onOpen(recipe) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/**
 * Searches names, ingredients and the author's notes at once, because what a person
 * remembers about a recipe is as often "the one with miso" as its title.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text("Search recipes and ingredients", style = MaterialTheme.typography.bodyMedium)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        trailingIcon = if (query.isBlank()) null else {
            {
                TextButton(onClick = { onQueryChange("") }) {
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyLibrary() {
    Column {
        Text("Nothing here yet.", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summaryOf(recipe),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recipe.extraction.needsReview) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "NEEDS REVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        // A wall of text is what the apps he rejected look like. The picture is the
        // fastest way to recognise a recipe you have cooked before.
        if (recipe.imagePath != null) {
            LocalImage(recipe.imagePath!!, modifier = Modifier.size(64.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

private fun summaryOf(recipe: Recipe): String {
    val parts = mutableListOf<String>()
    recipe.timings.totalMinutes?.let { parts += "$it min" }
    parts += "${recipe.ingredients.size} ingredients"
    recipe.source.url?.let { url ->
        parts += url.removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .removePrefix("www.")
    }
    return parts.joinToString("  ·  ")
}

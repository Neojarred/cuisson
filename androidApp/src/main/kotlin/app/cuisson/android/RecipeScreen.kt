package app.cuisson.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.referencedNoteLabels
import app.cuisson.domain.Step

/**
 * Ingredient lines are rendered from [IngredientLine.rawText], never rebuilt from the
 * parsed quantity and unit. Rebuilding is how "5 garlic cloves" turns into
 * "5 clove garlic". See docs/adr/0004.
 */
@Composable
fun RecipeScreen(recipe: Recipe, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("Back")
                }
                Spacer(Modifier.height(4.dp))
                recipe.imagePath?.let {
                    LocalImage(it)
                    Spacer(Modifier.height(12.dp))
                }
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

            itemsIndexed(recipe.ingredients) { index, line ->
                val previous = recipe.ingredients.getOrNull(index - 1)?.groupLabel
                val group = line.groupLabel
                if (group != null && group != previous) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                IngredientRow(line)
            }

            item {
                Spacer(Modifier.height(24.dp))
                SectionHeading("Method")
            }

            itemsIndexed(recipe.steps) { index, step ->
                StepRow(index + 1, step)
            }

            if (recipe.sourceNotes.isNotEmpty()) {
                val referenced = recipe.referencedNoteLabels()
                val pointedAt = recipe.sourceNotes.filter {
                    it.label?.lowercase() in referenced
                }
                val rest = recipe.sourceNotes - pointedAt.toSet()

                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeading("Notes from the source")
                    Text(
                        text = "Written by " + (recipe.source.name ?: "the author") + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                items(pointedAt) { note -> NoteRow(note) }

                if (rest.isNotEmpty()) {
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                if (expanded) "Hide the author's other notes"
                                else "${rest.size} more notes from the author",
                            )
                        }
                        if (expanded) {
                            Column { rest.forEach { NoteRow(it) } }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun NoteRow(note: app.cuisson.domain.SourceNote) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        note.label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp),
            )
        }
        Text(note.text, style = MaterialTheme.typography.bodyMedium)
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
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.rawText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
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

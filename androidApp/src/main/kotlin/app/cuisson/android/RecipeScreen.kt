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
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
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
import app.cuisson.domain.Cookbook
import app.cuisson.domain.Recipe
import app.cuisson.domain.referencedNoteLabels
import app.cuisson.domain.Step

/**
 * Ingredient lines are rendered from [IngredientLine.rawText], never rebuilt from the
 * parsed quantity and unit. Rebuilding is how "5 garlic cloves" turns into
 * "5 clove garlic". See docs/adr/0004.
 */
@Composable
fun RecipeScreen(
    recipe: Recipe,
    onBack: () -> Unit,
    cookbooks: List<Cookbook> = emptyList(),
    onFile: (Cookbook) -> Unit = {},
    onCook: () -> Unit = {},
) {
    var filing by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val capturedNoteLabels = recipe.sourceNotes.mapNotNull { it.label?.lowercase() }.toSet()
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                        Text("Back", style = MaterialTheme.typography.labelLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (cookbooks.isNotEmpty()) {
                            TextButton(
                                onClick = { filing = true },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("File in…", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        TextButton(onClick = onCook, contentPadding = PaddingValues(0.dp)) {
                            Text("Cook", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                recipe.imagePath?.let {
                    LocalImage(it)
                    Spacer(Modifier.height(12.dp))
                }
                Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = subtitleFor(recipe).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(30.dp))
                SectionHeading("Ingredients")
            }

            itemsIndexed(recipe.ingredients) { index, line ->
                val previous = recipe.ingredients.getOrNull(index - 1)?.groupLabel
                val group = line.groupLabel
                if (group != null && group != previous) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = group.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                IngredientRow(line)
            }

            item {
                Spacer(Modifier.height(24.dp))
                SectionHeading("Method")
            }

            itemsIndexed(recipe.steps) { index, step ->
                StepRow(index + 1, step, captured = capturedNoteLabels)
            }

            if (recipe.sourceNotes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    // Off the recipe's flow entirely. A publisher's notes run longer
                    // than the recipe and mix substitutions worth having with method
                    // narration and the date the post was first published. Which is
                    // which is a judgement no rule here can make, so the reader makes
                    // it. Cook Mode is where an individual note earns its place, shown
                    // against the step that points at it.
                    val referenced = recipe.referencedNoteLabels()
                    val ordered = recipe.sourceNotes.sortedByDescending {
                        it.label?.lowercase() in referenced
                    }
                    var expanded by remember { mutableStateOf(false) }
                    HorizontalDivider()
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text(
                            text = if (expanded) "Hide the author's notes"
                            else "Notes from the author · ${recipe.sourceNotes.size}",
                        )
                    }
                    if (expanded) {
                        Column {
                            Text(
                                text = "Written by " + (recipe.source.name ?: "the author") + ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            ordered.forEach { NoteRow(it) }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

/** Filing is one tap from the recipe, and never demanded at import. */
@Composable
private fun FilingDialog(
    cookbooks: List<Cookbook>,
    onPick: (Cookbook) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File in", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                cookbooks.forEach { cookbook ->
                    Text(
                        text = cookbook.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(cookbook) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
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
private fun StepRow(number: Int, step: Step, captured: Set<String>) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(32.dp),
        )
        Column {
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
            step.durationSeconds?.let { seconds ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${seconds / 60} MIN TIMER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Only the references we could not satisfy. Warning about a note that is
            // sitting in the list below contradicts the screen it is printed on.
            val missing = step.references.filterNot { reference ->
                reference.filter { it.isLetterOrDigit() }
                    .lowercase()
                    .removePrefix("note") in captured
            }
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "refers to ${missing.joinToString(", ")}, not captured",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun subtitleFor(recipe: Recipe): String {
    // How a recipe was extracted is our business, not the reader's. "STRUCTURED" meant
    // nothing to anyone holding a knife. What is worth saying is when the result should
    // not be trusted, and that has its own mark.
    val parts = mutableListOf<String>()
    recipe.servings?.let { servings ->
        val count = if (servings.count % 1.0 == 0.0) servings.count.toInt().toString()
        else servings.count.toString()
        parts += listOfNotNull(count, servings.unit ?: "servings").joinToString(" ")
    }
    recipe.timings.totalMinutes?.let { parts += "$it min" }
    if (recipe.extraction.needsReview) parts += "needs review"
    return parts.joinToString(" · ")
}

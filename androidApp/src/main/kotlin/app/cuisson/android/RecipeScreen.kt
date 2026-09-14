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
import app.cuisson.domain.Chapter
import app.cuisson.domain.Cookbook
import app.cuisson.domain.Recipe
import app.cuisson.domain.referencedNoteLabels
import app.cuisson.domain.Step
import app.cuisson.text.scaleIngredient

/**
 * Ingredient lines are rendered from [IngredientLine.text], never rebuilt from the
 * parsed quantity and unit. Rebuilding is how "5 garlic cloves" turns into
 * "5 clove garlic". See docs/adr/0004.
 */
@Composable
fun RecipeScreen(
    recipe: Recipe,
    onBack: () -> Unit,
    cookbooks: List<Cookbook> = emptyList(),
    cookCount: Int = 0,
    lastCooked: Long? = null,
    chaptersOf: (Cookbook) -> List<Chapter> = { emptyList() },
    onFile: (Chapter) -> Unit = {},
    onCook: (Double) -> Unit = {},
    onEdit: () -> Unit = {},
) {
    var filing by remember { mutableStateOf(false) }
    // The Serving Scale is never stored on the Recipe, which always holds the servings it
    // was written for. This is a way of reading it, not a change to it.
    val written = recipe.servings?.count
    var serving by remember(recipe.id) { mutableStateOf(written) }
    val factor = if (written != null && written > 0 && serving != null) {
        serving!! / written
    } else {
        1.0
    }
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
                        TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) {
                            Text("Edit", style = MaterialTheme.typography.labelLarge)
                        }
                        if (cookbooks.isNotEmpty()) {
                            TextButton(
                                onClick = { filing = true },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("File in…", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        TextButton(
                            onClick = { onCook(factor) },
                            contentPadding = PaddingValues(0.dp),
                        ) {
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
                if (cookCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = cookingRecord(cookCount, lastCooked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (written != null) {
                    Spacer(Modifier.height(18.dp))
                    ServingsRow(
                        serving = serving ?: written,
                        written = written,
                        unit = recipe.servings?.unit,
                        onChange = { serving = it },
                    )
                }
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
                IngredientRow(line, factor)
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

    if (filing) {
        FilingDialog(
            cookbooks = cookbooks,
            chaptersOf = chaptersOf,
            onPick = { onFile(it); filing = false },
            onDismiss = { filing = false },
        )
    }
}

/**
 * Filing is one tap from the recipe, and never demanded at import.
 *
 * Picking a cookbook with no chapters of its own files the recipe straight away. Only a
 * cookbook that has been divided asks the second question, which keeps the common case
 * at one tap and stops chapters being something everybody has to think about.
 */
@Composable
private fun FilingDialog(
    cookbooks: List<Cookbook>,
    chaptersOf: (Cookbook) -> List<Chapter>,
    onPick: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf<Cookbook?>(null) }
    val book = chosen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = book?.name ?: "File in",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                if (book == null) {
                    cookbooks.forEach { cookbook ->
                        FilingRow(cookbook.name) {
                            val chapters = chaptersOf(cookbook)
                            if (chapters.any { !it.isDefault }) {
                                chosen = cookbook
                            } else {
                                chapters.firstOrNull()?.let(onPick)
                            }
                        }
                    }
                } else {
                    chaptersOf(book).forEach { chapter ->
                        FilingRow(
                            label = if (chapter.isDefault) "Not in a chapter" else chapter.name,
                            onClick = { onPick(chapter) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (book == null) onDismiss() else chosen = null }) {
                Text(if (book == null) "Cancel" else "Back")
            }
        },
    )
}

@Composable
private fun FilingRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
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

/**
 * How many this is being cooked for.
 *
 * Only shown when the recipe says what it was written for, because a scale needs
 * something to be a scale of. Guessing a base and multiplying by it would produce
 * confident, wrong numbers in an ingredient list, which is the worst place for them.
 */
@Composable
private fun ServingsRow(
    serving: Double,
    written: Double,
    unit: String?,
    onChange: (Double) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stepper("\u2212", enabled = serving > 1) { onChange(serving - 1) }
        Text(
            text = countText(serving) + " " + (unit ?: "servings"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Stepper("+", enabled = serving < written * 8) { onChange(serving + 1) }
        if (serving != written) {
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = { onChange(written) },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    text = "written for ${countText(written)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Stepper(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

private fun countText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun IngredientRow(line: IngredientLine, factor: Double) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Scaled at display time from the line's own words. Nothing is rewritten and
            // nothing is stored: leaving the screen puts the recipe back as published.
            text = scaleIngredient(line.text, factor),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (line.isAmended) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "edited",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/**
 * What the Cook Entries add up to. A recipe you have made four times is a different thing
 * from one you saved and never cooked, and that is worth saying on the recipe itself.
 */
private fun cookingRecord(count: Int, lastCooked: Long?): String {
    val times = if (count == 1) "Cooked once" else "Cooked $count times"
    val last = lastCooked?.let {
        val date = java.time.Instant.ofEpochMilli(it)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val today = java.time.LocalDate.now()
        when {
            date == today -> "today"
            date == today.minusDays(1) -> "yesterday"
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }
    return listOfNotNull(times, last?.let { "last $it" }).joinToString(" · ").uppercase()
}

private fun subtitleFor(recipe: Recipe): String {
    // How a recipe was extracted is our business, not the reader's. "STRUCTURED" meant
    // nothing to anyone holding a knife. What is worth saying is when the result should
    // not be trusted, and that has its own mark.
    val parts = mutableListOf<String>()
    // Servings are not repeated here: they have their own control, which can change them.
    recipe.timings.totalMinutes?.let { parts += "$it min" }
    if (recipe.extraction.needsReview) parts += "needs review"
    return parts.joinToString(" · ")
}

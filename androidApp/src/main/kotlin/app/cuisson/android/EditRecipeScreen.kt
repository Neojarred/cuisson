package app.cuisson.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.Recipe
import app.cuisson.domain.Servings
import app.cuisson.domain.Step
import kotlin.time.Clock

/**
 * Where a recipe gets fixed.
 *
 * Nothing here overwrites what the publisher wrote. An edited line keeps the original
 * beside it as an Amendment, which is what makes "revert" possible and what leaves the
 * phase 4 ingredient parser something real to read. See ADR-0004.
 *
 * The publisher's own notes are not editable at all. They are attributed to someone else,
 * and an attributed quote you can rewrite is not a quote. See ADR-0009.
 */
@Composable
fun EditRecipeScreen(
    recipe: Recipe,
    onSave: (Recipe) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember(recipe.id) { mutableStateOf(recipe.title) }
    var servings by remember(recipe.id) {
        mutableStateOf(recipe.servings?.count?.asPlainNumber().orEmpty())
    }
    var servingsUnit by remember(recipe.id) {
        mutableStateOf(recipe.servings?.unit.orEmpty())
    }
    var totalMinutes by remember(recipe.id) {
        mutableStateOf(recipe.timings.totalMinutes?.toString().orEmpty())
    }
    var myNote by remember(recipe.id) { mutableStateOf(recipe.notes.orEmpty()) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val ingredients = remember(recipe.id) {
        mutableStateListOf<EditableLine>().apply {
            addAll(
                recipe.ingredients.map {
                    EditableLine(it.rawText, it.text, it.groupLabel.orEmpty())
                }
            )
        }
    }
    val steps = remember(recipe.id) {
        mutableStateListOf<EditableLine>().apply {
            addAll(recipe.steps.map { EditableLine(it.sourceText, it.text, "") })
        }
    }

    BackHandler(onBack = onCancel)

    fun save() {
        onSave(
            recipe.copy(
                title = title.trim().ifBlank { recipe.title },
                servings = servings.toDoubleOrNull()
                    ?.let { Servings(it, servingsUnit.trim().ifBlank { null }) },
                timings = recipe.timings.copy(totalMinutes = totalMinutes.toIntOrNull()),
                notes = myNote.trim().ifBlank { null },
                ingredients = ingredients.usable().mapIndexed { index, line ->
                    val original = recipe.ingredients.firstOrNull { it.rawText == line.source }
                    (original ?: IngredientLine(id = "", position = 0, rawText = line.text)).copy(
                        id = "${recipe.id.value}-i$index",
                        position = index,
                        rawText = line.source ?: line.text,
                        amendment = line.amendmentOrNull(),
                        groupLabel = line.section.trim().ifBlank { null },
                    )
                },
                steps = steps.usable().mapIndexed { index, line ->
                    Step(
                        id = "${recipe.id.value}-s$index",
                        position = index,
                        sourceText = line.source ?: line.text,
                        amendment = line.amendmentOrNull(),
                        // The duration is read again when this is written, so a step
                        // rewritten from twenty minutes to twenty-five offers the timer
                        // the cook actually meant.
                        references = recipe.steps.firstOrNull { it.sourceText == line.source }
                            ?.references
                            ?: emptyList(),
                    )
                },
                updatedAt = Clock.System.now(),
            )
        )
    }

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
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = ::save, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field(title, { title = it }, "Name")
                val published = recipe.rawTitle
                if (published != null && published != title) {
                    RevertRow("Published as: $published") { title = published }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(
                        value = servings,
                        onChange = { servings = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "Servings",
                        modifier = Modifier.width(110.dp),
                        numeric = true,
                    )
                    Field(
                        value = servingsUnit,
                        onChange = { servingsUnit = it },
                        label = "Called",
                        modifier = Modifier.weight(1f),
                    )
                    Field(
                        value = totalMinutes,
                        onChange = { totalMinutes = it.filter(Char::isDigit) },
                        label = "Minutes",
                        modifier = Modifier.width(110.dp),
                        numeric = true,
                    )
                }
                Spacer(Modifier.height(24.dp))
                EditHeading("Ingredients")
            }

            itemsIndexed(ingredients) { index, line ->
                LineEditor(
                    line = line,
                    lines = ingredients,
                    index = index,
                    placeholder = "Ingredient",
                    sectionable = true,
                )
            }

            item {
                AddRow("Add an ingredient") { ingredients.add(EditableLine(null, "", "")) }
                Spacer(Modifier.height(24.dp))
                EditHeading("Method")
            }

            itemsIndexed(steps) { index, line ->
                LineEditor(
                    line = line,
                    lines = steps,
                    index = index,
                    placeholder = "Step",
                    sectionable = false,
                )
            }

            item {
                AddRow("Add a step") { steps.add(EditableLine(null, "", "")) }
                Spacer(Modifier.height(24.dp))
                EditHeading("Your note")
                Field(
                    value = myNote,
                    onChange = { myNote = it },
                    placeholder = "Anything you want to remember",
                    multiline = true,
                )
                if (recipe.sourceNotes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The ${recipe.sourceNotes.size} notes from the author stay as " +
                            "they wrote them and are not edited here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(32.dp))
                HorizontalDivider()
                TextButton(
                    onClick = { confirmingDelete = true },
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Text(
                        text = "Delete this recipe",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${recipe.title}?") },
            // Said plainly, because it is true and because there is no server holding a
            // copy. Nothing here can undo it.
            text = { Text("This removes it from your library. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep it") }
            },
        )
    }
}

/**
 * One line being edited, and the wording it arrived with.
 *
 * [source] is null for a line the user wrote themselves, which has no publisher to differ
 * from and is therefore its own original.
 */
private data class EditableLine(
    val source: String?,
    val text: String,
    val section: String,
) {
    fun amendmentOrNull(): String? = text.trim().takeIf { it != source && source != null }

    val isAmended: Boolean get() = source != null && text.trim() != source
}

private fun SnapshotStateList<EditableLine>.usable(): List<EditableLine> =
    filter { it.text.isNotBlank() }.map { it.copy(text = it.text.trim()) }

/**
 * One line, with its handles kept small.
 *
 * The section field is revealed rather than always drawn. A first attempt gave every
 * ingredient a second box the same size as the first, and twenty ingredients became forty
 * boxes of mostly empty placeholder text. Most lines belong to no section, so most lines
 * should not have to say so.
 */
@Composable
private fun LineEditor(
    line: EditableLine,
    lines: SnapshotStateList<EditableLine>,
    index: Int,
    placeholder: String,
    sectionable: Boolean,
) {
    var asked by remember { mutableStateOf(false) }
    val showSection = sectionable && (asked || line.section.isNotBlank())

    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        if (showSection) {
            Field(
                value = line.section,
                onChange = { lines[index] = line.copy(section = it) },
                placeholder = "Section, such as For the sauce",
                small = true,
            )
        }
        Field(
            value = line.text,
            onChange = { lines[index] = line.copy(text = it) },
            placeholder = placeholder,
            multiline = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sectionable) {
                SmallAction("Section", enabled = !showSection) { asked = true }
            }
            Spacer(Modifier.weight(1f))
            SmallAction("Up", enabled = index > 0) { lines.swap(index, index - 1) }
            SmallAction("Down", enabled = index < lines.lastIndex) { lines.swap(index, index + 1) }
            SmallAction("Remove") { lines.removeAt(index) }
        }
        if (line.isAmended) {
            RevertRow("Published as: ${line.source}") {
                lines[index] = line.copy(text = line.source.orEmpty())
            }
        }
    }
}

/**
 * What the publisher wrote, with a way back to it.
 *
 * An edit that cannot be undone is one people hesitate to make, and hesitating over a
 * typo in an ingredient list is not a good use of anyone's evening.
 */
@Composable
private fun RevertRow(original: String, onRevert: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = original,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        SmallAction("Revert", onClick = onRevert)
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EditHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
}

/**
 * A label sits above the box permanently; a placeholder disappears as soon as there is
 * anything in it. The recipe's own details are worth labelling once. A list of twenty
 * ingredients is not worth the word "Ingredient" twenty times.
 */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    multiline: Boolean = false,
    numeric: Boolean = false,
    small: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = label?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        singleLine = !multiline,
        textStyle = if (small) MaterialTheme.typography.bodyMedium
        else MaterialTheme.typography.bodyLarge,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = modifier,
    )
}

private fun SnapshotStateList<EditableLine>.swap(a: Int, b: Int) {
    val held = this[a]
    this[a] = this[b]
    this[b] = held
}

/** "4" rather than "4.0", because nobody writes servings with a decimal point. */
private fun Double.asPlainNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

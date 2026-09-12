package app.cuisson.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cuisson.domain.DraftRecipe
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.ExtractionWarning
import app.cuisson.domain.RecipeId

@Composable
fun ImportScreen(
    state: ImportState,
    onUrlChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSave: (DraftRecipe) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onTypeInstead: () -> Unit = {},
    onOpenSaved: (RecipeId) -> Unit = {},
    onImportAnyway: (ImportState.AlreadyHave) -> Unit = {},
    onTypedChanged: (String, String) -> Unit = { _, _ -> },
    onParseTyped: (String, String) -> Unit = { _, _ -> },
) {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        when (state) {
            is ImportState.AskingForUrl ->
                AskForUrl(state.input, onUrlChanged, onSubmit, onCancel, onTypeInstead)
            is ImportState.TypingText -> TypeRecipe(
                title = state.title,
                body = state.body,
                onChanged = onTypedChanged,
                onDone = onParseTyped,
                onCancel = onCancel,
            )
            is ImportState.Working -> Working(state.url)
            is ImportState.Reviewing -> Review(state, onSave, onCancel)
            is ImportState.Refused -> Message(
                heading = "That site refused us",
                body = "It answered ${state.status} rather than sending the page. This is " +
                    "the site's decision and nothing you can change. Copying the recipe " +
                    "text and pasting it will still work.",
                onCancel = onCancel,
            )
            is ImportState.NoNetworkPermission -> Message(
                heading = "Cuisson could not reach the network",
                body = "The request never got as far as a server. Either this device is " +
                    "offline, or Cuisson has been denied the network: some Android " +
                    "systems let you decide that for each app, and it can be switched " +
                    "off without you noticing.",
                onCancel = onCancel,
                action = "Open settings" to onOpenSettings,
            )
            is ImportState.AlreadyHave -> AlreadyHave(
                state = state,
                onOpen = { onOpenSaved(state.existing.id) },
                onImportAnyway = { onImportAnyway(state) },
                onCancel = onCancel,
            )
            is ImportState.Broke -> Message(
                heading = "That did not work",
                body = state.reason,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun AskForUrl(
    input: String,
    onChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Import a recipe", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onChanged,
            label = { Text("Recipe address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSubmit(input) }, enabled = input.isNotBlank()) {
                Text("Import")
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onTypeInstead, contentPadding = PaddingValues(0.dp)) {
            Text("Paste or type the recipe instead")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You can also share a link to Cuisson from your browser, which is " +
                "usually quicker.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * For a recipe with no page behind it: dictated by a relative, copied out of a message,
 * or read off a card.
 *
 * One title and one block of text, because asking someone to sort their own ingredients
 * from their own method before the app will accept it is exactly the data entry that
 * makes people stop using these apps. Cuisson works out the split and the Review is where
 * it gets corrected.
 */
@Composable
private fun TypeRecipe(
    title: String,
    body: String,
    onChanged: (String, String) -> Unit,
    onDone: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Type or paste a recipe", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { onChanged(it, body) },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { onChanged(title, it) },
            label = { Text("Ingredients and method") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Paste it however it is written. Headings help but are not needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onDone(title, body) }, enabled = body.isNotBlank()) {
                Text("Continue")
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun Working(url: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(url.take(60), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Message(
    heading: String,
    body: String,
    onCancel: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(heading, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            action?.let { (label, onClick) ->
                Button(onClick = onClick) { Text(label) }
            }
            TextButton(onClick = onCancel) { Text("Close") }
        }
    }
}

/**
 * Offered when this address has been imported before.
 *
 * The recipe already saved comes first, because importing the same page twice is nearly
 * always a slip. Saving a second copy is still one tap away, since publishers do change
 * their recipes and two versions of one page can both be worth keeping.
 */
@Composable
private fun AlreadyHave(
    state: ImportState.AlreadyHave,
    onOpen: () -> Unit,
    onImportAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("You already have this", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.existing.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Saved from the same address.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen) { Text("Open it") }
            TextButton(onClick = onImportAnyway) { Text("Save a second copy") }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) { Text("Cancel") }
    }
}

/**
 * The Review. Always shown, and dismissible in one tap when the extraction was clean.
 *
 * What it must never do is present an uncertain result as a finished one, so anything the
 * extractor could not make sense of is stated here before the user commits to it.
 */
@Composable
private fun Review(
    state: ImportState.Reviewing,
    onSave: (DraftRecipe) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onTypeInstead: () -> Unit = {},
    onOpenSaved: (RecipeId) -> Unit = {},
    onImportAnyway: (ImportState.AlreadyHave) -> Unit = {},
    onTypedChanged: (String, String) -> Unit = { _, _ -> },
    onParseTyped: (String, String) -> Unit = { _, _ -> },
) {
    val draft = state.draft
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                state.imagePath?.let {
                    LocalImage(it, height = 160)
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = draft.title.ifBlank { "Untitled recipe" },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = describe(draft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.warnings.isNotEmpty() || !state.clean) {
                    Spacer(Modifier.height(12.dp))
                    Warnings(draft)
                }
                Spacer(Modifier.height(20.dp))
                Heading("Ingredients")
            }
            itemsIndexed(draft.ingredientLines) { index, line ->
                val previous = draft.ingredientLines.getOrNull(index - 1)?.group
                val group = line.group
                if (group != null && group != previous) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(line.text, modifier = Modifier.padding(vertical = 4.dp))
            }
            item {
                Spacer(Modifier.height(20.dp))
                Heading("Method")
            }
            itemsIndexed(draft.steps) { index, step ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "${index + 1}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column {
                        // Only where it changes. Ricardo labels every step "Meat Sauce",
                        // and repeating that above all eleven of them is noise.
                        val section = step.sectionLabel
                        if (section != null && section != draft.steps.getOrNull(index - 1)?.sectionLabel) {
                            Text(
                                text = section,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(step.text)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onSave(draft) }) { Text("Save") }
            TextButton(onClick = onCancel) { Text("Discard") }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    HorizontalDivider()
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Warnings(draft: DraftRecipe) {
    Column {
        if (draft.tier == ExtractionTier.PAGE_TEXT) {
            Text(
                text = "No recipe markup on this page. The text is here so you can salvage " +
                    "it, but nothing has been understood.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        draft.warnings.forEach { warning ->
            explain(warning)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun explain(warning: ExtractionWarning): String? = when (warning) {
    ExtractionWarning.NO_INGREDIENTS -> "No ingredients were found."
    ExtractionWarning.NO_STEPS -> "No method was found."
    ExtractionWarning.NO_TITLE -> "No title was found."
    ExtractionWarning.REFERENCES_UNCAPTURED_NOTES ->
        "This recipe refers to notes that were not part of the recipe data. Check the " +
            "original page before cooking."
    ExtractionWarning.STEPS_CONTAINED_MARKUP -> null
    ExtractionWarning.SERVINGS_NOT_UNDERSTOOD -> "The serving count could not be read."
    ExtractionWarning.DURATION_NOT_UNDERSTOOD -> "The timings could not be read."
}

private fun describe(draft: DraftRecipe): String {
    val parts = mutableListOf<String>()
    // "8 personnes" says what it is. A bare "4" does not, so it gets a noun.
    draft.servingsText?.let { parts += if (it.all(Char::isDigit)) "$it servings" else it }
    draft.totalMinutes?.let { parts += "$it min" }
    parts += "${draft.ingredientLines.size} ingredients"
    parts += when (draft.tier) {
        ExtractionTier.STRUCTURED -> "from the site's own recipe data"
        ExtractionTier.SITE_RULE -> "read from the page"
        ExtractionTier.MODEL -> "assembled by a model"
        ExtractionTier.PAGE_TEXT -> "page text only"
        ExtractionTier.HAND_WRITTEN -> "typed in"
    }
    return parts.joinToString(" · ")
}

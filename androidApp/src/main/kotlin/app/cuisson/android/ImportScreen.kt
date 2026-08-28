package app.cuisson.android

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

@Composable
fun ImportScreen(
    state: ImportState,
    onUrlChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSave: (DraftRecipe) -> Unit,
    onCancel: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        when (state) {
            is ImportState.AskingForUrl -> AskForUrl(state.input, onUrlChanged, onSubmit, onCancel)
            is ImportState.Working -> Working(state.url)
            is ImportState.Reviewing -> Review(state, onSave, onCancel)
            is ImportState.Refused -> Message(
                heading = "That site refused us",
                body = "It answered ${state.status} rather than sending the page. This is " +
                    "the site's decision and nothing you can change. Copying the recipe " +
                    "text and pasting it will still work.",
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
        Text(
            text = "You can also share a link to Cuisson from your browser, which is " +
                "usually quicker.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun Message(heading: String, body: String, onCancel: () -> Unit) {
    Column(modifier = Modifier.padding(20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(heading, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCancel) { Text("Close") }
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
) {
    val draft = state.draft
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
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
            items(draft.ingredientLines) { line ->
                Text(line, modifier = Modifier.padding(vertical = 4.dp))
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
                        step.sectionLabel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
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
    draft.servingsText?.let { parts += it }
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

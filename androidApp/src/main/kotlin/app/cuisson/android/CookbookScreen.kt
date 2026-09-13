package app.cuisson.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cuisson.domain.Chapter
import app.cuisson.domain.Cookbook
import app.cuisson.domain.Recipe

/**
 * One cookbook, opened.
 *
 * Chapters appear only once a second one exists. A cookbook of six recipes needs no
 * headings, and making someone invent one before they can file anything is exactly the
 * friction that stops people filing at all.
 */
@Composable
fun CookbookScreen(
    cookbook: Cookbook,
    chapters: List<Chapter>,
    recipes: List<Recipe>,
    onOpen: (Recipe) -> Unit,
    onAddChapter: (String) -> Unit,
    onRenameChapter: (Chapter, String) -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onRenameCookbook: (String) -> Unit,
    onDeleteCookbook: () -> Unit,
    onBack: () -> Unit,
) {
    var namingChapter by remember { mutableStateOf(false) }
    var renamingCookbook by remember { mutableStateOf(false) }
    var removingCookbook by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Chapter?>(null) }
    var removing by remember { mutableStateOf<Chapter?>(null) }

    BackHandler(onBack = onBack)

    // The unnamed chapter first and without a heading, then the named ones in order.
    // Recipes in it are in the cookbook rather than in one of its chapters, which is a
    // real place to be and not a mistake.
    val ordered = chapters.sortedBy { !it.isDefault }
    val byChapter = recipes.groupBy { it.chapterId }
    val showHeadings = chapters.count { !it.isDefault } > 0

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("Cookbooks", style = MaterialTheme.typography.labelLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!cookbook.isUnfiled) {
                        TextButton(
                            onClick = { renamingCookbook = true },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("Rename", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    TextButton(
                        onClick = { namingChapter = true },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("New chapter", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = cookbook.name,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn {
                ordered.forEach { chapter ->
                    val held = byChapter[chapter.id].orEmpty()
                    if (showHeadings && !chapter.isDefault) {
                        item(key = "h-${chapter.id}") {
                            ChapterHeading(
                                chapter = chapter,
                                count = held.size,
                                onRename = { renaming = chapter },
                                onRemove = { removing = chapter },
                            )
                        }
                    }
                    // The unnamed chapter's own heading, shown only when named chapters
                    // exist beside it and there is something in it to head.
                    if (showHeadings && chapter.isDefault && held.isNotEmpty()) {
                        item(key = "h-loose") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "NOT IN A CHAPTER",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    held.forEach { recipe ->
                        item(key = recipe.id.value) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(recipe) }
                                    .padding(vertical = 14.dp),
                            ) {
                                Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                if (recipes.isEmpty()) {
                    item {
                        Spacer(Modifier.height(22.dp))
                        Text(
                            text = "Nothing filed here yet. File a recipe from the recipe " +
                                "itself, using File in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Unfiled cannot go. It is where everything else falls back to.
                if (!cookbook.isUnfiled) {
                    item {
                        Spacer(Modifier.height(36.dp))
                        HorizontalDivider()
                        TextButton(
                            onClick = { removingCookbook = true },
                            contentPadding = PaddingValues(vertical = 10.dp),
                        ) {
                            Text(
                                text = "Delete this cookbook",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }

    if (namingChapter) {
        NameDialog(
            title = "New chapter",
            onDismiss = { namingChapter = false },
            onConfirm = { name ->
                namingChapter = false
                onAddChapter(name)
            },
        )
    }

    renaming?.let { chapter ->
        NameDialog(
            title = "Rename chapter",
            initial = chapter.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                onRenameChapter(chapter, name)
            },
        )
    }

    if (renamingCookbook) {
        NameDialog(
            title = "Rename cookbook",
            initial = cookbook.name,
            onDismiss = { renamingCookbook = false },
            onConfirm = { name ->
                renamingCookbook = false
                onRenameCookbook(name)
            },
        )
    }

    if (removingCookbook) {
        AlertDialog(
            onDismissRequest = { removingCookbook = false },
            title = { Text("Delete ${cookbook.name}?") },
            // Deleting a shelf is not deleting the books on it, and people expect the
            // worst from a red button, so the dialog says which one this is.
            text = {
                Text(
                    "The ${cookbook.recipeCount} recipes in it move to Unfiled. " +
                        "Nothing is deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removingCookbook = false
                    onDeleteCookbook()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removingCookbook = false }) { Text("Keep it") }
            },
        )
    }

    removing?.let { chapter ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${chapter.name}?") },
            // Worth stating outright, because deleting a heading looks like it should
            // take what is under it.
            text = { Text("The recipes in it stay in ${cookbook.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteChapter(chapter)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChapterHeading(
    chapter: Chapter,
    count: Int,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapter.name.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (count == 0) "empty" else "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        Row {
            TextButton(onClick = onRename) {
                Text("Rename", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onRemove) {
                Text("Remove", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

package app.cuisson.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuisson.data.Contribution
import app.cuisson.data.ShoppingItem
import app.cuisson.data.ShoppingListSummary
import app.cuisson.data.ShoppingListView
import app.cuisson.data.ShoppingRepository
import app.cuisson.domain.RecipeId
import app.cuisson.text.Aisle
import app.cuisson.text.IngredientCatalogue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The Shopping tab: opens on the list used last, with the others one tap away.
 *
 * Holds the state and does the writing, so [ShoppingScreen] can stay a plain drawing of
 * whatever list it is handed.
 */
@Composable
fun ShoppingTab(
    shopping: ShoppingRepository,
    language: String,
    modifier: Modifier,
    onOpenRecipe: (RecipeId) -> Unit,
) {
    val lists by remember { shopping.observeLists() }.collectAsStateWithLifecycle(emptyList())
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val listId = chosen?.takeIf { id -> lists.any { it.id == id && !it.archived } }
        ?: lists.firstOrNull { !it.archived }?.id
    val view by remember(listId, language) {
        listId?.let { shopping.observeList(it, language) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)

    fun write(block: ShoppingRepository.() -> Unit) {
        Cuisson.background.launch { shopping.block() }
    }
    fun now() = System.currentTimeMillis()

    ShoppingScreen(
        modifier = modifier,
        lists = lists,
        view = view?.takeIf { it.summary.id == listId },
        language = language,
        onChoose = { id ->
            chosen = id
            write { touchList(id, now()) }
        },
        onCreate = { name ->
            val id = UUID.randomUUID().toString()
            chosen = id
            write { createList(id, name, now()) }
        },
        onRename = { name -> listId?.let { id -> write { renameList(id, name) } } },
        onArchive = { listId?.let { id -> write { archiveList(id, now()) } } },
        onRevive = { id ->
            chosen = id
            write { reviveList(id, now()) }
        },
        onDelete = { id -> write { deleteList(id) } },
        onOpenRecipe = onOpenRecipe,
        onServings = { recipe, servings ->
            listId?.let { id -> write { setServings(id, recipe, servings) } }
        },
        onRemoveRecipe = { recipe -> listId?.let { id -> write { removeRecipe(id, recipe) } } },
        onTick = { item, ticked -> listId?.let { id -> write { setTicked(id, item.key, ticked) } } },
        onAddOwn = { text ->
            listId?.let { id -> write { addOwnLine(id, UUID.randomUUID().toString(), text, now()) } }
        },
        onRemoveOwn = { ownId -> listId?.let { id -> write { removeOwnLine(id, ownId) } } },
        onSetAmount = { item, amount -> listId?.let { id -> write { setAmount(id, item, amount) } } },
        onSameAs = { item, target -> listId?.let { id -> write { sameAs(id, item, target, now()) } } },
        onMoveTo = { item, aisle -> listId?.let { id -> write { moveTo(id, item, aisle) } } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    modifier: Modifier,
    lists: List<ShoppingListSummary>,
    view: ShoppingListView?,
    language: String,
    onChoose: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
    onRevive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenRecipe: (RecipeId) -> Unit,
    onServings: (RecipeId, Double) -> Unit,
    onRemoveRecipe: (RecipeId) -> Unit,
    onTick: (ShoppingItem, Boolean) -> Unit,
    onAddOwn: (String) -> Unit,
    onRemoveOwn: (String) -> Unit,
    onSetAmount: (ShoppingItem, String) -> Unit,
    onSameAs: (ShoppingItem, String) -> Unit,
    onMoveTo: (ShoppingItem, Aisle) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ShoppingListSummary?>(null) }
    var hideTicked by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var amountFor by remember { mutableStateOf<ShoppingItem?>(null) }
    var sameAsFor by remember { mutableStateOf<ShoppingItem?>(null) }
    var aisleFor by remember { mutableStateOf<ShoppingItem?>(null) }
    var ownText by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(20.dp))

            if (lists.none { !it.archived }) {
                Text("Shopping", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No shopping list yet. Make one here, then add recipes to it " +
                        "from the recipe itself.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { naming = true }, shape = RoundedCornerShape(9.dp)) {
                    Text("New list")
                }
                if (lists.any { it.archived }) {
                    TextButton(onClick = { choosing = true }) { Text("Archived lists") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { choosing = true },
                    ) {
                        Text(
                            text = view?.summary?.name ?: "",
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 2,
                        )
                        Text(
                            text = "Your lists",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = { hideTicked = !hideTicked }) {
                        Text(if (hideTicked) "Show all" else "Hide what I've got")
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (view != null) {
                    ListBody(
                        view = view,
                        hideTicked = hideTicked,
                        expanded = expanded,
                        ownText = ownText,
                        onOwnTextChange = { ownText = it },
                        onAddOwn = {
                            onAddOwn(ownText)
                            ownText = ""
                        },
                        onExpand = { key -> expanded = if (expanded == key) null else key },
                        onOpenRecipe = onOpenRecipe,
                        onServings = onServings,
                        onRemoveRecipe = onRemoveRecipe,
                        onTick = onTick,
                        onRemoveOwn = onRemoveOwn,
                        onAmount = { amountFor = it },
                        onSameAs = { sameAsFor = it },
                        onAisle = { aisleFor = it },
                        onRename = { renaming = true },
                        onArchive = onArchive,
                        onDelete = { deleting = view.summary },
                    )
                }
            }
        }
    }

    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Your lists", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                lists.filter { !it.archived }.forEach { list ->
                    ListRow(list) {
                        onChoose(list.id)
                        choosing = false
                    }
                }
                TextButton(onClick = { naming = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("New list")
                }
                val archived = lists.filter { it.archived }
                if (archived.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Label("Archived")
                    archived.forEach { list ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = list.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onRevive(list.id)
                                choosing = false
                            }) { Text("Bring back") }
                            TextButton(onClick = { deleting = list }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (naming) {
        NameDialog(
            title = "New list",
            onDismiss = { naming = false },
            onConfirm = { name ->
                naming = false
                choosing = false
                onCreate(name)
            },
        )
    }

    if (renaming && view != null) {
        NameDialog(
            title = "Rename list",
            initial = view.summary.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                onRename(name)
            },
        )
    }

    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${list.name}?") },
            text = { Text("The list goes. The recipes on it stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(list.id)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } },
        )
    }

    amountFor?.let { item ->
        AmountDialog(
            item = item,
            onDismiss = { amountFor = null },
            onSave = { amount ->
                onSetAmount(item, amount)
                amountFor = null
            },
        )
    }

    sameAsFor?.let { item ->
        SameAsDialog(
            item = item,
            others = view?.groups?.flatMap { it.items }?.filter { it.key != item.key } ?: emptyList(),
            language = language,
            onDismiss = { sameAsFor = null },
            onPick = { target ->
                onSameAs(item, target)
                sameAsFor = null
                expanded = null
            },
        )
    }

    aisleFor?.let { item ->
        AlertDialog(
            onDismissRequest = { aisleFor = null },
            title = { Text("Which aisle?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Aisle.entries.forEach { aisle ->
                        Text(
                            text = aisle.label(language),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (aisle == item.aisle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveTo(item, aisle)
                                    aisleFor = null
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { aisleFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ListBody(
    view: ShoppingListView,
    hideTicked: Boolean,
    expanded: String?,
    ownText: String,
    onOwnTextChange: (String) -> Unit,
    onAddOwn: () -> Unit,
    onExpand: (String) -> Unit,
    onOpenRecipe: (RecipeId) -> Unit,
    onServings: (RecipeId, Double) -> Unit,
    onRemoveRecipe: (RecipeId) -> Unit,
    onTick: (ShoppingItem, Boolean) -> Unit,
    onRemoveOwn: (String) -> Unit,
    onAmount: (ShoppingItem) -> Unit,
    onSameAs: (ShoppingItem) -> Unit,
    onAisle: (ShoppingItem) -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    LazyColumn {
        item {
            Label("Recipes on this list")
            if (view.recipes.isEmpty()) {
                Text(
                    text = "None yet. Open a recipe and use Add to list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            view.recipes.forEach { recipe ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clickable { onOpenRecipe(recipe.recipeId) }
                                .padding(vertical = 4.dp),
                        )
                        val servings = recipe.servings ?: recipe.writtenFor
                        if (servings != null && recipe.writtenFor != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onServings(recipe.recipeId, servings - 1) },
                                    enabled = servings > 1,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                Text(
                                    text = "${count(servings)} servings",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                TextButton(
                                    onClick = { onServings(recipe.recipeId, servings + 1) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                    TextButton(onClick = { onRemoveRecipe(recipe.recipeId) }) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ownText,
                    onValueChange = onOwnTextChange,
                    placeholder = { Text("Add something yourself") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddOwn, enabled = ownText.isNotBlank()) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
        }

        view.groups.forEach { group ->
            val shown = group.items.filter { !(hideTicked && it.ticked) }
            if (shown.isEmpty()) return@forEach
            item(key = "aisle-${group.aisle}") {
                Spacer(Modifier.height(16.dp))
                Label(group.label)
            }
            shown.forEach { item ->
                item(key = item.key) {
                    ItemRow(
                        item = item,
                        expanded = expanded == item.key,
                        onExpand = { onExpand(item.key) },
                        onTick = { onTick(item, it) },
                        onOpenRecipe = onOpenRecipe,
                        onRemoveOwn = onRemoveOwn,
                        onAmount = { onAmount(item) },
                        onSameAs = { onSameAs(item) },
                        onAisle = { onAisle(item) },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Row {
                TextButton(onClick = onRename) { Text("Rename") }
                TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * One thing to buy.
 *
 * The box ticks, the rest of the row opens the working: which recipe asked for how much,
 * as that recipe wrote it. That is where a wrong merge becomes visible, and where it gets
 * put right.
 */
@Composable
private fun ItemRow(
    item: ShoppingItem,
    expanded: Boolean,
    onExpand: () -> Unit,
    onTick: (Boolean) -> Unit,
    onOpenRecipe: (RecipeId) -> Unit,
    onRemoveOwn: (String) -> Unit,
    onAmount: () -> Unit,
    onSameAs: () -> Unit,
    onAisle: () -> Unit,
) {
    val faded = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onExpand),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.ticked, onCheckedChange = onTick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.ticked) TextDecoration.LineThrough else null,
                color = if (item.ticked) faded else MaterialTheme.colorScheme.onSurface,
            )
            if (item.amount.isNotEmpty()) {
                Text(
                    text = item.amount + if (item.setByHand) " · set by you" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = faded,
                    textDecoration = if (item.ticked) TextDecoration.LineThrough else null,
                )
            }
        }
    }
    if (expanded) {
        Column(modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)) {
            item.contributions.forEach { contribution ->
                when (contribution) {
                    is Contribution.FromRecipe -> Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = contribution.title +
                                (contribution.servings?.let { " · ${count(it)} servings" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenRecipe(contribution.recipeId) },
                        )
                        Text(contribution.line, style = MaterialTheme.typography.bodyMedium)
                    }
                    is Contribution.FromYou -> Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "You",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(contribution.line, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { onRemoveOwn(contribution.ownId) }) { Text("Remove") }
                    }
                }
            }
            if (item.setByHand && item.computed.isNotEmpty()) {
                Text(
                    text = "The recipes add up to ${item.computed}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = faded,
                )
            }
            Row {
                TextButton(onClick = onAmount, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Amount")
                }
                TextButton(onClick = onSameAs, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Same as…")
                }
                TextButton(onClick = onAisle, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Aisle…")
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Clearing the field puts the amount back to what the recipes add up to. */
@Composable
private fun AmountDialog(item: ShoppingItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(item.key) { mutableStateOf(item.amount) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(item.computed.ifEmpty { "Amount" }) },
                )
                if (item.computed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The recipes add up to ${item.computed}. Your figure stays " +
                            "until something changes this item.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = { onSave("") }, enabled = item.setByHand) { Text("Automatic") }
        },
    )
}

/**
 * "This is the same as…" Offers the other things on this list first, since that is where a
 * duplicate is noticed, then anything Cuisson knows about.
 */
@Composable
private fun SameAsDialog(
    item: ShoppingItem,
    others: List<ShoppingItem>,
    language: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val onList = others.filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
    val known = if (needle.length < 2) emptyList() else IngredientCatalogue.all
        .filter { it.id != item.key && others.none { other -> other.key == it.id } }
        .filter {
            it.name(language).lowercase().contains(needle) || it.english.lowercase().contains(needle)
        }
        .take(20)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.name} is the same as") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search") },
                )
                Spacer(Modifier.height(8.dp))
                onList.forEach { other ->
                    PickRow(other.name) { onPick(other.key) }
                }
                known.forEach { ingredient ->
                    PickRow(ingredient.name(language).replaceFirstChar { it.uppercase() }) {
                        onPick(ingredient.id)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun ListRow(list: ShoppingListSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(list.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = when (list.recipeCount) {
                0 -> "no recipes"
                1 -> "1 recipe"
                else -> "${list.recipeCount} recipes"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

/**
 * Putting a recipe on a list, from the recipe. Every current list, plus a new one, at the
 * servings on screen.
 */
@Composable
fun AddToListDialog(
    servings: Double?,
    lists: List<ShoppingListSummary>,
    onPick: (ShoppingListSummary) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to a list") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                servings?.let {
                    Text(
                        text = "At ${count(it)} servings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                lists.filter { !it.archived }.forEach { list -> ListRow(list) { onPick(list) } }
                if (naming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            placeholder = { Text("Name") },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                            Text("Create")
                        }
                    }
                } else {
                    TextButton(onClick = { naming = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("New list")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun count(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

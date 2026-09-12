package app.cuisson.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cuisson.domain.Recipe
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(recipe: Recipe, onFinish: (cooked: Boolean) -> Unit) {
    // Held awake only while this screen is open. A recipe you are reading on the sofa has
    // no business keeping the screen on.
    val view = LocalView.current
    LaunchedEffect(Unit) { view.keepScreenOn = true }

    val cards = remember(recipe) { cookCardsFor(recipe) }
    val pager = rememberPagerState { cards.size }
    var showIngredients by remember { mutableStateOf(false) }
    val ticked = remember { mutableStateMapOf<String, Boolean>() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding().fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onFinish(false) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Close", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "STEP ${cards.getOrNull(pager.currentPage)?.stepNumber ?: 1}" +
                        " OF ${recipe.steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { showIngredients = true },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Ingredients", style = MaterialTheme.typography.labelLarge)
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                CookCardView(cards[page])
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(cards.size, pager.currentPage, Modifier.weight(1f))
                if (pager.currentPage == cards.lastIndex) {
                    Button(
                        onClick = { onFinish(true) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text("I cooked it", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showIngredients) {
        ModalBottomSheet(onDismissRequest = { showIngredients = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Ingredients", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                recipe.ingredients.forEach { line ->
                    val done = ticked[line.id] == true
                    line.groupLabel?.takeIf { label ->
                        recipe.ingredients.first { it.groupLabel == label }.id == line.id
                    }?.let { label ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = line.rawText,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ticked[line.id] = !done }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CookCardView(card: CookCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        if (!card.isWholeStep) {
            Text(
                text = "PART ${card.partOfStep} OF ${card.partsInStep}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = card.text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 23.sp, lineHeight = 34.sp),
        )
        card.durationSeconds?.let {
            Spacer(Modifier.height(22.dp))
            Timer(it)
        }
        card.notes.forEach { note ->
            Spacer(Modifier.height(18.dp))
            NoteCard(note)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The author's note, against the instruction that points at it.
 *
 * This is the only place a Source Note is shown without being asked for, because here it
 * is the answer to a question the step just raised.
 */
@Composable
private fun NoteCard(note: app.cuisson.domain.SourceNote) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = "NOTE ${note.label.orEmpty()}".trim(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(note.text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Counts down in the app while it is open. It does not yet survive leaving the app, which
 * needs a foreground service and is still to come.
 */
@Composable
private fun Timer(seconds: Int) {
    var remaining by remember(seconds) { mutableStateOf(seconds) }
    var running by remember(seconds) { mutableStateOf(false) }

    LaunchedEffect(running, remaining) {
        if (running && remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "%d:%02d".format(remaining / 60, remaining % 60),
            style = MaterialTheme.typography.displaySmall,
            color = if (remaining == 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(0.dp))
        TextButton(onClick = {
            if (remaining == 0) remaining = seconds else running = !running
        }) {
            Text(
                text = when {
                    remaining == 0 -> "Again"
                    running -> "Pause"
                    else -> "Start"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count.coerceAtMost(14)) { index ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index == current.coerceAtMost(13)) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

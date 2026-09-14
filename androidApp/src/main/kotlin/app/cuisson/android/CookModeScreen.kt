package app.cuisson.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cuisson.domain.Recipe
import app.cuisson.text.scaleIngredient
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(
    recipe: Recipe,
    factor: Double = 1.0,
    onFinish: (cooked: Boolean) -> Unit,
) {
    // Held awake only while this screen is open. A recipe you are reading on the sofa has
    // no business keeping the screen on.
    val view = LocalView.current
    LaunchedEffect(Unit) { view.keepScreenOn = true }

    val cards = remember(recipe) { cookCardsFor(recipe) }
    val pager = rememberPagerState { cards.size }
    var showIngredients by remember { mutableStateOf(false) }
    val ticked = remember { mutableStateMapOf<String, Boolean>() }

    val timers = runningTimers()
    val mine = remember(timers, cards) {
        val ids = cards.map { it.timerId }.toSet()
        timers.filter { it.id in ids }
    }
    // Not the one on the card in front of you. Showing it twice, the same number in two
    // sizes, reads as two timers rather than one.
    val elsewhere = mine.filterNot { it.id == cards.getOrNull(pager.currentPage)?.timerId }

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

            // Timers set on a step you have already walked past. Without this the only
            // way to find one is to page back looking for it, which at the hob is
            // exactly when you have no attention to spare.
            if (elsewhere.isNotEmpty()) {
                RunningStrip(
                    timers = elsewhere,
                    onOpen = { id ->
                        cards.indexOfFirst { it.timerId == id }
                            .takeIf { it >= 0 }
                            ?.let { pager.requestScrollToPage(it) }
                    },
                )
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val card = cards[page]
                CookCardView(
                    card = card,
                    label = "${recipe.title}, step ${card.stepNumber}",
                    running = mine.firstOrNull { it.id == card.timerId },
                )
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
                        // The same scale the recipe was being read at a moment ago.
                        text = scaleIngredient(line.text, factor),
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

/**
 * Every timer currently running, re-read once a second.
 *
 * Polling rather than observing, because the truth is a moment written to disk that
 * another process may have changed. A second of lag on a screen that is already awake
 * costs nothing, and this way the display cannot drift away from what will actually
 * happen.
 */
@Composable
private fun runningTimers(): List<KitchenTimer.Running> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    return remember(tick) { KitchenTimer.all(context) }
}

@Composable
private fun RunningStrip(timers: List<KitchenTimer.Running>, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        timers.take(3).forEach { timer ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onOpen(timer.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = clock(remainingOf(timer)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CookCardView(card: CookCard, label: String, running: KitchenTimer.Running?) {
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
        card.durationSeconds?.let { seconds ->
            Spacer(Modifier.height(22.dp))
            Timer(id = card.timerId, seconds = seconds, label = label, running = running)
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
 * A timer that outlives the screen it was started from.
 *
 * Nothing counts down here. [running] is the moment the alarm will fire, so the number on
 * screen is derived from the clock and is right however long the app was away.
 */
@Composable
private fun Timer(id: String, seconds: Int, label: String, running: KitchenTimer.Running?) {
    val context = LocalContext.current
    var lateWarning by remember { mutableStateOf(false) }

    // Asked for at the moment a timer is started rather than at launch, because that is
    // the first point at which a notification is something the user wants.
    val askForNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = clock(running?.let(::remainingOf) ?: seconds),
                style = MaterialTheme.typography.displaySmall,
                color = if (running != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = {
                if (running != null) {
                    KitchenTimer.cancel(context, id)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    lateWarning = !KitchenTimer.start(context, id, label, seconds)
                }
            }) {
                Text(
                    text = if (running != null) "Stop" else "Start",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Only after a timer has actually been set inexactly. Warning about it in advance
        // would be asking for a permission before there is anything to spend it on.
        if (lateWarning) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Android may hold this back by a few minutes. Allow exact alarms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        )
                    }
                },
            )
        }
    }
}

private fun remainingOf(timer: KitchenTimer.Running): Int =
    ((timer.endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()

/**
 * A range gives its lower end deliberately, so "30 to 40 minutes" starts at thirty, which
 * is when you check. An hour is written out in full rather than as sixty minutes.
 */
private fun clock(seconds: Int): String = if (seconds >= 3600) {
    "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
} else {
    "%d:%02d".format(seconds / 60, seconds % 60)
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

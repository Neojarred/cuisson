package app.cuisson.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.cuisson.domain.DraftRecipe
import app.cuisson.domain.toRecipe
import app.cuisson.importer.ImportOutcome
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Clock

/**
 * Where a shared link becomes a recipe.
 *
 * Registered for the Android share sheet, so browsing happens wherever the user already
 * browses and Cuisson never needs a browser of its own. That was decided in D2, and it is
 * also what keeps ADR-0005 true: the fetch happens here, on this device, because a person
 * pressed share on a page they were reading.
 */
class ImportActivity : ComponentActivity() {

    private var state by mutableStateOf<ImportState>(ImportState.AskingForUrl(""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = sharedTextFrom(intent)
        if (shared != null) start(shared) else state = ImportState.AskingForUrl("")

        setContent {
            CuissonTheme {
                ImportScreen(
                    state = state,
                    onUrlChanged = { state = ImportState.AskingForUrl(it) },
                    onSubmit = ::start,
                    onSave = ::save,
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun sharedTextFrom(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun start(input: String) {
        state = ImportState.Working(input)
        lifecycleScope.launch {
            state = when (val outcome = Cuisson.importPipeline.importUrl(input)) {
                is ImportOutcome.Ready -> ImportState.Reviewing(outcome.draft, clean = true)
                is ImportOutcome.NeedsWork -> ImportState.Reviewing(outcome.draft, clean = false)
                is ImportOutcome.Blocked -> ImportState.Refused(outcome.status, outcome.url)
                is ImportOutcome.Failed -> ImportState.Broke(outcome.reason)
                is ImportOutcome.NotAUrl -> ImportState.Broke(
                    "That does not look like a web address."
                )
            }
        }
    }

    private fun save(draft: DraftRecipe) {
        val recipe = draft.toRecipe(UUID.randomUUID().toString(), Clock.System.now())
        Cuisson.repository(this).save(recipe)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}

sealed interface ImportState {
    data class AskingForUrl(val input: String) : ImportState
    data class Working(val url: String) : ImportState
    data class Reviewing(val draft: DraftRecipe, val clean: Boolean) : ImportState

    /** The site refused us. Distinct from a failure because the advice differs. */
    data class Refused(val status: Int, val url: String) : ImportState
    data class Broke(val reason: String) : ImportState
}

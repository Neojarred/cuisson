package app.cuisson.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
    private val imageStore by lazy { ImageStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)

        setContent {
            CuissonTheme {
                ImportScreen(
                    state = state,
                    onUrlChanged = { state = ImportState.AskingForUrl(it) },
                    onSubmit = ::start,
                    onTypeInstead = { state = ImportState.TypingText("", "") },
                    onTypedChanged = { t, b -> state = ImportState.TypingText(t, b) },
                    onParseTyped = ::parseTyped,
                    onSave = ::save,
                    onCancel = { imageStore.clearStaging(); finish() },
                    onOpenSettings = ::openAppSettings,
                )
            }
        }
    }

    /**
     * Sharing a second link while this screen is open delivers a new intent to the
     * running activity rather than creating another one. Without this the app would sit
     * showing the previous recipe and appear to have ignored the share.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val shared = sharedTextFrom(intent)
        state = if (shared != null) ImportState.Working(shared) else ImportState.AskingForUrl("")
        if (shared != null) start(shared)
    }

    /**
     * Whether a failure happened before any server was reached.
     *
     * These two causes cannot be told apart from inside the app. The device may be
     * offline, or this app in particular may have been denied the network, which
     * GrapheneOS allows per app and enforces in the network stack: checkSelfPermission
     * still answers "granted" while every connection fails, and ConnectivityManager
     * reports no network at all because we are not allowed to see one.
     *
     * So the message names both possibilities instead of guessing at one. Being told the
     * wrong cause confidently is worse than being told two and shown where to look.
     */
    private fun failedBeforeReachingAnyServer(reason: String): Boolean = listOf(
        "UnknownHostException", "ConnectException", "SecurityException",
        "SocketException", "NoRouteToHost", "UnresolvedAddress",
    ).any { reason.contains(it, ignoreCase = true) }

    private fun sharedTextFrom(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun start(input: String) {
        state = ImportState.Working(input)
        lifecycleScope.launch {
            val outcome = Cuisson.importPipeline.importUrl(input)
            Log.i(IMPORT_LOG, describeForLog(input, outcome))
            // Every outcome that produces a Review goes through reviewOf, which is
            // what starts the image download. An earlier version built two of these
            // states inline and silently skipped it.
            state = when (outcome) {
                is ImportOutcome.Ready, is ImportOutcome.NeedsWork -> reviewOf(outcome)
                is ImportOutcome.Blocked -> ImportState.Refused(outcome.status, outcome.url)
                is ImportOutcome.Failed ->
                    if (failedBeforeReachingAnyServer(outcome.describe)) {
                        ImportState.NoNetworkPermission
                    } else {
                        ImportState.Broke(outcome.describe)
                    }
                // Not an address, so it is presumably the recipe itself: someone
                // selected text in another app and shared it here.
                is ImportOutcome.NotAUrl ->
                    reviewOf(Cuisson.importPipeline.fromText(outcome.input))
            }
        }
    }

    private fun reviewOf(outcome: ImportOutcome): ImportState {
        val review = when (outcome) {
            is ImportOutcome.Ready -> ImportState.Reviewing(outcome.draft, clean = true)
            is ImportOutcome.NeedsWork -> ImportState.Reviewing(outcome.draft, clean = false)
            else -> return ImportState.Broke("That could not be read as a recipe.")
        }
        fetchImageForReview(review.draft.imageUrl)
        return review
    }

    /**
     * Downloads the picture while the user is reading the Review.
     *
     * An import that lands with no photograph reads as one that failed, even when every
     * ingredient is right. So the image arrives before the decision rather than after it.
     * It goes to a staging file and is promoted only if the recipe is saved.
     */
    private fun fetchImageForReview(url: String?) {
        imageStore.clearStaging()
        if (url == null) return
        lifecycleScope.launch {
            val bytes = Cuisson.importPipeline.fetcher.fetchBytes(url) ?: return@launch
            val path = imageStore.writeStaging(bytes)
            (state as? ImportState.Reviewing)?.let { state = it.copy(imagePath = path) }
        }
    }

    fun parseTyped(title: String, body: String) {
        state = reviewOf(Cuisson.importPipeline.fromText(body, title.takeIf { it.isNotBlank() }))
    }

    /**
     * One line per import, so a run over many sites can be measured from the device
     * rather than from a desktop whose TLS fingerprint sites treat differently.
     */
    private fun describeForLog(input: String, outcome: ImportOutcome): String {
        val host = Regex("https?://([^/]+)").find(input)?.groupValues?.get(1) ?: input.take(40)
        return when (outcome) {
            is ImportOutcome.Ready -> "OK       $host ing=${outcome.draft.ingredientLines.size} " +
                "steps=${outcome.draft.steps.size} warn=${outcome.draft.warnings.size}"
            is ImportOutcome.NeedsWork -> "THIN     $host tier=${outcome.draft.tier} " +
                "ing=${outcome.draft.ingredientLines.size}"
            is ImportOutcome.Blocked -> "BLOCKED  $host status=${outcome.status}"
            is ImportOutcome.Failed -> "FAILED   $host ${outcome.describe.take(60)}"
            is ImportOutcome.NotAUrl -> "NOTAURL  $input"
        }
    }

    private fun save(draft: DraftRecipe) {
        val id = UUID.randomUUID().toString()
        val recipe = draft.toRecipe(id, Clock.System.now())
        val repository = Cuisson.repository(this)
        repository.save(recipe)

        // The picture was already downloaded for the Review, so saving is a rename.
        imageStore.promoteStaging(id)?.let { repository.setImagePath(recipe.id, it) }

        // Unless the download had not finished yet, in which case it continues on the
        // application scope. This screen is closing and would cancel anything tied to it.
        if (imageStore.pathFor(id) == null) {
            val url = draft.imageUrl
            val store = imageStore
            if (url != null) {
                Cuisson.background.launch {
                    val bytes = Cuisson.importPipeline.fetcher.fetchBytes(url)
                    if (bytes != null) {
                        repository.setImagePath(recipe.id, store.write(id, bytes))
                    }
                }
            }
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }
}

private const val IMPORT_LOG = "CuissonImport"

sealed interface ImportState {
    data class AskingForUrl(val input: String) : ImportState

    /** Typing or pasting the recipe itself, when there is no page to fetch. */
    data class TypingText(val title: String, val body: String) : ImportState
    data class Working(val url: String) : ImportState
    data class Reviewing(
        val draft: DraftRecipe,
        val clean: Boolean,
        val imagePath: String? = null,
    ) : ImportState

    /** The site refused us. Distinct from a failure because the advice differs. */
    data class Refused(val status: Int, val url: String) : ImportState
    data class Broke(val reason: String) : ImportState

    /** Only reachable where INTERNET is revocable, which in practice means GrapheneOS. */
    data object NoNetworkPermission : ImportState
}

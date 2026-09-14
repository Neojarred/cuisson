package app.cuisson.android

import android.content.Context
import app.cuisson.data.DatabaseDriverFactory
import app.cuisson.data.LibraryExport
import app.cuisson.data.RecipeRepository
import app.cuisson.data.ShoppingRepository
import app.cuisson.data.db.CuissonDatabase
import app.cuisson.importer.ImportPipeline
import app.cuisson.importer.RecipeFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The application's few long-lived objects.
 *
 * There is no dependency injection framework here on purpose. Nothing in this app talks
 * to a network service of ours or needs swapping out at runtime, so a couple of lazily
 * built singletons say what is going on more clearly than a graph would.
 */
object Cuisson {

    private var database: CuissonDatabase? = null

    fun repository(context: Context): RecipeRepository {
        val existing = database
        val db = existing ?: CuissonDatabase(
            DatabaseDriverFactory(context.applicationContext).create()
        ).also { database = it }
        val repository = RecipeRepository(db)
        // Here rather than in MainActivity, because a share on a fresh install reaches
        // ImportActivity first and would otherwise save a recipe with nowhere to live.
        if (existing == null) repository.ensureUnfiled()
        return repository
    }

    fun exports(context: Context): LibraryExport {
        val recipes = repository(context)
        return LibraryExport(database!!, recipes)
    }

    fun shopping(context: Context): ShoppingRepository {
        val recipes = repository(context)
        return ShoppingRepository(database!!, recipes)
    }

    val importPipeline: ImportPipeline by lazy {
        ImportPipeline(RecipeFetcher(HttpClient(OkHttp)))
    }

    /**
     * For work that must outlive the screen that started it.
     *
     * Downloading a recipe's picture belongs here rather than in an activity's scope: the
     * user presses Save and the screen closes immediately, and tying the download to that
     * screen cancels it the moment it starts.
     */
    val background: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

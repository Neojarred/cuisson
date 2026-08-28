package app.cuisson.android

import android.content.Context
import app.cuisson.data.DatabaseDriverFactory
import app.cuisson.data.RecipeRepository
import app.cuisson.data.db.CuissonDatabase
import app.cuisson.importer.ImportPipeline
import app.cuisson.importer.RecipeFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

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
        return RecipeRepository(db)
    }

    val importPipeline: ImportPipeline by lazy {
        ImportPipeline(RecipeFetcher(HttpClient(OkHttp)))
    }
}

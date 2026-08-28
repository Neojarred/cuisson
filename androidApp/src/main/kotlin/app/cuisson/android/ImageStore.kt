package app.cuisson.android

import android.content.Context
import java.io.File

/**
 * Where a recipe's picture lives.
 *
 * Images are copied into the app's own storage rather than fetched from the publisher
 * each time. Two reasons, and both are decisions rather than convenience: the app has to
 * work in a kitchen with no signal, and a page that is later edited or taken down should
 * not silently change the recipe you saved.
 *
 * Nothing in here is ever uploaded. See ADR-0007.
 */
class ImageStore(context: Context) {

    private val directory = File(context.filesDir, "images").apply { mkdirs() }

    fun write(recipeId: String, bytes: ByteArray): String {
        val file = File(directory, "$recipeId.img")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun delete(recipeId: String) {
        File(directory, "$recipeId.img").delete()
    }
}

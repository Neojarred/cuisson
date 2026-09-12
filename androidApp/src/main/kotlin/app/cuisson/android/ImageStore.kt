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

    /**
     * Holds a picture belonging to a recipe nobody has accepted yet.
     *
     * The Review downloads the image before the user decides, because an import that
     * shows no picture reads as one that went wrong even when every ingredient is
     * correct. Accepting the recipe promotes the file; discarding deletes it.
     */
    fun writeStaging(bytes: ByteArray): String = write(STAGING, bytes)

    fun promoteStaging(recipeId: String): String? {
        val staged = File(directory, "$STAGING.img")
        if (!staged.exists()) return null
        val destination = File(directory, "$recipeId.img")
        return if (staged.renameTo(destination)) destination.absolutePath else null
    }

    fun clearStaging() {
        File(directory, "$STAGING.img").delete()
    }

    private companion object {
        const val STAGING = "staging"
    }

    fun pathFor(recipeId: String): String? =
        File(directory, "$recipeId.img").takeIf { it.exists() }?.absolutePath

    fun delete(recipeId: String) {
        File(directory, "$recipeId.img").delete()
    }
}

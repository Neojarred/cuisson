package app.cuisson.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A recipe's picture, read off this device.
 *
 * Cuisson never displays an image from someone else's server, per ADR-0007, so there is
 * nothing to load over a network and no image library involved.
 *
 * Decoding, however, is not free. A list row showing a 64dp thumbnail was decoding the
 * full photograph on the main thread every time it scrolled back into view, which is
 * what made the library stutter. Bitmaps are now decoded off the main thread, scaled
 * down to roughly the size they are drawn at, and kept in a small cache.
 */
private const val CACHE_BYTES = 12 * 1024 * 1024

private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
fun LocalImage(path: String, modifier: Modifier = Modifier.fillMaxWidth().height(210.dp)) {
    // A rough target: thumbnails are small, the cover is full width. Decoding to within
    // a factor of two of the drawn size is enough and costs a fraction of the full read.
    val target = 512
    val key = "$path@$target"

    val bitmap by produceState<ImageBitmap?>(initialValue = null, key) {
        // Reset first. produceState keeps the previous value when its key changes, so a
        // recycled list row went on showing the photograph of the recipe that had just
        // scrolled away.
        value = cache[key]?.asImageBitmap()
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                decodeScaled(path, target)?.also { cache.put(key, it) }?.asImageBitmap()
            }
        }
    }

    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
    )
}

/**
 * Reads the file's dimensions first, then decodes at a power-of-two reduction, which is
 * the only sampling BitmapFactory does cheaply.
 */
private fun decodeScaled(path: String, target: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
        sample *= 2
    }
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

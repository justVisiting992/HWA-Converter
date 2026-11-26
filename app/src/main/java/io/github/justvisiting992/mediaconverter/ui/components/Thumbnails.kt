package io.github.justvisiting992.mediaconverter.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video thumbnails for job cards, from the platform's own frame
 * grabber — no image library, no new dependencies. Frames are decoded
 * off the main thread, downscaled to card size before caching, and
 * kept in a small in-memory cache (24 entries), so scrolling a long
 * queue costs nothing after the first pass.
 */
object Thumbnails {

    private const val MAX_WIDTH = 288

    private val cache = LruCache<String, Bitmap>(24)

    fun cached(uri: Uri): Bitmap? = cache.get(uri.toString())

    /** Load (or return the cached) poster frame for a video input. */
    suspend fun load(context: Context, uri: Uri): Bitmap? {
        cache.get(uri.toString())?.let { return it }
        val frame = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(
                        1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        } ?: return null
        val scaled = if (frame.width > MAX_WIDTH) {
            val height = (frame.height * (MAX_WIDTH.toFloat() / frame.width)).toInt()
            Bitmap.createScaledBitmap(frame, MAX_WIDTH, height.coerceAtLeast(1), true)
        } else {
            frame
        }
        cache.put(uri.toString(), scaled)
        return scaled
    }
}

// PORT-MODE: Native Feature
// HOST-WIRING:
//   - Bridges Java-side call sites (ImageLoader.loadAsync, PhotoViewer slot loader)
//     into Coil's SingletonImageLoader so the post body / GroupedPhotosListView strip /
//     PhotoViewer fullscreen share a single decoded-bitmap cache.
package io.github.baba983.nativedo.telegram.messenger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.transition.CrossfadeDrawable

object CoilBridge {

    fun interface Callback {
        fun onLoaded(bitmap: Bitmap?)
    }

    @JvmStatic
    fun bitmapFromMemoryCache(context: Context, url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return runCatching {
            val loader = SingletonImageLoader.get(context)
            val cache = loader.memoryCache ?: return null
            val value = cache[MemoryCache.Key(url)] ?: return null
            (value.image as? BitmapImage)?.bitmap
                ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        }.getOrNull()
    }

    @JvmStatic
    fun loadAsync(context: Context, url: String, callback: Callback) {
        val appContext = context.applicationContext
        val loader = SingletonImageLoader.get(appContext)
        val request = ImageRequest.Builder(appContext)
            .data(url)
            .memoryCacheKey(url)
            .allowHardware(false)
            .listener(object : ImageRequest.Listener {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    callback.onLoaded(extractBitmap(appContext, result))
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    callback.onLoaded(null)
                }

                override fun onCancel(request: ImageRequest) {
                    callback.onLoaded(null)
                }
            })
            .build()
        loader.enqueue(request)
    }

    private fun extractBitmap(context: Context, result: SuccessResult): Bitmap? {
        val image = result.image
        if (image is BitmapImage) {
            val bitmap = image.bitmap
            if (!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                return bitmap
            }
        }
        val drawable: Drawable = image.asDrawable(context.resources)
        return extractBitmap(drawable)
    }

    private fun extractBitmap(drawable: Drawable?): Bitmap? = when (drawable) {
        null -> null
        is BitmapDrawable -> drawable.bitmap?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        is CrossfadeDrawable -> extractBitmap(drawable.end) ?: extractBitmap(drawable.start)
        else -> null
    }
}

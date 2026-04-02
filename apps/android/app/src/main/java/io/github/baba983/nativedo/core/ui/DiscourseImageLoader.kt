package io.github.baba983.nativedo.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.github.baba983.nativedo.core.storage.CookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val discourseImageClient = OkHttpClient()
private val discourseBitmapCache = MemoryLruCache<Bitmap>(maxEntries = 128)

@Composable
internal fun rememberDiscourseBitmap(
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): State<Bitmap?> {
    val cachedBitmap = remember(resolvedUrl) {
        discourseBitmapCache.get(resolvedUrl)
    }
    return produceState<Bitmap?>(initialValue = cachedBitmap, resolvedUrl, cookieStore, baseUrl) {
        if (resolvedUrl.isBlank()) {
            value = null
            return@produceState
        }

        value = discourseBitmapCache.get(resolvedUrl) ?: fetchDiscourseBitmap(
            resolvedUrl = resolvedUrl,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
        )
    }
}

internal suspend fun fetchDiscourseBitmap(
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): Bitmap? {
    discourseBitmapCache.get(resolvedUrl)?.let { cachedBitmap ->
        return cachedBitmap
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val imageUrl = resolvedUrl.toHttpUrl()
            val cookieHeader = cookieStore.load(imageUrl)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "; ") { cookie ->
                    "${cookie.name}=${cookie.value}"
                }
            val request = Request.Builder()
                .url(imageUrl)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Referer", baseUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
                )
                .apply {
                    if (!cookieHeader.isNullOrBlank()) {
                        header("Cookie", cookieHeader)
                    }
                }
                .build()

            discourseImageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching null
                }
                response.body?.bytes()
                    ?.let(::decodeDiscourseBitmapBytes)
                    ?.also { bitmap ->
                        discourseBitmapCache.put(resolvedUrl, bitmap)
                    }
            }
        }.getOrNull()
    }
}

internal fun extractLargestPngPayloadFromIco(bytes: ByteArray): ByteArray? {
    if (!bytes.isIcoContainer()) {
        return null
    }

    val iconCount = bytes.readLittleEndianUnsignedShort(offset = 4)
    var bestEntry: IcoPngEntry? = null
    for (index in 0 until iconCount) {
        val entryOffset = icoHeaderSize + (index * icoDirectoryEntrySize)
        if (entryOffset + icoDirectoryEntrySize > bytes.size) {
            return null
        }

        val width = bytes.icoDimensionAt(entryOffset)
        val height = bytes.icoDimensionAt(entryOffset + 1)
        val payloadSize = bytes.readLittleEndianInt(entryOffset + 8)
        val payloadOffset = bytes.readLittleEndianInt(entryOffset + 12)
        if (payloadSize <= 0 || payloadOffset < 0 || payloadOffset + payloadSize > bytes.size) {
            continue
        }
        if (!bytes.hasPngSignature(offset = payloadOffset)) {
            continue
        }

        val candidate = IcoPngEntry(
            width = width,
            height = height,
            payloadOffset = payloadOffset,
            payloadSize = payloadSize,
        )
        if (bestEntry == null || candidate.isBetterThan(bestEntry)) {
            bestEntry = candidate
        }
    }

    return bestEntry?.let { entry ->
        bytes.copyOfRange(entry.payloadOffset, entry.payloadOffset + entry.payloadSize)
    }
}

private fun decodeDiscourseBitmapBytes(bytes: ByteArray): Bitmap? {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: extractLargestPngPayloadFromIco(bytes)
            ?.let { payload ->
                BitmapFactory.decodeByteArray(payload, 0, payload.size)
            }
}

private fun ByteArray.isIcoContainer(): Boolean {
    return size >= icoHeaderSize &&
        readLittleEndianUnsignedShort(offset = 0) == 0 &&
        readLittleEndianUnsignedShort(offset = 2) == 1 &&
        readLittleEndianUnsignedShort(offset = 4) > 0
}

private fun ByteArray.icoDimensionAt(offset: Int): Int {
    return getOrNull(offset)?.toInt()?.and(0xff)?.let { value ->
        if (value == 0) {
            256
        } else {
            value
        }
    } ?: 0
}

private fun ByteArray.readLittleEndianUnsignedShort(offset: Int): Int {
    if (offset + 2 > size) {
        return 0
    }
    return (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8)
}

private fun ByteArray.readLittleEndianInt(offset: Int): Int {
    if (offset + 4 > size) {
        return -1
    }
    return (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)
}

private fun ByteArray.hasPngSignature(offset: Int): Boolean {
    if (offset < 0 || offset + pngSignature.size > size) {
        return false
    }
    return pngSignature.indices.all { index ->
        this[offset + index] == pngSignature[index]
    }
}

private data class IcoPngEntry(
    val width: Int,
    val height: Int,
    val payloadOffset: Int,
    val payloadSize: Int,
) {
    fun isBetterThan(other: IcoPngEntry): Boolean {
        val thisArea = width * height
        val otherArea = other.width * other.height
        return thisArea > otherArea ||
            (thisArea == otherArea && payloadSize > other.payloadSize)
    }
}

private const val icoHeaderSize = 6
private const val icoDirectoryEntrySize = 16
private val pngSignature = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4e,
    0x47,
    0x0d,
    0x0a,
    0x1a,
    0x0a,
)

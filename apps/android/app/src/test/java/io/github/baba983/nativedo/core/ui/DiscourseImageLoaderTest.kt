package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

class DiscourseImageLoaderTest {
    @Test
    fun extractLargestPngPayloadFromIco_returnsLargestPngEntry() {
        val smallPng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFAAH/e+m+7wAAAABJRU5ErkJggg==",
        )
        val largePng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWP8z8Dwn4GBgYGJAQoAHxcCAr7c4eUAAAAASUVORK5CYII=",
        )

        val icoBytes = buildIco(
            IcoEntry(width = 16, height = 16, payload = smallPng),
            IcoEntry(width = 32, height = 32, payload = largePng),
        )

        val extracted = extractLargestPngPayloadFromIco(icoBytes)

        assertThat(extracted).isEqualTo(largePng)
    }

    @Test
    fun extractLargestPngPayloadFromIco_returnsNullForNonIcoBytes() {
        val pngBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFAAH/e+m+7wAAAABJRU5ErkJggg==",
        )

        val extracted = extractLargestPngPayloadFromIco(pngBytes)

        assertThat(extracted).isNull()
    }
}

private data class IcoEntry(
    val width: Int,
    val height: Int,
    val payload: ByteArray,
)

private fun buildIco(vararg entries: IcoEntry): ByteArray {
    val headerSize = 6 + entries.size * 16
    val output = ArrayList<Byte>(headerSize + entries.sumOf { it.payload.size })

    fun writeShort(value: Int) {
        output += (value and 0xff).toByte()
        output += ((value ushr 8) and 0xff).toByte()
    }

    fun writeInt(value: Int) {
        output += (value and 0xff).toByte()
        output += ((value ushr 8) and 0xff).toByte()
        output += ((value ushr 16) and 0xff).toByte()
        output += ((value ushr 24) and 0xff).toByte()
    }

    writeShort(0)
    writeShort(1)
    writeShort(entries.size)

    var offset = headerSize
    entries.forEach { entry ->
        output += entry.width.toIcoDimensionByte()
        output += entry.height.toIcoDimensionByte()
        output += 0
        output += 0
        writeShort(1)
        writeShort(32)
        writeInt(entry.payload.size)
        writeInt(offset)
        offset += entry.payload.size
    }

    entries.forEach { entry ->
        entry.payload.forEach(output::add)
    }

    return output.toByteArray()
}

private fun Int.toIcoDimensionByte(): Byte {
    return if (this >= 256) {
        0
    } else {
        toByte()
    }
}

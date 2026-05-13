package io.github.baba983.nativedo.core.ui

import java.util.Locale
import kotlin.math.roundToInt

fun compactCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> compactCountLabel(
        value = count / 1_000.0,
        suffix = "K",
        fractionDigits = 1,
    )
    count < 1_000_000 -> compactCountLabel(
        value = count / 1_000.0,
        suffix = "K",
        fractionDigits = 0,
    ).replace("1000K", "1M")
    count < 10_000_000 -> compactCountLabel(
        value = count / 1_000_000.0,
        suffix = "M",
        fractionDigits = 1,
    )
    else -> compactCountLabel(
        value = count / 1_000_000.0,
        suffix = "M",
        fractionDigits = 0,
    )
}

private fun compactCountLabel(
    value: Double,
    suffix: String,
    fractionDigits: Int,
): String {
    val roundedValue = when (fractionDigits) {
        0 -> value.roundToInt().toString()
        else -> {
            val rounded = (value * 10).roundToInt() / 10.0
            if (rounded % 1.0 == 0.0) {
                rounded.roundToInt().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
        }
    }
    return "$roundedValue$suffix"
}

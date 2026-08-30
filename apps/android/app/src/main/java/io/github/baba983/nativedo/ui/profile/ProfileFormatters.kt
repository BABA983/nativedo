/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Shared formatters for Discourse profile/account values used by the
 * profile header and the account-details settings screen.
 */
package io.github.baba983.nativedo.ui.profile

import android.text.format.DateUtils

internal fun profileFormatTimeRead(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun profileFormatRelativeActivity(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        DateUtils.getRelativeTimeSpanString(
            java.time.Instant.parse(isoDate).toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    } catch (_: Exception) {
        profileFormatDate(isoDate)
    }
}

internal fun profileFormatDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.parse(isoDate))
    } catch (_: Exception) {
        isoDate.take(10)
    }
}

package io.github.baba983.nativedo.core.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URISyntaxException

internal fun shouldTryOpenInExternalApp(
    baseUrl: String,
    url: String,
): Boolean {
    val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return false
    val resolvedUrl = baseHttpUrl.resolve(url) ?: url.toHttpUrlOrNull() ?: return false
    return !resolvedUrl.host.equals(baseHttpUrl.host, ignoreCase = true)
}

internal fun Context.openInExternalApp(url: String): Boolean {
    val intent = buildOpenLinkIntent(url) ?: return false
    val scheme = intent.data?.scheme?.lowercase().orEmpty()

    if (scheme !in webSchemes) {
        return startActivitySafely(intent)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return startActivitySafely(
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER),
        )
    }

    val packageName = selectNonBrowserPackageName(
        handlerPackageNames = packageManager.queryIntentActivitiesCompat(intent)
            .map { resolveInfo -> resolveInfo.activityInfo.packageName },
        browserPackageNames = browserPackageNames(packageManager),
    ) ?: return false

    return startActivitySafely(
        Intent(intent).setPackage(packageName),
    )
}

internal fun Context.openInSystemBrowser(url: String): Boolean {
    val intent = buildOpenLinkIntent(url) ?: return false
    val browserPackageName = selectBrowserPackageName(
        defaultPackageName = packageManager.resolveActivityCompat(intent)?.activityInfo?.packageName,
        browserPackageNames = browserPackageNames(packageManager),
    )
    val browserIntent = if (browserPackageName != null) {
        Intent(intent).setPackage(browserPackageName)
    } else {
        intent
    }
    return startActivitySafely(browserIntent)
}

internal fun selectNonBrowserPackageName(
    handlerPackageNames: List<String>,
    browserPackageNames: Set<String>,
): String? {
    return handlerPackageNames.firstOrNull { packageName ->
        packageName !in browserPackageNames
    }
}

internal fun selectBrowserPackageName(
    defaultPackageName: String?,
    browserPackageNames: Set<String>,
): String? {
    return when {
        defaultPackageName != null && defaultPackageName in browserPackageNames -> defaultPackageName
        else -> browserPackageNames.firstOrNull()
    }
}

private fun Context.startActivitySafely(intent: Intent): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun buildOpenLinkIntent(url: String): Intent? {
    val intent = try {
        if (url.startsWith(intentSchemePrefix, ignoreCase = true)) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
    } catch (_: URISyntaxException) {
        return null
    }

    return intent.apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
}

private fun browserPackageNames(
    packageManager: PackageManager,
): Set<String> {
    return webSchemes.flatMap { scheme ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://linux.do")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        packageManager.queryIntentActivitiesCompat(intent)
            .map { resolveInfo -> resolveInfo.activityInfo.packageName }
    }.toSet()
}

@Suppress("DEPRECATION")
private fun PackageManager.queryIntentActivitiesCompat(
    intent: Intent,
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    queryIntentActivities(
        intent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
    )
} else {
    queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
}

@Suppress("DEPRECATION")
private fun PackageManager.resolveActivityCompat(
    intent: Intent,
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    resolveActivity(
        intent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
    )
} else {
    resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
}

private val webSchemes = setOf("http", "https")
private const val intentSchemePrefix = "intent://"

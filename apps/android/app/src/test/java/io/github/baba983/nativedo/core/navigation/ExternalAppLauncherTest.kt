package io.github.baba983.nativedo.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExternalAppLauncherTest {

    @Test
    fun selectNonBrowserPackageName_returnsFirstNonBrowserHandler() {
        val result = selectNonBrowserPackageName(
            handlerPackageNames = listOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "org.telegram.messenger",
            ),
            browserPackageNames = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
            ),
        )

        assertThat(result).isEqualTo("org.telegram.messenger")
    }

    @Test
    fun selectNonBrowserPackageName_onlyBrowsers_returnsNull() {
        val result = selectNonBrowserPackageName(
            handlerPackageNames = listOf(
                "com.android.chrome",
                "org.mozilla.firefox",
            ),
            browserPackageNames = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
            ),
        )

        assertThat(result).isNull()
    }

    @Test
    fun selectBrowserPackageName_prefersDefaultBrowser() {
        val result = selectBrowserPackageName(
            defaultPackageName = "org.mozilla.firefox",
            browserPackageNames = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
            ),
        )

        assertThat(result).isEqualTo("org.mozilla.firefox")
    }

    @Test
    fun selectBrowserPackageName_fallsBackToKnownBrowser() {
        val result = selectBrowserPackageName(
            defaultPackageName = "org.telegram.messenger",
            browserPackageNames = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
            ),
        )

        assertThat(result).isEqualTo("com.android.chrome")
    }
}

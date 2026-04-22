package io.github.baba983.nativedo

import android.content.Context
import android.content.Intent
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.actionbar.HelloFragment

internal enum class MainRootHostMode {
    LegacyCompose,
    Rewrite,
}

internal const val RewriteRootIntentExtra: String = "io.github.baba983.nativedo.extra.REWRITE_ROOT"

internal object MainRootHostSwitch {
    @Volatile
    private var overrideModeForTesting: MainRootHostMode? = null

    fun currentMode(intent: Intent? = null): MainRootHostMode {
        val overrideMode = overrideModeForTesting
        if (overrideMode != null) {
            return overrideMode
        }
        if (intent?.getBooleanExtra(RewriteRootIntentExtra, false) == true) {
            return MainRootHostMode.Rewrite
        }
        return MainRootHostMode.LegacyCompose
    }

    fun setOverrideModeForTesting(mode: MainRootHostMode?) {
        overrideModeForTesting = mode
    }
}

internal fun resolveMainRootHostMode(intent: Intent? = null): MainRootHostMode {
    return MainRootHostSwitch.currentMode(intent)
}

internal fun createRewriteRoot(context: Context): ActionBarLayout {
    return ActionBarLayout(context).apply {
        presentFragment(
            fragment = HelloFragment(),
            animated = false,
        )
    }
}

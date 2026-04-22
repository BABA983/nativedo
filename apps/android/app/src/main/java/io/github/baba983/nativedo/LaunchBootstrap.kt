package io.github.baba983.nativedo

import android.content.Intent
import android.os.Bundle
import io.github.baba983.nativedo.core.util.FileLog
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.actionbar.BaseFragment
import io.github.baba983.nativedo.ui.actionbar.HelloFragment

class LaunchBootstrap {
    fun attach(
        rootLayout: ActionBarLayout,
        launchIntent: Intent?,
    ): Boolean {
        if (rootLayout.getFragmentStack().isNotEmpty()) {
            return false
        }
        FileLog.d("$LogTag: Attach startup fragment. action=${launchIntent?.action ?: "null"}")
        return rootLayout.presentFragment(
            fragment = createInitialFragment(launchIntent),
            animated = false,
        )
    }

    internal fun createInitialFragment(launchIntent: Intent?): BaseFragment {
        if (launchIntent?.action != null) {
            FileLog.d("$LogTag: Initial launch intent action=${launchIntent.action}")
        }
        return HelloFragment()
    }

    internal fun restoreFragment(
        navigationKey: String,
        arguments: Bundle?,
    ): BaseFragment? {
        return when (navigationKey) {
            HelloFragment.NavigationKey -> HelloFragment.restore(arguments)
            else -> null
        }
    }

    private companion object {
        const val LogTag: String = "LaunchBootstrap"
    }
}

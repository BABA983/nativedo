/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Shared scaffolding for NativeDo settings screens built on the vendored
 * org.telegram.ui.Components.UniversalFragment list: session-scoped
 * coroutines, the app container, and the session-state collection that
 * SettingsFragment and AccountDetailsFragment both need.
 */
package io.github.baba983.nativedo.ui.settings

import android.content.Context
import android.view.View
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalFragment

abstract class SessionSettingsFragment : UniversalFragment() {
    protected var fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private set
    protected val viewCollectors = mutableListOf<Job>()
    protected lateinit var appContainer: AppContainer

    override fun createView(context: Context): View {
        appContainer = (context.applicationContext as NativeDoApp).appContainer
        val view = super.createView(context)
        listView.setSections()
        actionBar.setAdaptiveBackground(listView)
        listView.clipToPadding = false
        return view
    }

    override fun clearViews() {
        viewCollectors.forEach(Job::cancel)
        viewCollectors.clear()
        fragmentScope.cancel()
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        super.clearViews()
    }

    /** True once [createView] has bound the app container. */
    protected fun isSessionContainerReady(): Boolean = this::appContainer.isInitialized

    protected fun string(resId: Int): String = context?.getString(resId).orEmpty()
}

internal fun profileVersionFooterReplacesTerminalShadow(lastViewType: Int?): Boolean =
    lastViewType == UniversalAdapter.VIEW_TYPE_SHADOW

package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class BaseFragment {
    var parentLayout: ActionBarLayout? = null
        internal set

    var actionBar: ActionBar? = null
        protected set

    val pageScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var fragmentView: View? = null
    private var lifecycleCreated: Boolean = false

    abstract fun createView(context: Context): View

    open fun onFragmentCreate(): Boolean = true

    open fun onFragmentDestroy() {
        pageScope.cancel()
    }

    open fun onResume() = Unit

    open fun onPause() = Unit

    open fun onBackPressed(): Boolean = false

    open fun isSwipeBackEnabled(): Boolean = true

    open fun onSlideProgress(progress: Float) = Unit

    open fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) = Unit

    open fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) = Unit

    open fun onBecomeFullyVisible() = Unit

    open fun getNavigationBarColor(): Int? = null

    open fun hasForceLightStatusBar(): Boolean = false

    protected open fun onCreateActionBar(context: Context): ActionBar? = null

    internal fun ensureView(context: Context): View? {
        if (fragmentView != null) {
            return fragmentView
        }
        if (!lifecycleCreated) {
            lifecycleCreated = onFragmentCreate()
        }
        if (!lifecycleCreated) {
            return null
        }
        actionBar = onCreateActionBar(context)?.also { createdActionBar ->
            createdActionBar.setOnBackClickListener {
                parentLayout?.closeLastFragment(animated = true)
            }
        }
        val contentView = createView(context)
        fragmentView = wrapWithActionBar(
            context = context,
            contentView = contentView,
            actionBar = actionBar,
        )
        return fragmentView
    }

    internal fun destroyFragment() {
        onFragmentDestroy()
        parentLayout = null
        actionBar = null
        fragmentView = null
    }

    private fun wrapWithActionBar(
        context: Context,
        contentView: View,
        actionBar: ActionBar?,
    ): View {
        if (actionBar == null) {
            return contentView
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                actionBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                contentView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
    }
}

package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ActionBarLayout(
    context: Context,
) : FrameLayout(context), INavigationLayout {
    private val fragmentHolders = mutableListOf<FragmentHolder>()
    private var delegate: INavigationLayoutDelegate? = null

    init {
        fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    override fun presentFragment(fragment: BaseFragment, animated: Boolean): Boolean {
        if (fragmentHolders.any { it.fragment === fragment }) {
            return false
        }
        fragment.parentLayout = this
        val fragmentView = fragment.ensureView(context) ?: return false
        val previousHolder = fragmentHolders.lastOrNull()
        previousHolder?.fragment?.onPause()
        previousHolder?.view?.visibility = VISIBLE

        val holder = FragmentHolder(fragment = fragment, view = fragmentView)
        fragmentHolders += holder
        addView(
            fragmentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        notifyStackChanged()

        if (previousHolder == null || !animated) {
            previousHolder?.view?.visibility = GONE
            fragment.onResume()
            fragment.onBecomeFullyVisible()
            return true
        }

        fragment.onTransitionAnimationStart(isOpen = true, backward = false)
        val startTranslation = width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        fragmentView.translationX = startTranslation
        previousHolder.view.translationX = 0f
        fragmentView.animate()
            .translationX(0f)
            .setDuration(220L)
            .withEndAction {
                previousHolder.view.visibility = GONE
                previousHolder.view.translationX = 0f
                fragment.onTransitionAnimationEnd(isOpen = true, backward = false)
                fragment.onResume()
                fragment.onBecomeFullyVisible()
            }
            .start()
        return true
    }

    override fun closeLastFragment(animated: Boolean): Boolean {
        if (fragmentHolders.size <= 1) {
            return false
        }
        val currentHolder = fragmentHolders.last()
        val previousHolder = fragmentHolders[fragmentHolders.lastIndex - 1]
        currentHolder.fragment.onPause()
        previousHolder.view.visibility = VISIBLE

        if (!animated) {
            finishClose(currentHolder, previousHolder)
            return true
        }

        currentHolder.fragment.onTransitionAnimationStart(isOpen = false, backward = true)
        val endTranslation = width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        currentHolder.view.animate()
            .translationX(endTranslation)
            .setDuration(220L)
            .withEndAction {
                currentHolder.fragment.onTransitionAnimationEnd(isOpen = false, backward = true)
                currentHolder.view.translationX = 0f
                finishClose(currentHolder, previousHolder)
            }
            .start()
        return true
    }

    override fun getFragmentStack(): List<BaseFragment> {
        return fragmentHolders.map { it.fragment }
    }

    override fun addFragmentToStack(fragment: BaseFragment, position: Int): Boolean {
        if (position !in 0..fragmentHolders.size || fragmentHolders.any { it.fragment === fragment }) {
            return false
        }
        if (position == fragmentHolders.size) {
            return presentFragment(fragment, animated = false)
        }
        fragment.parentLayout = this
        val fragmentView = fragment.ensureView(context) ?: return false
        val holder = FragmentHolder(fragment = fragment, view = fragmentView)
        fragmentHolders.add(position, holder)
        addView(
            fragmentView,
            position,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        fragmentView.visibility = GONE
        notifyStackChanged()
        return true
    }

    override fun removeFragmentFromStack(fragment: BaseFragment): Boolean {
        val holderIndex = fragmentHolders.indexOfFirst { it.fragment === fragment }
        if (holderIndex == -1) {
            return false
        }
        if (holderIndex == fragmentHolders.lastIndex) {
            return closeLastFragment(animated = false)
        }
        val holder = fragmentHolders.removeAt(holderIndex)
        removeView(holder.view)
        holder.fragment.destroyFragment()
        notifyStackChanged()
        return true
    }

    override fun setDelegate(delegate: INavigationLayoutDelegate?) {
        this.delegate = delegate
    }

    fun onBackPressed(): Boolean {
        val topFragment = fragmentHolders.lastOrNull()?.fragment ?: return false
        if (topFragment.onBackPressed()) {
            return true
        }
        return closeLastFragment(animated = true)
    }

    private fun finishClose(
        currentHolder: FragmentHolder,
        previousHolder: FragmentHolder,
    ) {
        fragmentHolders.remove(currentHolder)
        removeView(currentHolder.view)
        currentHolder.fragment.destroyFragment()
        previousHolder.view.visibility = VISIBLE
        previousHolder.fragment.onResume()
        previousHolder.fragment.onBecomeFullyVisible()
        notifyStackChanged()
    }

    private fun notifyStackChanged() {
        delegate?.onFragmentStackChanged(getFragmentStack())
    }

    private data class FragmentHolder(
        val fragment: BaseFragment,
        val view: View,
    )

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        ).toInt()
    }
}

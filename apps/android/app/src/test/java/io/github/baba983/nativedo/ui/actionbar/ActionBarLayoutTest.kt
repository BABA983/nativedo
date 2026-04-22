package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class ActionBarLayoutTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun presentFragment_addsFragmentToStackAndResumesIt() {
        val layout = ActionBarLayout(context)
        val fragment = RecordingFragment("root")

        layout.onResume()
        val result = layout.presentFragment(fragment, animated = false)

        assertThat(result).isTrue()
        assertThat(layout.getFragmentStack()).containsExactly(fragment)
        assertThat(fragment.events).containsExactly("create", "resume").inOrder()
        assertThat(layout.getChildCount()).isEqualTo(1)
    }

    @Test
    fun presentFragment_pausesPreviousAndCloseLastFragment_resumesItAgain() {
        val layout = ActionBarLayout(context)
        val first = RecordingFragment("first")
        val second = RecordingFragment("second")

        layout.onResume()
        layout.presentFragment(first, animated = false)
        layout.presentFragment(second, animated = false)
        val closeResult = layout.closeLastFragment(animated = false)

        assertThat(closeResult).isTrue()
        assertThat(layout.getFragmentStack()).containsExactly(first)
        assertThat(first.events).containsExactly("create", "resume", "pause", "resume").inOrder()
        assertThat(second.events).containsExactly("create", "resume", "pause", "destroy").inOrder()
        assertThat(second.pageScope.coroutineContext[Job]?.isCancelled).isTrue()
    }

    @Test
    fun onPauseAndOnResume_replaysTopFragmentLifecycleWithoutDuplicateResume() {
        val layout = ActionBarLayout(context)
        val fragment = RecordingFragment("root")

        layout.onResume()
        layout.presentFragment(fragment, animated = false)
        layout.onPause()
        layout.onResume()

        assertThat(fragment.events).containsExactly("create", "resume", "pause", "resume").inOrder()
    }

    @Test
    fun saveNavigationState_restoresFragmentStackInOrder() {
        val layout = ActionBarLayout(context)
        val first = RestorableFragment("first")
        val second = RestorableFragment("second")

        layout.onResume()
        layout.presentFragment(first, animated = false)
        layout.presentFragment(second, animated = false)

        val savedState = layout.saveNavigationState()
        val restoredLayout = ActionBarLayout(context)
        restoredLayout.onResume()
        val restored = restoredLayout.restoreNavigationState(savedState!!) { navigationKey, arguments ->
            if (navigationKey == RestorableFragment.NavigationKey) {
                RestorableFragment(arguments?.getString(RestorableFragment.LabelArgumentKey).orEmpty())
            } else {
                null
            }
        }

        val restoredFragments = restoredLayout.getFragmentStack().filterIsInstance<RestorableFragment>()

        assertThat(restored).isTrue()
        assertThat(restoredFragments.map { it.label }).containsExactly("first", "second").inOrder()
        assertThat(restoredFragments.last().events).containsExactly("create", "resume").inOrder()
    }

    @Test
    fun closeLastFragmentAnimated_switchesVisibleFragmentToPreviousBeforeRemoval() {
        val layout = createMeasuredLayout()
        val delegate = RecordingDelegate()
        val first = RecordingFragment("first")
        val second = RecordingFragment("second")

        layout.setDelegate(delegate)
        layout.onResume()
        layout.presentFragment(first, animated = false)
        layout.presentFragment(second, animated = false)
        delegate.visibleFragments.clear()

        val closed = layout.closeLastFragment(animated = true)

        assertThat(closed).isTrue()
        assertThat(delegate.visibleFragments).containsExactly(first)

        idleAnimations()

        assertThat(layout.getFragmentStack()).containsExactly(first)
    }

    @Test
    fun swipeBackCrossingThreshold_dismissesCurrentFragment() {
        val layout = createMeasuredLayout()
        val delegate = RecordingDelegate()
        val first = RecordingFragment("first")
        val second = RecordingFragment("second")

        layout.setDelegate(delegate)
        layout.onResume()
        layout.presentFragment(first, animated = false)
        layout.presentFragment(second, animated = false)
        delegate.visibleFragments.clear()

        val gesture = startSwipe(layout, downX = 8f, firstMoveX = 64f)
        continueSwipe(layout, gesture.downTime, moveX = 520f)
        endSwipe(layout, gesture.downTime, upX = 520f)
        idleAnimations()

        assertThat(layout.getFragmentStack()).containsExactly(first)
        assertThat(delegate.visibleFragments).containsExactly(first)
    }

    @Test
    fun swipeBackBelowThreshold_restoresCurrentFragmentAndVisibleOwner() {
        val layout = createMeasuredLayout()
        val delegate = RecordingDelegate()
        val first = RecordingFragment("first")
        val second = RecordingFragment("second")

        layout.setDelegate(delegate)
        layout.onResume()
        layout.presentFragment(first, animated = false)
        layout.presentFragment(second, animated = false)
        delegate.visibleFragments.clear()

        val gesture = startSwipe(layout, downX = 8f, firstMoveX = 64f)
        continueSwipe(layout, gesture.downTime, moveX = 180f)

        val previousView = layout.getChildAt(0)
        val currentView = layout.getChildAt(1)
        assertThat(previousView.translationX).isLessThan(0f)
        assertThat(currentView.translationX).isGreaterThan(0f)

        cancelSwipe(layout, gesture.downTime, cancelX = 180f)
        idleAnimations()

        assertThat(layout.getFragmentStack()).containsExactly(first, second).inOrder()
        assertThat(delegate.visibleFragments).containsExactly(first, second).inOrder()
        assertThat(previousView.visibility).isEqualTo(View.GONE)
        assertThat(currentView.translationX).isEqualTo(0f)
    }

    private fun createMeasuredLayout(): ActionBarLayout {
        return ActionBarLayout(context).apply {
            measure(
                MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY),
            )
            layout(0, 0, 1080, 1920)
        }
    }

    private fun startSwipe(
        layout: ActionBarLayout,
        downX: Float,
        firstMoveX: Float,
    ): SwipeGesture {
        val downTime = System.currentTimeMillis()
        val down = motionEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            x = downX,
        )
        val firstMove = motionEvent(
            downTime = downTime,
            eventTime = downTime + 16,
            action = MotionEvent.ACTION_MOVE,
            x = firstMoveX,
        )
        layout.onInterceptTouchEvent(down)
        layout.onInterceptTouchEvent(firstMove)
        down.recycle()
        firstMove.recycle()
        return SwipeGesture(downTime)
    }

    private fun continueSwipe(
        layout: ActionBarLayout,
        downTime: Long,
        moveX: Float,
    ) {
        val move = motionEvent(
            downTime = downTime,
            eventTime = downTime + 32,
            action = MotionEvent.ACTION_MOVE,
            x = moveX,
        )
        layout.onTouchEvent(move)
        move.recycle()
    }

    private fun endSwipe(
        layout: ActionBarLayout,
        downTime: Long,
        upX: Float,
    ) {
        val up = motionEvent(
            downTime = downTime,
            eventTime = downTime + 48,
            action = MotionEvent.ACTION_UP,
            x = upX,
        )
        layout.onTouchEvent(up)
        up.recycle()
    }

    private fun cancelSwipe(
        layout: ActionBarLayout,
        downTime: Long,
        cancelX: Float,
    ) {
        val cancel = motionEvent(
            downTime = downTime,
            eventTime = downTime + 48,
            action = MotionEvent.ACTION_CANCEL,
            x = cancelX,
        )
        layout.onTouchEvent(cancel)
        cancel.recycle()
    }

    private fun motionEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
    ): MotionEvent {
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            x,
            16f,
            0,
        )
    }

    private fun idleAnimations() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
    }

    private open class RecordingFragment(
        private val label: String,
    ) : BaseFragment() {
        val events = mutableListOf<String>()

        override fun onFragmentCreate(): Boolean {
            events += "create"
            return true
        }

        override fun onResume() {
            events += "resume"
        }

        override fun onPause() {
            events += "pause"
        }

        override fun onFragmentDestroy() {
            events += "destroy"
            super.onFragmentDestroy()
        }

        override fun createView(context: Context): View {
            return TextView(context).apply {
                text = label
            }
        }
    }

    private class RestorableFragment(
        val label: String,
    ) : RecordingFragment(label) {
        companion object {
            const val NavigationKey: String = "recording"
            const val LabelArgumentKey: String = "label"
        }

        override fun getNavigationKey(): String = NavigationKey

        override fun saveArguments(): Bundle {
            return Bundle().apply {
                putString(LabelArgumentKey, label)
            }
        }
    }

    private class RecordingDelegate : INavigationLayoutDelegate {
        val visibleFragments = mutableListOf<BaseFragment?>()

        override fun onVisibleFragmentChanged(fragment: BaseFragment?) {
            visibleFragments += fragment
        }
    }

    private data class SwipeGesture(
        val downTime: Long,
    )
}

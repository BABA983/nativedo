package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.view.View
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActionBarLayoutTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun presentFragment_addsFragmentToStackAndResumesIt() {
        val layout = ActionBarLayout(context)
        val fragment = RecordingFragment("root")

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

        layout.presentFragment(first, animated = false)
        layout.presentFragment(second, animated = false)
        val closeResult = layout.closeLastFragment(animated = false)

        assertThat(closeResult).isTrue()
        assertThat(layout.getFragmentStack()).containsExactly(first)
        assertThat(first.events).containsExactly("create", "resume", "pause", "resume").inOrder()
        assertThat(second.events).containsExactly("create", "resume", "pause", "destroy").inOrder()
        assertThat(second.pageScope.coroutineContext[Job]?.isCancelled).isTrue()
    }

    private class RecordingFragment(
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
}

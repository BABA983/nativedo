package org.telegram.ui.Components

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BulletinContainerContractTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ApplicationLoader.init(context)
    }

    @Test
    fun fragmentPrefersItsActualViewParentForBulletins() {
        val parent = FrameLayout(context)
        val fragmentView = View(context)
        parent.addView(fragmentView)
        val fragment = object : BaseFragment() {}
        fragment.setFragmentView(fragmentView)

        assertThat(fragment.bulletinLayoutContainer).isSameInstanceAs(parent)
    }

    @Test
    fun nestedContainerFindsBottomSheetStyleAncestorDelegate() {
        val parent = FrameLayout(context)
        val bulletinContainer = FrameLayout(context)
        parent.addView(bulletinContainer)
        Bulletin.addDelegate(parent, object : Bulletin.Delegate {
            override fun getTopOffset(tag: Int): Int = 24
        })

        val bulletin = BulletinFactory.of(bulletinContainer, null)
            .createInfoBulletin("Saved")
            .show(true)

        val layoutParams = bulletinContainer.getChildAt(0).layoutParams as FrameLayout.LayoutParams
        assertThat(layoutParams.gravity).isEqualTo(Gravity.TOP)
        assertThat(layoutParams.topMargin).isEqualTo(AndroidUtilities.dp(8f) + 24)

        bulletin.hide(false, 0)
        Bulletin.removeDelegate(parent)
    }
}

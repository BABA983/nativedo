package io.github.baba983.nativedo.ui.profile

import android.content.Context
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.actionbar.BaseFragment

class ProfileFragment : BaseFragment() {

    override fun createView(context: Context): View {
        actionBar.setTitle(context.getString(R.string.tab_profile))
        actionBar.setBackButtonDrawable(null)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    ProfileScreen()
                }
            }
        }
    }
}

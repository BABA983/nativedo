package org.telegram.ui

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

/**
 * Phase 0 contract tests for ArticleViewer public surface used by NativeDo.
 *
 * These are not full UI tests. They lock the small API surface :app and
 * topic body code depend on so ArticleViewer rebases cannot silently break:
 * - [ArticleViewer.getInstance] / [ArticleViewer.hasInstance]
 * - [ArticleViewer.DrawingText] construction
 * - [ArticleViewer.ErrorContainer] construction (login WebView error UI)
 * - [ArticleViewer.dispatchActivityResult] no-op when no file chooser owner
 *
 * Run after any ArticleViewer edit:
 * `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'org.telegram.ui.ArticleViewerContractTest'`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ArticleViewerContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        // Avoid Theme.init(): pulls media action drawables not on the unit-test classpath.
    }
    @Test
    fun getInstance_returnsSingleton() {
        val a = ArticleViewer.getInstance()
        val b = ArticleViewer.getInstance()
        assertThat(a).isNotNull()
        assertThat(b).isSameInstanceAs(a)
        assertThat(ArticleViewer.hasInstance()).isTrue()
    }

    @Test
    fun drawingText_canBeConstructedFromInstance() {
        val viewer = ArticleViewer.getInstance()
        val viaNoArg = viewer.DrawingText()
        val viaParent = viewer.DrawingText(viewer)
        assertThat(viaNoArg).isNotNull()
        assertThat(viaParent).isNotNull()
        assertThat(viaNoArg.parentViewer).isSameInstanceAs(viewer)
        assertThat(viaParent.parentViewer).isSameInstanceAs(viewer)
        assertThat(viaNoArg.textLayout).isNull()
        assertThat(viaNoArg.parentBlock).isNull()
        // Defaults used by TableBlock / table layout bridge
        assertThat(viaNoArg.x).isEqualTo(0)
        assertThat(viaNoArg.y).isEqualTo(0)
        assertThat(viaNoArg.row).isEqualTo(0)
    }

    @Test
    fun errorContainer_constructsWithExpectedChildren() {
        val context = RuntimeEnvironment.getApplication()
        val container = ArticleViewer.ErrorContainer(context)
        assertThat(container).isNotNull()
        assertThat(container.layout).isNotNull()
        assertThat(container.buttonView).isNotNull()
        // GONE until host shows an error (LoginWebViewFragment)
        assertThat(container.visibility).isEqualTo(android.view.View.GONE)
    }

    @Test
    fun dispatchActivityResult_returnsFalseWhenNoFileChooserOwner() {
        // No active file chooser: must not crash and must not claim the result.
        val handled = ArticleViewer.dispatchActivityResult(
            /* requestCode = */ -1,
            /* resultCode = */ 0,
            Intent(),
        )
        assertThat(handled).isFalse()
    }

    @Test
    fun nonSheetConstructor_hasNullSheet() {
        val viewer = ArticleViewer()
        assertThat(viewer.isSheet).isFalse()
        assertThat(viewer.sheet).isNull()
        assertThat(viewer.pagesStack).isNotNull()
        assertThat(viewer.pagesStack).isEmpty()
    }

    @Test
    fun getResources_exposesSharedPaintMaps() {
        val viewer = ArticleViewer.getInstance()
        val res = viewer.getResources()
        assertThat(res).isNotNull()
        // B4: pin-shaped Resources holds the paint SparseArrays (static fields alias these)
        assertThat(res.paragraphTextPaints).isNotNull()
        assertThat(res.tableTextPaints).isNotNull()
        assertThat(res.quoteTextPaints).isNotNull()
        assertThat(res.photoCaptionTextPaints).isNotNull()
        assertThat(res.isRichMessage).isFalse()
    }

    @Test
    fun resources_preserveIdentityAndConstructorModes() {
        val viewer = ArticleViewer.getInstance()
        assertThat(viewer.getResources()).isSameInstanceAs(viewer.getResources())
        assertThat(ArticleViewer.Resources().isRichMessage).isFalse()
        assertThat(ArticleViewer.Resources(true).isRichMessage).isTrue()
    }

    @Test
    fun resources_instancesOwnIndependentPaintMaps() {
        val first = ArticleViewer.Resources()
        val second = ArticleViewer.Resources()

        first.paragraphTextPaints.put(7, android.text.TextPaint())

        assertThat(first.paragraphTextPaints.size()).isEqualTo(1)
        assertThat(second.paragraphTextPaints.size()).isEqualTo(0)
    }

}

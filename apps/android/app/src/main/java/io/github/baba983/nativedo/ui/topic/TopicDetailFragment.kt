package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import io.github.baba983.nativedo.NativeDoApp
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.browser.Browser
import io.github.baba983.nativedo.core.ui.GalleryImage
import io.github.baba983.nativedo.core.ui.RoundedImageView
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.ui.TopicViewModel
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
// TODO(photoviewer): re-enable when telegram/ui/PhotoViewer can compile.
// import org.telegram.ui.PhotoViewer
// import org.telegram.ui.PhotoViewer.PhotoEntry
// import org.telegram.ui.PhotoViewer.PlaceProviderObject

class TopicDetailFragment(
    private val topicId: Long,
    private val initialTitle: String? = null,
) : BaseFragment() {
    private lateinit var viewModel: TopicViewModel
    // TODO(photoviewer): private var activePhotoViewer: PhotoViewer? = null

    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(initialTitle ?: "")
        val appContainer = (context.applicationContext as NativeDoApp).appContainer
        viewModel = ViewModelProvider(
            this,
            TopicViewModelFactory(
                repository = TopicRepository(appContainer.discourseApiClient),
                topicId = topicId,
            ),
        )[TopicViewModel::class.java]

        return FrameLayout(context).apply {
            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    var hiddenImageUrl by remember { mutableStateOf<String?>(null) }
                    val imageBoundsByUrl = remember { mutableMapOf<String, RectF>() }
                    val imageViewByUrl = remember { mutableMapOf<String, android.view.View>() }
                    TopicDetailScreen(
                        viewModel = viewModel,
                        topicId = topicId,
                        baseUrl = appContainer.baseUrl,
                        cookieStore = appContainer.cookieStore,
                        discourseApiClient = appContainer.discourseApiClient,
                        svgSpriteStore = appContainer.svgSpriteStore,
                        hiddenImageUrl = hiddenImageUrl,
                        onTitleChanged = { title ->
                            actionBar.setTitle(title)
                        },
                        onTopicClick = { nextTopicId ->
                            presentFragment(TopicDetailFragment(nextTopicId))
                        },
                        onOpenLink = { url ->
                            Browser.openUrl(context, url)
                        },
                        onImageBoundsChanged = { imageUrl, bounds ->
                            if (bounds == null) {
                                imageBoundsByUrl.remove(imageUrl)
                            } else {
                                imageBoundsByUrl[imageUrl] = RectF(bounds)
                            }
                        },
                        onImageViewChanged = { imageUrl, view ->
                            if (view == null) {
                                imageViewByUrl.remove(imageUrl)
                            } else {
                                imageViewByUrl[imageUrl] = view
                            }
                        },
                        onOpenImage = { url, galleryImages, sourceBoundsInWindow, thumb, sourceView ->
                            if (sourceBoundsInWindow != null) {
                                imageBoundsByUrl[url] = RectF(sourceBoundsInWindow)
                            }
                            openImageViewer(
                                url = url,
                                galleryImages = galleryImages,
                                initialThumb = thumb,
                                tappedSourceView = sourceView,
                                imageViewByUrl = imageViewByUrl,
                                sourceBoundsProvider = { imageUrl ->
                                    imageBoundsByUrl[imageUrl] ?: if (imageUrl == url) sourceBoundsInWindow else null
                                },
                                setHiddenImageUrl = { hiddenUrl ->
                                    hiddenImageUrl = hiddenUrl
                                },
                            )
                        },
                    )
                }
            }
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    override fun onBackPressed(): Boolean {
        // TODO(photoviewer): re-add activePhotoViewer?.handleBackPressed() guard.
        return super.onBackPressed()
    }

    private fun openImageViewer(
        url: String,
        galleryImages: List<GalleryImage>,
        initialThumb: Bitmap?,
        tappedSourceView: View?,
        imageViewByUrl: Map<String, android.view.View>,
        sourceBoundsProvider: (String) -> RectF?,
        setHiddenImageUrl: (String?) -> Unit,
    ) {
        // TODO(photoviewer): wire PhotoViewer.getInstance().openPhoto(...) once
        // telegram/ui/PhotoViewer compiles. Body intentionally a no-op so :app
        // can build while we backfill the telegram/ dependency graph.
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        val ignored = listOf<Any?>(
            url,
            galleryImages,
            initialThumb,
            tappedSourceView,
            imageViewByUrl,
            sourceBoundsProvider,
            setHiddenImageUrl,
            findHostActivity(fragmentView?.context ?: return),
            getPhotoViewerScaleView(),
            AndroidUtilities.dp(14f),
        )
        setHiddenImageUrl(null)
    }

    private fun findHostActivity(context: Context): android.app.Activity? {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @Suppress("unused")
    private fun sourceViewRectInWindow(sourceView: View): RectF {
        val location = IntArray(2)
        sourceView.getLocationInWindow(location)
        return RectF(
            location[0].toFloat(),
            location[1].toFloat(),
            location[0] + sourceView.width.toFloat(),
            location[1] + sourceView.height.toFloat(),
        )
    }

    @Suppress("unused")
    private fun sourceImageRectInWindow(sourceView: View?, sourceViewRect: RectF): RectF? {
        val imageView = sourceView as? ImageView ?: return null
        val bitmap = if (imageView is RoundedImageView) {
            imageView.bitmap
        } else {
            (imageView.drawable as? BitmapDrawable)?.bitmap
        } ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0 || imageView.width <= 0 || imageView.height <= 0) {
            return null
        }
        val scale = minOf(
            imageView.width.toFloat() / bitmap.width.toFloat(),
            imageView.height.toFloat() / bitmap.height.toFloat(),
        )
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = sourceViewRect.left + (imageView.width - drawnWidth) / 2f
        val top = sourceViewRect.top + (imageView.height - drawnHeight) / 2f
        return RectF(left, top, left + drawnWidth, top + drawnHeight)
    }

    // TODO(photoviewer): private fun buildPhotoEntries(...) returned List<PhotoEntry>.
    // Re-add when PhotoViewer.PhotoEntry is back on the classpath.

    override fun getNavigationBarColor(): Int {
        return Theme.getColor(Theme.key_windowBackgroundGray)
    }

    override fun isSupportEdgeToEdge(): Boolean {
        return true
    }
}

private class TopicViewModelFactory(
    private val repository: TopicRepository,
    private val topicId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return TopicViewModel(repository = repository, topicId = topicId) as T
    }
}

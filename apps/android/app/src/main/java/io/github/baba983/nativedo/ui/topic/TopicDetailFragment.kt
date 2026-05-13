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
import io.github.baba983.nativedo.core.AndroidUtilities
import io.github.baba983.nativedo.core.ui.GalleryImage
import io.github.baba983.nativedo.core.ui.RoundedImageView
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.ui.TopicViewModel
import io.github.baba983.nativedo.ui.actionbar.BackDrawable
import io.github.baba983.nativedo.ui.actionbar.BaseFragment
import io.github.baba983.nativedo.ui.actionbar.Theme
import io.github.baba983.nativedo.ui.browser.BrowserSheet
import io.github.baba983.nativedo.ui.photo.PhotoViewer
import io.github.baba983.nativedo.ui.photo.PhotoViewer.PhotoEntry
import io.github.baba983.nativedo.ui.photo.PhotoViewer.PlaceProviderObject

class TopicDetailFragment(
    private val topicId: Long,
    private val initialTitle: String? = null,
) : BaseFragment() {
    private lateinit var viewModel: TopicViewModel
    private var activePhotoViewer: PhotoViewer? = null

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
                            val sheet = BrowserSheet(this@TopicDetailFragment, url)
                            addSheet(sheet)
                            sheet.show()
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
        activePhotoViewer?.let { viewer ->
            if (viewer.handleBackPressed()) {
                return false
            }
        }
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
        val root = fragmentView as? android.view.ViewGroup ?: return
        if (activePhotoViewer != null) {
            return
        }
        val sourceRadius = AndroidUtilities.dp(14f).toFloat()
        // Telegram-shaped entry point: bind the singleton to this Activity, then call
        // openPhoto on the singleton. attachToDecor() is now folded into openPhoto.
        val activity = findHostActivity(root.context) ?: return
        PhotoViewer.setParentActivity(activity)
        val viewer = PhotoViewer.getInstance()
        viewer.setOnDismissListener {
            setHiddenImageUrl(null)
            activePhotoViewer = null
        }
        val entries = buildPhotoEntries(url, galleryImages)
        val currentIndex = entries.indexOfFirst { it.url == url }.coerceAtLeast(0)
        val opened = viewer.openPhoto(entries, currentIndex, object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(index: Int, entry: PhotoEntry, needPreview: Boolean, closing: Boolean): PlaceProviderObject? {
                // Prefer a real Android View over cached Compose bounds. When
                // closePhoto temporarily resets the host zoom (scale→1.0), the View's
                // getLocationInWindow returns un-zoomed coordinates — but the cached
                // imageBoundsByUrl still has zoomed coordinates from Compose's
                // onGloballyPositioned (which reported under the active zoom). Using
                // zoomed bounds as drawRegion with un-zoomed parentClipRect produces
                // the "shrink too small then snap back" artifact on non-tapped images.
                val sourceView = if (entry.url == url) tappedSourceView else imageViewByUrl[entry.url]
                val hasLiveView = sourceView != null && sourceView.width > 0 && sourceView.height > 0

                val liveSourceRect = if (hasLiveView) {
                    sourceViewRectInWindow(sourceView!!)
                } else {
                    null
                }
                val cachedSourceRect = sourceBoundsProvider(entry.url)
                val sourceViewRect = liveSourceRect ?: cachedSourceRect ?: return null
                val sourceImageRect = if (hasLiveView) {
                    sourceImageRectInWindow(sourceView, sourceViewRect) ?: sourceViewRect
                } else {
                    sourceViewRect
                }
                var receiverViewX = sourceViewRect.left
                var receiverViewY = sourceViewRect.top
                var receiverScale = 1f
                var sourceVisibleRect: RectF? = null
                val sourceImageReceiverHandle = if (hasLiveView && sourceView is RoundedImageView) {
                    val location = IntArray(2)
                    sourceView.getLocationInWindow(location)
                    receiverViewX = location[0].toFloat()
                    receiverViewY = location[1].toFloat()
                    val visible = Rect()
                    if (sourceView.getGlobalVisibleRect(visible)) {
                        sourceVisibleRect = RectF(visible)
                    }
                    sourceView.imageReceiver
                } else {
                    null
                }
                // Telegram parity (PhotoViewer.java:19533-19560): two roles must
                // be split.
                //   parentView  → clip target. The wrapper sits topMargin=
                //                 actionBarHeight BELOW the host ActionBar, so its
                //                 getLocationInWindow() correctly excludes the
                //                 ActionBar strip from the morph view's clip.
                //   parentScaleView → host zoom target. Must include the ActionBar
                //                 so the ActionBar visually follows the open/close
                //                 zoom along with the content. The natural choice
                //                 is the wrapper's parent (= ActionBarLayout's
                //                 LayoutContainer that holds both ActionBar and
                //                 wrapper as siblings).
                val photoViewerWrapper = getPhotoViewerScaleView()
                val photoViewerScaleHost = (photoViewerWrapper?.parent as? View) ?: photoViewerWrapper
                // Capture the clip boundary (parentView's visible area in window
                // coords) NOW, while the host zoom is temporarily reset to 1.0 by
                // closePhoto (see PhotoViewer.java:1502-1510). If we leave
                // parentClipRect null, setupAnimatingImageView reads
                // parentView.getLocationInWindow AFTER the zoom is restored, getting
                // zoomed coords that don't match the un-zoomed drawRegion/viewRect —
                // this caused two bugs: (1) ActionBar overlay (clipParentTop was
                // lower than drawRegion.top → clipTop=0), (2) over-shrink (clipBottom
                // was ~100px too large due to zoomed parentView bottom).
                val clipRect = if (photoViewerWrapper != null) {
                    val wrapperLoc = IntArray(2)
                    photoViewerWrapper.getLocationInWindow(wrapperLoc)
                    RectF(
                        wrapperLoc[0].toFloat(),
                        wrapperLoc[1].toFloat(),
                        (wrapperLoc[0] + photoViewerWrapper.width).toFloat(),
                        (wrapperLoc[1] + photoViewerWrapper.height).toFloat(),
                    )
                } else {
                    null
                }
                return PlaceProviderObject().apply {
                    parentView = photoViewerWrapper
                    parentClipRect = clipRect
                    parentScaleView = photoViewerScaleHost
                    viewRect = RectF(sourceViewRect)
                    imageDrawRect = RectF(sourceImageRect)
                    visibleRect = sourceVisibleRect?.let(::RectF)
                    viewX = receiverViewX
                    viewY = receiverViewY
                    scale = receiverScale
                    thumb = if (entry.url == url) initialThumb else null
                    radius = sourceRadius
                    this.index = index
                    // Wire the real Android View for any image that has one (not just
                    // the initially tapped image). sourceView enables synchronous
                    // setVisibility and lets PhotoViewer borrow the view's bitmap.
                    if (hasLiveView) {
                        this.sourceView = sourceView
                        if (sourceImageReceiverHandle != null) {
                            imageReceiver = sourceImageReceiverHandle
                        }
                    } else {
                        sourceImageReceiver = PhotoViewer.SourceImageReceiver { visible, _ ->
                            setHiddenImageUrl(if (visible) null else entry.url)
                        }
                    }
                }
            }
        })
        if (!opened) {
            setHiddenImageUrl(null)
            return
        }
        // attachToDecor() is now folded into PhotoViewer.openPhoto() to match
        // Telegram's API surface. No separate attach step needed.
        activePhotoViewer = viewer
    }

    private fun findHostActivity(context: Context): android.app.Activity? {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

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

    private fun buildPhotoEntries(url: String, galleryImages: List<GalleryImage>): List<PhotoEntry> {
        val entries = linkedMapOf<String, PhotoEntry>()
        galleryImages.forEach { image ->
            if (image.url.isNotEmpty()) {
                entries[image.url] = PhotoEntry(image.url, image.thumbUrl, image.widthPx, image.heightPx)
            }
        }
        if (url.isNotEmpty() && !entries.containsKey(url)) {
            entries[url] = PhotoEntry(url)
        }
        return entries.values.toList()
    }

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

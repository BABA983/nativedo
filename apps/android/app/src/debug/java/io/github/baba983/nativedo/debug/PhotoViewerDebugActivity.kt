package io.github.baba983.nativedo.debug

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCellDelegate
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.PhotoViewer
import java.io.File

class PhotoViewerDebugActivity : ComponentActivity() {
    private lateinit var hostFragment: BaseFragment
    private lateinit var sourceView: SourcePreviewView
    private lateinit var statusView: TextView
    private var lastStatus: String = "pending"
    private var lastPlaceSourceInWindow: RectF? = null
    private var lastTopicCellSourceInWindow: RectF? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtilities.init(this)

        val urls = intent.getStringExtra(EXTRA_URLS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: createDebugImageFiles()
        val index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, urls.lastIndex)
        val scenario = intent.getStringExtra(EXTRA_SCENARIO)

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xff101010.toInt())
            keepScreenOn = true
        }
        statusView = TextView(this).apply {
            setTextColor(0xffffffff.toInt())
            textSize = 14f
            setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(10f), AndroidUtilities.dp(16f), AndroidUtilities.dp(10f))
            text = "PhotoViewer debug: pending"
        }
        root.addView(
            statusView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        setContentView(root)

        hostFragment = object : BaseFragment() {}
        hostFragment.setFragmentView(root)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val viewer = PhotoViewer.getInstance()
                    if (viewer.isVisibleOrAnimating()) {
                        viewer.closePhoto(true, false)
                    } else {
                        finish()
                    }
                }
            },
        )

        if (scenario == SCENARIO_TOPIC_CELL_TAP) {
            setupTopicCellScenario(root, urls, index, intent.getLongExtra(EXTRA_CLOSE_AFTER_MS, 0L))
        } else {
            sourceView = SourcePreviewView(this).apply {
                setImageUrl(urls[index])
            }
            root.addView(
                sourceView,
                FrameLayout.LayoutParams(AndroidUtilities.dp(260f), AndroidUtilities.dp(180f), Gravity.CENTER),
            )
            root.post {
                openViewer(urls, index)
                runScenario(scenario)
                val closeAfterMs = intent.getLongExtra(EXTRA_CLOSE_AFTER_MS, 0L)
                if (closeAfterMs > 0L) {
                    root.postDelayed({ PhotoViewer.getInstance().closePhoto(true, false) }, closeAfterMs)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PhotoViewer.getInstance().onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AndroidUtilities.init(this)
        PhotoViewer.getInstance().onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        PhotoViewer.getInstance().destroyPhotoViewer()
        super.onDestroy()
    }

    private fun openViewer(urls: List<String>, index: Int) {
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(hostFragment)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                photoIndex: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject? {
                if (photoIndex != index) return null
                sourceView.ensureReceiverBounds()
                val receiver = if (needPreview) sourceView.imageReceiver else null
                val sourceBounds = sourceView.imageBoundsInWindow()
                if (needPreview) {
                    lastPlaceSourceInWindow = sourceView.imageHandoffRectInWindow()
                }
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = (sourceBounds.left - (receiver?.imageX ?: 0f)).toInt()
                    viewY = (sourceBounds.top - (receiver?.imageY ?: 0f)).toInt()
                    viewWidth = (receiver?.imageWidth ?: sourceBounds.width()).toInt()
                    viewHeight = (receiver?.imageHeight ?: sourceBounds.height()).toInt()
                    parentView = sourceView
                    imageReceiver = receiver
                    thumb = if (needPreview) sourceView.bitmapHolder() else null
                    radius = if (needPreview) sourceView.radius() else null
                }
            }

            override fun getThumbForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                photoIndex: Int,
            ): ImageReceiver.BitmapHolder? {
                return if (photoIndex == index) sourceView.bitmapHolder() else null
            }

            override fun onPhotoChanged(photoIndex: Int) {
                Log.d(TAG, "onPhotoChanged index=$photoIndex")
                setStatus("onPhotoChanged index=$photoIndex")
            }

            override fun onClose() {
                Log.d(TAG, "onClose")
                showStatus("onClose visible=${PhotoViewer.getInstance().isVisibleOrAnimating()} previous=$lastStatus")
            }
        }
        val items = urls.mapIndexed { itemIndex, url ->
            PhotoViewer.UrlPhotoItem(url, null, 1600, 1000, "Photo ${itemIndex + 1}")
        }
        val opened = viewer.openPhotoItems(items, index, provider)
        setStatus("opened=$opened index=${currentPhotoIndex()} visible=${viewer.isVisibleOrAnimating()}")
    }

    private fun setupTopicCellScenario(root: FrameLayout, urls: List<String>, index: Int, closeAfterMs: Long) {
        val topicUrls = urls.map { "thumb://0:$it" }
        val targetUrl = topicUrls[index]
        val cookedHtml = """
            <p><img src="$targetUrl" width="1600" height="1000" alt="Debug topic image"></p>
        """.trimIndent()
        val parsedBlocks = parseCookedPostBody(cookedHtml, DEBUG_BASE_URL)
        val cell = TopicPostCell(this).apply {
            setDelegate(object : TopicPostCellDelegate {
                override fun didPressImage(
                    post: Post,
                    url: String,
                    gallery: List<GalleryImage>,
                    sourceBoundsInWindow: RectF?,
                    hostView: View,
                    sourceBoundsProvider: (String) -> RectF?,
                    sourceImageReceiverProvider: (String) -> ImageReceiver?,
                    sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder?,
                    sourceRadiusProvider: (String) -> IntArray?,
                ) {
                    lastTopicCellSourceInWindow = sourceBoundsInWindow?.let(::RectF)
                    openViewerFromTopicCell(
                        post = post,
                        url = url,
                        galleryImages = gallery,
                        tappedSourceView = hostView,
                        sourceBoundsProvider = sourceBoundsProvider,
                        sourceImageReceiverProvider = sourceImageReceiverProvider,
                        sourceBitmapProvider = sourceBitmapProvider,
                        sourceRadiusProvider = sourceRadiusProvider,
                    )
                }
            })
            setPost(
                Post(
                    id = 1L,
                    postNumber = 1,
                    authorUsername = "debug",
                    authorDisplayName = "PhotoViewer Debug",
                    createdAt = "2026-06-06T00:00:00Z",
                    cookedHtml = cookedHtml,
                    imageUrls = topicUrls,
                ),
                null,
                null,
                true,
                true,
                1L,
                DEBUG_BASE_URL,
                emptyMap(),
                emptyMap(),
                0,
                emptyList(),
                parsedBlocks,
                PostBodyBlockState(),
                0,
                null,
            )
        }
        root.addView(
            cell,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                topMargin = AndroidUtilities.dp(64f)
            },
        )
        root.postDelayed({
            val dispatched = dispatchTopicCellImageTap(cell)
            cell.postDelayed({
                val start = viewerRect("startImageRect")
                val startAbs = start?.let(::RectF)?.apply {
                    val location = photoViewerContainerLocationInWindow()
                    offset(location[0].toFloat(), location[1].toFloat())
                }
                val text = "scenario=$SCENARIO_TOPIC_CELL_TAP dispatched=$dispatched currentIndex=${currentPhotoIndex()} source=${lastTopicCellSourceInWindow?.toShortString()} handoff=${lastPlaceSourceInWindow?.toShortString()} startAbs=${startAbs?.toShortString()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                Log.i(TAG, text)
                setStatus(text)
            }, 650L)
            if (closeAfterMs > 0L) {
                cell.postDelayed({ PhotoViewer.getInstance().closePhoto(true, false) }, closeAfterMs)
            }
        }, 700L)
    }

    private fun openViewerFromTopicCell(
        post: Post,
        url: String,
        galleryImages: List<GalleryImage>,
        tappedSourceView: View,
        sourceBoundsProvider: (String) -> RectF?,
        sourceImageReceiverProvider: (String) -> ImageReceiver?,
        sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder?,
        sourceRadiusProvider: (String) -> IntArray?,
    ) {
        val urls = galleryImages.map { it.url }.ifEmpty { listOf(url) }
        val sourceCell = tappedSourceView as? TopicPostCell
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(hostFragment)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                photoIndex: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject? {
                val imageUrl = urls.getOrNull(photoIndex) ?: return null
                val bounds = sourceBoundsProvider(imageUrl) ?: return null
                val imageReceiver = sourceImageReceiverProvider(imageUrl)
                if (needPreview) {
                    lastPlaceSourceInWindow = handoffRectInWindow(bounds, imageReceiver)
                }
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = (bounds.left - (imageReceiver?.imageX ?: 0f)).toInt()
                    viewY = (bounds.top - (imageReceiver?.imageY ?: 0f)).toInt()
                    viewWidth = (imageReceiver?.imageWidth ?: bounds.width()).toInt()
                    viewHeight = (imageReceiver?.imageHeight ?: bounds.height()).toInt()
                    parentView = tappedSourceView
                    this.imageReceiver = imageReceiver
                    thumb = if (needPreview) sourceBitmapProvider(imageUrl) else null
                    radius = if (needPreview) sourceRadiusProvider(imageUrl) else null
                }
            }

            override fun getThumbForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
            ): ImageReceiver.BitmapHolder? {
                val imageUrl = urls.getOrNull(index) ?: return null
                return sourceBitmapProvider(imageUrl)
            }

            override fun onClose() {
                sourceCell?.setHiddenImageUrl(null)
                Log.d(TAG, "onClose")
                showStatus("onClose visible=${PhotoViewer.getInstance().isVisibleOrAnimating()} previous=$lastStatus")
            }
        }
        val title = post.authorDisplayName.ifBlank { post.authorUsername }.ifBlank { "PhotoViewer Debug" }
        val subtitle = buildString {
            post.postNumber?.let { append('#').append(it) }
            if (post.authorUsername.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append('@').append(post.authorUsername)
            }
        }.ifBlank { null }
        val items = galleryImages.map { image ->
            PhotoViewer.UrlPhotoItem(image.url, image.thumbUrl, image.widthPx, image.heightPx, image.caption, title, subtitle)
        }.ifEmpty {
            listOf(PhotoViewer.UrlPhotoItem(url, null, 1600, 1000, null, title, subtitle))
        }
        val opened = viewer.openPhotoItems(items, items.indexOfFirst { it.url == url }.coerceAtLeast(0), provider)
        if (!opened) {
            sourceCell?.setHiddenImageUrl(null)
        }
        setStatus("topicCellOpened=$opened index=${currentPhotoIndex()} visible=${viewer.isVisibleOrAnimating()}")
    }

    private fun dispatchTopicCellImageTap(cell: TopicPostCell): Boolean {
        val bodyLeft = privateInt(cell, "bodyLeft")
        val bodyWidth = privateInt(cell, "bodyWidth")
        val blockTop = privateIntArray(cell, "blockTops").firstOrNull() ?: AndroidUtilities.dp(64f)
        val blockHeight = privateIntArray(cell, "blockHeights").firstOrNull() ?: AndroidUtilities.dp(160f)
        val x = bodyLeft + bodyWidth / 2f
        val y = blockTop + (blockHeight / 2f).coerceAtMost(AndroidUtilities.dp(120f).toFloat())
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, x, y, 0)
        val downHandled = cell.dispatchTouchEvent(down)
        val upHandled = cell.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        return downHandled || upHandled
    }

    private fun handoffRectInWindow(bounds: RectF, imageReceiver: ImageReceiver?): RectF {
        if (imageReceiver == null) return RectF(bounds)
        val drawRegion = imageReceiver.drawRegion
        val receiverLeft: Float
        val receiverTop: Float
        val receiverRight: Float
        val receiverBottom: Float
        if (!drawRegion.isEmpty) {
            receiverLeft = drawRegion.left
            receiverTop = drawRegion.top
            receiverRight = drawRegion.right
            receiverBottom = drawRegion.bottom
        } else {
            receiverLeft = imageReceiver.imageX
            receiverTop = imageReceiver.imageY
            receiverRight = imageReceiver.imageX2
            receiverBottom = imageReceiver.imageY2
        }
        val viewX = bounds.left - imageReceiver.imageX
        val viewY = bounds.top - imageReceiver.imageY
        return RectF(viewX + receiverLeft, viewY + receiverTop, viewX + receiverRight, viewY + receiverBottom)
    }

    private fun privateInt(target: Any, fieldName: String): Int {
        val field = target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.getInt(target)
    }

    private fun privateIntArray(target: Any, fieldName: String): IntArray {
        val field = target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.get(target) as IntArray
    }

    private fun runScenario(scenario: String?) {
        when (scenario) {
            SCENARIO_SWIPE_NEXT -> sourceView.postDelayed({
                val dispatched = dispatchSwipe(0.82f, 0.5f, 0.16f, 0.5f, 360L)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched currentIndex=${currentPhotoIndex()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 500L)
            }, 700L)
            SCENARIO_DRAG_DISMISS -> sourceView.postDelayed({
                val dispatched = dispatchSwipe(0.5f, 0.46f, 0.5f, 0.82f, 360L)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched currentIndex=${currentPhotoIndex()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 500L)
            }, 700L)
            SCENARIO_DOUBLE_TAP_ZOOM -> sourceView.postDelayed({
                val dispatched = dispatchDoubleTap(0.5f, 0.5f)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched scale=${viewerScale()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 650L)
            }, 700L)
            SCENARIO_PINCH_ZOOM -> sourceView.postDelayed({
                val dispatched = dispatchPinch(0.5f, 0.5f, 0.12f, 0.54f, 760L)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched scale=${viewerScale()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 650L)
            }, 700L)
            SCENARIO_HANDOFF_RECT -> sourceView.postDelayed({
                val source = sourceView.imageBoundsInWindow()
                val handoff = lastPlaceSourceInWindow
                val start = viewerRect("startImageRect")
                val startAbs = start?.let(::RectF)?.apply {
                    val location = photoViewerContainerLocationInWindow()
                    offset(location[0].toFloat(), location[1].toFloat())
                }
                val text = "scenario=$scenario source=${source.toShortString()} handoff=${handoff?.toShortString()} startAbs=${startAbs?.toShortString()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                Log.i(TAG, text)
                setStatus(text)
            }, 650L)
            SCENARIO_BACK_CLOSE -> sourceView.postDelayed({
                val dispatched = dispatchBack()
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched currentIndex=${currentPhotoIndex()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 500L)
            }, 700L)
        }
    }

    private fun dispatchDoubleTap(x: Float, y: Float): Boolean {
        val first = dispatchTap(x, y)
        val second = sourceView.postDelayed({ dispatchTap(x, y) }, 90L)
        return first && second
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val target = photoViewerWindowView() ?: return false
        val width = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, width * x, height * y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, width * x, height * y, 0)
        val downHandled = target.dispatchTouchEvent(down)
        val upHandled = target.dispatchTouchEvent(up)
        return downHandled || upHandled
    }

    private fun dispatchSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean {
        val target = photoViewerWindowView() ?: return false
        val width = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val start = SystemClock.uptimeMillis()
        val steps = 8
        fun event(action: Int, step: Int): MotionEvent {
            val progress = step / steps.toFloat()
            return MotionEvent.obtain(
                start,
                start + (durationMs * progress).toLong(),
                action,
                width * (fromX + (toX - fromX) * progress),
                height * (fromY + (toY - fromY) * progress),
                0,
            )
        }
        var handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0))
        for (step in 1 until steps) {
            handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, step)) || handled
        }
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, steps)) || handled
        return handled
    }

    private fun dispatchPinch(centerX: Float, centerY: Float, startSpan: Float, endSpan: Float, durationMs: Long): Boolean {
        val target = photoViewerWindowView() ?: return false
        val width = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        fun coords(span: Float): Array<MotionEvent.PointerCoords> {
            val half = width * span / 2f
            val y = height * centerY
            return arrayOf(
                MotionEvent.PointerCoords().apply { x = width * centerX - half; this.y = y; pressure = 1f; size = 1f },
                MotionEvent.PointerCoords().apply { x = width * centerX + half; this.y = y; pressure = 1f; size = 1f },
            )
        }
        val steps = 16
        fun event(action: Int, pointerCount: Int, pointerCoords: Array<MotionEvent.PointerCoords>, step: Int): MotionEvent {
            return MotionEvent.obtain(
                downTime,
                downTime + durationMs * step / steps,
                action,
                pointerCount,
                properties,
                pointerCoords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        }
        var handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 1, coords(startSpan), 0))
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, coords(startSpan), 1)) || handled
        for (step in 2 until steps) {
            val progress = (step - 1) / (steps - 1).toFloat()
            val span = startSpan + (endSpan - startSpan) * progress
            handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 2, coords(span), step)) || handled
        }
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, coords(endSpan), steps)) || handled
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 1, coords(endSpan), steps)) || handled
        return handled
    }

    private fun dispatchBack(): Boolean {
        val target = photoViewerWindowView() ?: return false
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        val up = KeyEvent(eventTime, eventTime + 40L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        val downHandled = target.dispatchKeyEvent(down)
        val upHandled = target.dispatchKeyEvent(up)
        return downHandled || upHandled
    }

    private fun setStatus(text: String) {
        lastStatus = text
        showStatus(text)
    }

    private fun showStatus(text: String) {
        statusView.text = "PhotoViewer debug: $text"
    }

    private fun photoViewerWindowView(): View? {
        val window = photoViewerWindow() ?: return null
        val viewField = window.javaClass.getDeclaredField("windowView").apply { isAccessible = true }
        return viewField.get(window) as? View
    }

    private fun photoViewerContainerView(): View? {
        val window = photoViewerWindow() ?: return null
        val viewField = window.javaClass.getDeclaredField("containerView").apply { isAccessible = true }
        return viewField.get(window) as? View
    }

    private fun currentPhotoIndex(): Int {
        val field = PhotoViewer::class.java.getDeclaredField("currentIndex").apply { isAccessible = true }
        return field.getInt(PhotoViewer.getInstance())
    }

    private fun viewerScale(): Float {
        val window = photoViewerWindow() ?: return -1f
        (reflectedField(window, "scale") as? Float)?.let { return it }
        val container = reflectedField(window, "containerView") ?: return -1f
        return reflectedField(container, "scale") as? Float ?: -1f
    }

    private fun viewerRect(fieldName: String): RectF? {
        val window = photoViewerWindow() ?: return null
        val container = reflectedField(window, "containerView")
        val rect = reflectedField(window, fieldName) as? RectF
            ?: container?.let { reflectedField(it, fieldName) as? RectF }
            ?: reflectedField(window, "centerImageRect") as? RectF
            ?: container?.let { reflectedField(it, "centerImageRect") as? RectF }
        return rect?.let(::RectF)
    }

    private fun reflectedField(target: Any, fieldName: String): Any? {
        return runCatching {
            target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(target)
        }.getOrNull()
    }

    private fun photoViewerContainerLocationInWindow(): IntArray {
        val location = IntArray(2)
        photoViewerContainerView()?.getLocationInWindow(location)
        return location
    }

    private fun photoViewerWindow(): Any? {
        return PhotoViewer.getInstance()
    }

    private fun createDebugImageFiles(): List<String> {
        return DEBUG_COLORS.mapIndexed { index, colors ->
            val file = File(cacheDir, "photo-viewer-debug-${index + 1}.jpg")
            val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f,
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat(),
                colors.first,
                colors.second,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            paint.shader = null
            paint.color = 0x33ffffff
            canvas.drawCircle(bitmap.width * 0.72f, bitmap.height * 0.32f, 180f, paint)
            paint.color = 0x44000000
            canvas.drawRoundRect(120f, 650f, 1480f, 860f, 64f, 64f, paint)
            paint.color = 0xffffffff.toInt()
            paint.textSize = 92f
            paint.isFakeBoldText = true
            canvas.drawText("PhotoViewer ${index + 1}", 180f, 780f, paint)
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            bitmap.recycle()
            file.absolutePath
        }
    }

    private class SourcePreviewView(context: android.content.Context) : View(context) {
        val imageReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(false)
            setRoundRadius(AndroidUtilities.dp(14f))
        }
        private val bounds = RectF()
        private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setWillNotDraw(false)
        }

        fun setImageUrl(url: String) {
            imageReceiver.setImage(ImageLocation.getForPath(url), null, null, null, url, 0)
        }

        fun ensureReceiverBounds() {
            bounds.set(AndroidUtilities.dp(24f).toFloat(), AndroidUtilities.dp(18f).toFloat(), width - AndroidUtilities.dp(18f).toFloat(), height - AndroidUtilities.dp(22f).toFloat())
            imageReceiver.setImageCoords(bounds)
        }

        fun imageBoundsInWindow(): RectF {
            ensureReceiverBounds()
            val location = IntArray(2)
            getLocationInWindow(location)
            return RectF(bounds).apply { offset(location[0].toFloat(), location[1].toFloat()) }
        }

        fun imageHandoffRectInWindow(): RectF {
            ensureReceiverBounds()
            val location = IntArray(2)
            getLocationInWindow(location)
            val drawRegion = imageReceiver.drawRegion
            val rect = if (!drawRegion.isEmpty) {
                RectF(drawRegion)
            } else {
                RectF(imageReceiver.imageX, imageReceiver.imageY, imageReceiver.imageX2, imageReceiver.imageY2)
            }
            return rect.apply { offset(location[0].toFloat(), location[1].toFloat()) }
        }

        fun bitmapHolder(): ImageReceiver.BitmapHolder {
            return imageReceiver.getBitmapSafe() ?: ImageReceiver.BitmapHolder(createFallbackBitmap())
        }

        fun radius(): IntArray = imageReceiver.getRoundRadius(true).copyOf()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            imageReceiver.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            imageReceiver.onDetachedFromWindow()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            ensureReceiverBounds()
            placeholderPaint.shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                0xff355c7d.toInt(),
                0xffc06c84.toInt(),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(bounds, AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(14f).toFloat(), placeholderPaint)
            placeholderPaint.shader = null
            imageReceiver.draw(canvas)
        }

        private fun createFallbackBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            draw(canvas)
            return bitmap
        }
    }

    private companion object {
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_CLOSE_AFTER_MS = "closeAfterMs"
        private const val EXTRA_SCENARIO = "scenario"
        private const val SCENARIO_SWIPE_NEXT = "swipeNext"
        private const val SCENARIO_DRAG_DISMISS = "dragDismiss"
        private const val SCENARIO_DOUBLE_TAP_ZOOM = "doubleTapZoom"
        private const val SCENARIO_PINCH_ZOOM = "pinchZoom"
        private const val SCENARIO_HANDOFF_RECT = "handoffRect"
        private const val SCENARIO_TOPIC_CELL_TAP = "topicCellTap"
        private const val SCENARIO_BACK_CLOSE = "backClose"
        private const val DEBUG_BASE_URL = "https://linux.do/"
        private const val TAG = "PhotoViewerDebugActivity"
        private val DEBUG_COLORS = listOf(
            0xff1c7c54.toInt() to 0xff73e2a7.toInt(),
            0xff214f9c.toInt() to 0xfff0b429.toInt(),
            0xff8f2d56.toInt() to 0xffff7a59.toInt(),
        )
    }
}

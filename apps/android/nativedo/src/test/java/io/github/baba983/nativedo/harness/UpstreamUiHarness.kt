package io.github.baba983.nativedo.harness

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPausedLooper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ILocationServiceProvider
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.PhotoViewer
import io.github.baba983.nativedo.app.NativeDoActivity
import io.github.baba983.nativedo.app.NativeDoApplication
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Runs the stock upstream UI on the JVM: Robolectric supplies the Android runtime (real
 * resources, real Canvas/Skia through `@GraphicsMode(NATIVE)`), the generated shadows in
 * `shadows/` supply the natives that live in `libtmessages.49.so`.
 *
 * Two entry points, because there are two levels worth rendering:
 *  * [bootApp] builds the real [NativeDoActivity], so a screenshot shows the screen the app
 *    ships (ActionBar, drawer host, the fragment the shell presents);
 *  * [bootViews] skips the shell and hands a bare view tree to [attach]/[render], for the
 *    cell-level tests that do not need a fragment.
 *
 * Nothing here touches an upstream file: the environment is built from public statics, the way
 * `ThemePreviewActivity` builds a local message for its preview list.
 */
internal class UpstreamUiHarness {

    lateinit var activity: Activity
    /** The activity's content view: gives the cells a window and a real width. */
    lateinit var root: FrameLayout
    /** The wrap-content wrapper that is measured and drawn. */
    lateinit var content: FrameLayout

    /** Pixel 6 sized, at the density Robolectric reports for that device. */
    val width: Int get() = context.resources.displayMetrics.widthPixels
    val height: Int get() = context.resources.displayMetrics.heightPixels

    val context: Context get() = RuntimeEnvironment.getApplication()

    /** The shell the app ships, driven through Robolectric's activity lifecycle. */
    fun bootApp(): NativeDoActivity = bootApp(null)

    /**
     * The shell booted with [intent] as its launch intent, for a cold-start deep-link test; a null
     * [intent] is the plain launch [bootApp] does.
     */
    fun bootApp(intent: android.content.Intent?): NativeDoActivity {
        prepareUpstream()
        resetImageLoader()
        resetPhotoViewer()
        resetReadMarks()
        // setup().visible() drains without a budget. Glass backgrounds start upstream's permanent
        // frame clock, so hold VSYNC until that drain ends, then use our bounded idle below.
        val pausedFrames = org.robolectric.shadows.ShadowChoreographer.isPaused()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        val app = try {
            val controller = if (intent == null) {
                Robolectric.buildActivity(NativeDoActivity::class.java)
            } else {
                Robolectric.buildActivity(NativeDoActivity::class.java, intent)
            }
            val controller2 = controller.setup()
            // 13.2.1's page host creates the tab shell's views after Robolectric's visible() pass,
            // so the views added later never receive the simulated attach. Ask again now that the
            // tree is complete: an unattached tree loses View clicks (they are posted until attach)
            // and reports window coordinates relative to the wrong root.
            controller2.visible()
            controller2.get()
        } finally {
            org.robolectric.shadows.ShadowChoreographer.setPaused(pausedFrames)
        }
        activity = app
        idle()
        // 13.2.1's shell finishes its first layout on delayed passes: the tab strip and the page
        // host are added after the window's first frame, and until that lands the strip is not
        // attached (its clicks queue until attach) and reports window coordinates from the wrong
        // root. Let the simulated clock reach those passes before a test drives the shell.
        idleClock(300)
        reportBootRequests()
        resetSideEffectCounters()
        return app
    }

    /** A bare activity with an empty wrapper, for tests that build the view themselves. */
    fun bootViews() {
        prepareUpstream()
        resetImageLoader()
        resetPhotoViewer()
        resetReadMarks()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        root = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        content = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        root.addView(content)
        activity.setContentView(root)
        idle()
        reportBootRequests()
        resetSideEffectCounters()

        LocaleController.getInstance()
        Theme.createCommonResources(context)
        Theme.createDialogsResources(context)
        Theme.createChatResources(context, false)
        Theme.createCommonMessageResources()
        idle()
    }

    /**
     * This module's own per-process state, dropped the way upstream's is: a mark is a topic a
     * previous test read, and it would clear the badge of a topic this test has not opened.
     */
    private fun resetReadMarks() {
        io.github.baba983.nativedo.feature.topic.data.TopicReadMarks.clear()
    }

    private fun prepareUpstream() {
        // Robolectric instantiates upstream's ApplicationLoader (the manifest's application),
        // so applicationContext / applicationLoaderInstance are already the real ones. Only the
        // GMS-backed location provider is replaced: there is no Play Services on a bare JVM.
        ApplicationLoader.applicationLoaderInstance = object : NativeDoApplication() {
            override fun onCreateLocationServiceProvider(): ILocationServiceProvider = NoopLocationProvider
        }
        val metrics = context.resources.displayMetrics
        AndroidUtilities.density = metrics.density
        AndroidUtilities.displayMetrics = metrics
        AndroidUtilities.displaySize.set(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * What upstream's singletons ask for while the environment boots (no account exists yet).
     * Recorded before the counters are reset, so it is reported without failing the test: a
     * request here cannot leave the app, because tgnet is never initialised.
     */
    private fun reportBootRequests() {
        println(
            "harness: requests during boot = ${ShadowConnectionsManager.REQUEST_ATTEMPTS} " +
                "last=${ShadowConnectionsManager.LAST_REQUEST_CLASS}",
        )
    }

    private fun resetSideEffectCounters() {
        ShadowConnectionsManager.resetCounters()
        ShadowSQLiteDatabase.resetCounters()
    }

    /**
     * Upstream's image singletons live in the sandbox, which Robolectric shares between test classes
     * that use the same configuration. The singleton holds both the decoded images and the
     * directories it reads files from — and those directories belong to the app data dir of the test
     * that created it, so a later test would look for its files in an earlier test's directory.
     * Dropping the instance at boot rebuilds both from the app this test runs in.
     */
    private fun resetImageLoader() {
        runCatching {
            val field = ImageLoader::class.java.getDeclaredField("Instance").apply { isAccessible = true }
            field.set(null, null)
        }
        runCatching { ImageLoader.getInstance().clearMemory() }
    }

    /**
     * Upstream's full screen viewer is a singleton too, and one test's open viewer is the next
     * test's refusal: `PhotoViewer.openPhoto` answers false while a viewer is already visible.
     * Dropping the instance gives every test the viewer it would have on a first open.
     */
    private fun resetPhotoViewer() {
        for (name in listOf("Instance", "PipInstance", "Instance2")) {
            runCatching {
                val field = PhotoViewer::class.java.getDeclaredField(name).apply { isAccessible = true }
                field.set(null, null)
            }
        }
    }

    fun present(fragment: BaseFragment) {
        (activity as NativeDoActivity).present(fragment)
        idle()
    }

    /**
     * The fragment the shell is presenting on top. The tab host counts through to the page it is
     * showing: that is the screen the reader is actually looking at when no fragment is pushed.
     */
    fun visibleFragment(): BaseFragment? {
        val top = topFragment()
        return (top as? org.telegram.ui.ViewPagerActivity)?.currentVisibleFragment ?: top
    }

    fun visibleFragmentName(): String? = visibleFragment()?.javaClass?.simpleName

    /** The fragment on top of the shell's stack, tab host included. */
    fun topFragment(): BaseFragment? {
        val layoutField = NativeDoActivity::class.java.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(activity) as org.telegram.ui.ActionBar.ActionBarLayout
        return layout.fragmentStack.lastOrNull()
    }

    fun fragmentStack(): List<BaseFragment> {
        val layoutField = NativeDoActivity::class.java.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(activity) as org.telegram.ui.ActionBar.ActionBarLayout
        return layout.fragmentStack.toList()
    }

    /**
     * Drains the main looper, but never for longer than [maxTasks] tasks.
     *
     * <p>Upstream animates rich media off `Choreographer.postFrameCallback`, and Robolectric's
     * paused clock makes the next frame due immediately, so an unbounded drain never returns: the
     * frame callback that keeps the spinner moving also keeps the queue non-empty forever. A
     * budget ends the drain with the drawing done.</p>
     */
    fun idle(maxTasks: Int = 1_000) {
        val shadow = shadowOf(Looper.getMainLooper()) as ShadowPausedLooper
        var ran = 0
        while (ran < maxTasks && !shadow.isIdle()) {
            shadow.runOneTask()
            ran++
        }
        if (ran >= maxTasks && !budgetReported) {
            budgetReported = true
            println("harness: idle stopped at the $maxTasks task budget")
        }
    }

    /**
     * Runs the main looper with the clock moving, in slices, so a delayed callback (upstream moves
     * its composer to the send state 200 ms after a send) is reached without an unbounded drain: a
     * frame callback that re-posts itself would run forever inside a single long drain.
     */
    fun idleClock(millis: Long, slice: Long = 20, maxTasks: Int = 200) {
        var elapsed = 0L
        while (elapsed < millis) {
            idle(maxTasks)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(slice))
            elapsed += slice
        }
        idle(maxTasks)
    }

    /**
     * Drains the workers upstream owns (`ImageLoader`, `FileLoader`). They run on private loopers,
     * which Robolectric pauses like any other, so a cached photo only reaches the cell once they
     * are given a turn.
     */
    fun idleUpstreamQueues(tasks: Int = 400) {
        for (queue in upstreamQueues()) {
            // Not every worker is looper-backed: a DispatchQueuePriority runs on a plain thread
            // pool, which Robolectric does not pause and which therefore needs no draining.
            val handler = runCatching {
                queue.javaClass.getMethod("getHandler").invoke(queue) as? Handler
            }.getOrNull() ?: continue
            val looper = handler.looper ?: continue
            val shadow = shadowOf(looper) as ShadowPausedLooper
            shadow.idleFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            var ran = 0
            while (ran < tasks && !shadow.isIdle()) {
                shadow.runOneTask()
                ran++
            }
        }
    }

    /**
     * Upstream's workers, by the name of the field that holds them. They are private, so the
     * harness reaches them the way it reaches the shell's own internals: by reflection, in test
     * code only.
     */
    private fun upstreamQueues(): List<Any> {
        val queues = mutableListOf<Any>()
        val imageLoader = ImageLoader.getInstance()
        for (name in listOf("cacheOutQueue", "cacheThumbOutQueue", "thumbGeneratingQueue", "imageLoadQueue")) {
            val field = imageLoader.javaClass.getDeclaredField(name).apply { isAccessible = true }
            queues += field.get(imageLoader)!!
        }
        queues += FileLoader.getInstance(0).fileLoaderQueue
        return queues
    }

    private var budgetReported = false

    /**
     * Waits for work that finishes off the main thread (the API client runs on OkHttp's pool)
     * while draining the main looper, which is where the callbacks land.
     *
     * <p>Bounded by iterations rather than by a clock: Robolectric simulates the clock, so a
     * deadline would never be reached when there is no work left to idle.</p>
     */
    fun await(what: String, iterations: Int = 4_000, condition: () -> Boolean) {
        repeat(iterations) {
            idle()
            idleUpstreamQueues()
            if (condition()) {
                idle()
                return
            }
            // Real time, not simulated: the fetch finishes on OkHttp's own thread, and the
            // sandbox shadows Thread.sleep onto Robolectric's paused clock, which would never
            // elapse here. LockSupport lives in java.base and is not instrumented.
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
        throw AssertionError("timed out waiting for $what")
    }

    fun attach(view: View, params: ViewGroup.LayoutParams) {
        content.addView(view, params)
        idle()
        // Lay out once so the parent has a real width: upstream cells size their text against
        // getParentWidth(), and binding a cell before that wraps the text at width 0.
        layoutRoot()
    }

    /** Lays the wrapper out at its full width; called again before drawing. */
    fun layoutRoot() {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        content.measure(widthSpec, heightSpec)
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        idle()
    }

    /** Lays the harness root out at [width] and writes what it drew to a PNG. */
    fun render(name: String): File = renderView(content, name)

    /** Lays the activity's content out and writes what it drew to a PNG. */
    fun renderApp(name: String): File {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
        return renderView(contentView, name)
    }

    /**
     * The same draw with a bounded drain. A queued frame callback keeps re-posting itself, so
     * [renderApp]'s unbounded drain runs an animation to its end before drawing; a frame in the
     * middle of one needs the drain to stop after [maxTasks].
     */
    fun renderAppFrame(name: String, maxTasks: Int = 6): File {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
        return renderView(contentView, name, maxTasks)
    }

    /**
     * Lays the activity's content out without drawing it. A list that was handed a new adapter only
     * binds its rows during a layout pass, so waiting for a row to change means laying it out.
     */
    fun layoutApp() {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        contentView.measure(widthSpec, heightSpec)
        contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)
        idle()
    }

    /**
     * Lays the activity's content out and draws it into a throwaway bitmap: a row's own draw pass is
     * where this client hands its avatar over, so waiting for one means drawing.
     */
    fun drawApp() {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        layoutApp()
        val bitmap = Bitmap.createBitmap(
            contentView.measuredWidth.coerceAtLeast(1),
            contentView.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            contentView.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
        idle()
    }

    /**
     * Draws the whole page the way the window manager composites it: the activity's own decor, then
     * every popup window at the offset the window manager placed it at.
     *
     * <p>A menu is its own window: a capture of the content view alone misses the menu, and a
     * capture of the popup alone misses the page it dims and the post it leaves lit. Only the two
     * composited show whether the menu is aligned, dimmed and anchored the way it is on a device.</p>
     */
    fun renderComposite(relativePath: String, maxTasks: Int = 1_000): File {
        val decor = activity.window.decorView
        val w = width
        val h = height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            measureAndLayout(decor, w, h, maxTasks)
            decor.draw(canvas)
            val windowManager = org.robolectric.shadow.api.Shadow.extract(activity.windowManager)
                as org.robolectric.shadows.ShadowWindowManagerImpl
            for (view in windowManager.views) {
                if (view === decor) continue
                if (view.visibility != View.VISIBLE) continue
                val params = view.layoutParams as? android.view.WindowManager.LayoutParams
                // A popup is WRAP_CONTENT: measure it to its own natural size, never stretched to the
                // display, or the rail inside it drifts away from the list it belongs to.
                repeat(2) {
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST),
                    )
                    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                    idle(maxTasks)
                }
                val dx = params?.x ?: 0
                val dy = params?.y ?: 0
                canvas.save()
                canvas.translate(dx.toFloat(), dy.toFloat())
                view.draw(canvas)
                canvas.restore()
            }
            val out = repoFile(relativePath)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("harness: wrote $out (${out.length()} bytes)")
            return out
        } finally {
            bitmap.recycle()
        }
    }

    private fun measureAndLayout(view: View, w: Int, h: Int, maxTasks: Int) {
        repeat(2) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            idle(maxTasks)
        }
    }

    private fun renderView(view: View, name: String, maxTasks: Int = 1_000): File {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        // Two passes: binding a cell attaches its rich layout, which asks for another measure, and
        // a single pass would draw a bubble measured before the layout existed (clipped bottom).
        repeat(2) {
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            idle(maxTasks)
        }

        val bitmap = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(1),
            view.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            view.draw(Canvas(bitmap))

            val out = File(artifactsDir(), "$name.png")
            out.parentFile?.mkdirs()
            cropToContent(bitmap).let { cropped ->
                try {
                    FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    if (cropped !== bitmap) cropped.recycle()
                }
            }
            println("harness: wrote $out (${out.length()} bytes)")
            return out
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The stock cell measures to the whole chat viewport (that is what `setFullyDraw` does), so
     * the drawn content sits at the top of a mostly empty bitmap. Trim the empty rows.
     */
    private fun cropToContent(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var lastContentRow = -1
        for (row in bitmap.height - 1 downTo 0) {
            val start = row * bitmap.width
            for (column in 0 until bitmap.width) {
                if (pixels[start + column] != Color.WHITE) {
                    lastContentRow = row
                    break
                }
            }
            if (lastContentRow >= 0) break
        }
        if (lastContentRow <= 0) return bitmap
        val croppedHeight = (lastContentRow + 8).coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, croppedHeight)
    }

    fun fixture(name: String): String = repoFile("fixtures/discourse/$name").readText()

    /**
     * What the stock code touched outside the cells. tgnet must never initialise, no Telegram
     * database may be opened, and nothing may reach the tgnet native layer: this client speaks to
     * linux.do over HTTPS, and every one of those three would be a Telegram-only path.
     *
     * <p>These are read without waiting on purpose. Robolectric simulates both clocks, so a wait
     * for the storage queue would either spin or block forever; what makes the database check
     * meaningful is [NativeDoHooksTest], which calls the same open with the hook off and shows it
     * reaching SQLite.</p>
     */
    fun assertNoTelegramSideEffects() {
        println(
            "harness: request_attempts=${ShadowConnectionsManager.REQUEST_ATTEMPTS} " +
                "native_sendRequest=${ShadowConnectionsManager.SEND_REQUEST_CALLS} " +
                "native_init=${ShadowConnectionsManager.NATIVE_INIT_CALLS} " +
                "sqlite_opendb=${ShadowSQLiteDatabase.OPEN_DB_CALLS}",
        )
        ShadowConnectionsManager.LAST_REQUEST_CLASS?.let {
            println("harness: last request = $it")
        }
        expectZero("tgnet initialisations", ShadowConnectionsManager.NATIVE_INIT_CALLS)
        expectZero("Telegram database opens", ShadowSQLiteDatabase.OPEN_DB_CALLS)
        expectZero("calls reaching the tgnet native layer", ShadowConnectionsManager.SEND_REQUEST_CALLS)
    }

    private fun expectZero(what: String, value: Int) {
        if (value != 0) throw AssertionError("$what: expected 0 but was $value")
    }

    private fun artifactsDir(): File = repoFile(".amp/in/artifacts/nativedo")

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            if (File(dir, "fixtures/discourse").isDirectory) {
                return File(dir, relative)
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root from ${System.getProperty("user.dir")}")
    }
}

/** No Google Play Services on a bare JVM: upstream asks for a provider, we hand it a no-op. */
internal object NoopLocationProvider : ILocationServiceProvider {
    override fun init(context: Context) = Unit
    override fun onCreateLocationRequest(): ILocationServiceProvider.ILocationRequest =
        object : ILocationServiceProvider.ILocationRequest {
            override fun setPriority(priority: Int) = Unit
            override fun setInterval(interval: Long) = Unit
            override fun setFastestInterval(interval: Long) = Unit
        }

    override fun onCreateLocationServicesAPI(
        context: Context,
        connectionCallbacks: ILocationServiceProvider.IAPIConnectionCallbacks,
        failedListener: ILocationServiceProvider.IAPIOnConnectionFailedListener,
    ): ILocationServiceProvider.IMapApiClient = object : ILocationServiceProvider.IMapApiClient {
        override fun connect() = Unit
        override fun disconnect() = Unit
    }

    override fun checkServices(): Boolean = false
    override fun getLastLocation(callback: androidx.core.util.Consumer<Location>) = Unit
    override fun requestLocationUpdates(
        request: ILocationServiceProvider.ILocationRequest,
        locationListener: ILocationServiceProvider.ILocationListener,
    ) = Unit

    override fun removeLocationUpdates(locationListener: ILocationServiceProvider.ILocationListener) = Unit
    override fun checkLocationSettings(
        request: ILocationServiceProvider.ILocationRequest,
        callback: androidx.core.util.Consumer<Int>,
    ) = Unit
}

package io.github.baba983.nativedo.spike

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ILocationServiceProvider
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme
import io.github.baba983.nativedo.spike.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.spike.shadows.ShadowSQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Runs the stock upstream UI on the JVM: Robolectric supplies the Android runtime (real
 * resources, real Canvas/Skia through `@GraphicsMode(NATIVE)`), the generated shadows in
 * `shadows/` supply the natives that live in `libtmessages.49.so`.
 *
 * Nothing here touches an upstream file: the environment is built from public statics, the
 * way `ThemePreviewActivity` builds a local message for its preview list.
 */
internal class SpikeHarness {

    lateinit var activity: Activity
    /** The activity's content view: gives the cells a window and a real width. */
    lateinit var root: FrameLayout
    /** The wrap-content wrapper that is measured and drawn. */
    lateinit var content: FrameLayout

    /** Pixel 6 sized, at the density Robolectric reports for that device. */
    val width: Int get() = context.resources.displayMetrics.widthPixels
    val height: Int get() = context.resources.displayMetrics.heightPixels

    val context: Context get() = RuntimeEnvironment.getApplication()

    fun boot() {
        // Robolectric instantiates upstream's ApplicationLoader (the manifest's application),
        // so applicationContext / applicationLoaderInstance are already the real ones. Only the
        // GMS-backed location provider is replaced: there is no Play Services on a bare JVM.
        ApplicationLoader.applicationLoaderInstance = object : ApplicationLoader() {
            override fun onCreateLocationServiceProvider(): ILocationServiceProvider = NoopLocationProvider
        }
        val metrics = context.resources.displayMetrics
        AndroidUtilities.density = metrics.density
        AndroidUtilities.displayMetrics = metrics
        AndroidUtilities.displaySize.set(metrics.widthPixels, metrics.heightPixels)

        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        root = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        content = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        root.addView(content)
        activity.setContentView(root)
        idle()

        // What upstream's singletons ask for while the environment boots (no account exists yet).
        println("spike: requests during boot = ${ShadowConnectionsManager.REQUEST_ATTEMPTS} " +
            "last=${ShadowConnectionsManager.LAST_REQUEST_CLASS}")

        // Measure from a clean slate: what the cells touch is what this run touched.
        ShadowConnectionsManager.resetCounters()
        ShadowSQLiteDatabase.resetCounters()

        LocaleController.getInstance()
        Theme.createCommonResources(context)
        Theme.createDialogsResources(context)
        Theme.createChatResources(context, false)
        Theme.createCommonMessageResources()
        idle()
    }

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

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
    fun render(name: String, measureWidth: Int = width): File {
        layoutRoot()
        idle()

        val bitmap = Bitmap.createBitmap(
            content.measuredWidth.coerceAtLeast(1),
            content.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        content.draw(Canvas(bitmap))

        val out = File(artifactsDir(), "$name.png")
        out.parentFile?.mkdirs()
        cropToContent(bitmap).let { cropped ->
            FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        println("spike: wrote $out (${out.length()} bytes)")
        return out
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

    fun fixture(name: String): String {
        val file = repoFile("fixtures/discourse/$name")
        return file.readText()
    }

    private fun artifactsDir(): File = repoFile(".amp/in/artifacts/spike")

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (File(dir, "fixtures/discourse").isDirectory) {
                return candidate
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
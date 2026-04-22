package io.github.baba983.nativedo.core.util

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.ceil
import kotlin.math.roundToInt

object AndroidUtilities : ComponentCallbacks {
    private val lock = Any()

    @Volatile
    private var initializedContext: Context? = null

    @Volatile
    var density: Float = ResourcesSnapshot.fromSystem().density
        private set

    @Volatile
    var displaySize: Point = ResourcesSnapshot.fromSystem().displaySize
        private set

    @Volatile
    var statusBarHeight: Int = ResourcesSnapshot.fromSystem().statusBarHeight
        private set

    @Volatile
    var navigationBarHeight: Int = ResourcesSnapshot.fromSystem().navigationBarHeight
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (initializedContext === appContext) {
                updateDisplayMetrics(appContext)
                return
            }
            initializedContext?.unregisterComponentCallbacks(this)
            initializedContext = appContext
            initializedContext?.registerComponentCallbacks(this)
            updateDisplayMetrics(appContext)
        }
    }

    fun dp(value: Int): Int = dp(value.toFloat())

    fun dp(value: Float): Int {
        if (value == 0f) {
            return 0
        }
        return ceil(density * value.toDouble()).toInt()
    }

    fun dpf2(value: Float): Float {
        if (value == 0f) {
            return 0f
        }
        return density * value
    }

    fun lerp(a: Int, b: Int, t: Float): Int = (a + (t * (b - a))).toInt()

    fun lerp(a: Float, b: Float, t: Float): Float = a + ((b - a) * t)

    fun blendColors(color1: Int, color2: Int, factor: Float): Int {
        val clampedFactor = Utilities.clamp01(factor)
        val inverseFactor = 1f - clampedFactor
        return argb(
            alpha = ((Color.alpha(color1) * inverseFactor) + (Color.alpha(color2) * clampedFactor)).roundToInt(),
            red = ((Color.red(color1) * inverseFactor) + (Color.red(color2) * clampedFactor)).roundToInt(),
            green = ((Color.green(color1) * inverseFactor) + (Color.green(color2) * clampedFactor)).roundToInt(),
            blue = ((Color.blue(color1) * inverseFactor) + (Color.blue(color2) * clampedFactor)).roundToInt(),
        )
    }

    fun setAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (Utilities.clamp(alpha, 255, 0) shl 24)
    }

    fun argb(
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ): Int {
        return Color.argb(
            Utilities.clamp(alpha, 255, 0),
            Utilities.clamp(red, 255, 0),
            Utilities.clamp(green, 255, 0),
            Utilities.clamp(blue, 255, 0),
        )
    }

    fun showKeyboard(view: View?): Boolean {
        if (view == null) {
            return false
        }
        return try {
            val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false
            inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } catch (error: Exception) {
            FileLog.e("AndroidUtilities", "Failed to show keyboard", error)
            false
        }
    }

    fun hideKeyboard(view: View?) {
        if (view == null) {
            return
        }
        try {
            val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return
            if (!inputMethodManager.isActive) {
                return
            }
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (error: Exception) {
            FileLog.e("AndroidUtilities", "Failed to hide keyboard", error)
        }
    }

    fun getKeyboardHeight(view: View): Int {
        return ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom
            ?: 0
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        initializedContext?.let(::updateDisplayMetrics)
    }

    override fun onLowMemory() = Unit

    private fun updateDisplayMetrics(context: Context) {
        val snapshot = ResourcesSnapshot.fromContext(context)
        density = snapshot.density
        displaySize = snapshot.displaySize
        statusBarHeight = snapshot.statusBarHeight
        navigationBarHeight = snapshot.navigationBarHeight
    }

    private data class ResourcesSnapshot(
        val density: Float,
        val displaySize: Point,
        val statusBarHeight: Int,
        val navigationBarHeight: Int,
    ) {
        companion object {
            fun fromContext(context: Context): ResourcesSnapshot {
                val resources = context.resources
                val metrics = resources.displayMetrics
                return ResourcesSnapshot(
                    density = metrics.density,
                    displaySize = Point(metrics.widthPixels, metrics.heightPixels),
                    statusBarHeight = resolveDimensionPixelSize(resources, "status_bar_height"),
                    navigationBarHeight = resolveDimensionPixelSize(resources, "navigation_bar_height"),
                )
            }

            fun fromSystem(): ResourcesSnapshot {
                val metrics = android.content.res.Resources.getSystem().displayMetrics
                return ResourcesSnapshot(
                    density = metrics.density,
                    displaySize = Point(metrics.widthPixels, metrics.heightPixels),
                    statusBarHeight = 0,
                    navigationBarHeight = 0,
                )
            }

            private fun resolveDimensionPixelSize(
                resources: android.content.res.Resources,
                name: String,
            ): Int {
                val resourceId = resources.getIdentifier(name, "dimen", "android")
                if (resourceId == 0) {
                    return 0
                }
                return resources.getDimensionPixelSize(resourceId)
            }
        }
    }
}

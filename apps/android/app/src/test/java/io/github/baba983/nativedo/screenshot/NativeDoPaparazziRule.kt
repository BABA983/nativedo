package io.github.baba983.nativedo.screenshot

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme
import java.io.File

internal class NativeDoPaparazziRule(
    vararg configOptions: List<PaparazziConfigOption>,
    private val theme: String = "Theme.NativeDo",
    private val contentWidthDp: Float = 360f,
    private val maxPercentDifference: Double = 0.001,
) : TestRule {
    private val testCases = configOptions.toTestCases()
    private val defaultDeviceConfig = DeviceConfig.PIXEL_6.copy(softButtons = false)
    private val paparazzi = Paparazzi(
        deviceConfig = defaultDeviceConfig,
        maxPercentDifference = maxPercentDifference,
        renderingMode = SessionParams.RenderingMode.SHRINK,
        showSystemUi = false,
        theme = theme,
    )
    private var description: Description? = null

    override fun apply(base: Statement, description: Description): Statement {
        this.description = description
        return object : Statement() {
            override fun evaluate() = base.evaluate()
        }
    }

    fun snapshot(viewFactory: (Context) -> View) {
        val testDescription = requireNotNull(description) {
            "NativeDoPaparazziRule must be applied as a JUnit rule"
        }
        var firstFailure: Throwable? = null

        testCases.forEach { testCase ->
            val scenarioDescription = Description.createTestDescription(
                testDescription.className,
                testDescription.methodName + testCase.name,
            )
            try {
                paparazzi.apply(
                    object : Statement() {
                        override fun evaluate() {
                            val deviceConfig = testCase.apply(defaultDeviceConfig)
                            paparazzi.unsafeUpdateConfig(deviceConfig)
                            initializeEnvironment(paparazzi.context)
                            testCase.initialize(paparazzi.context)
                            paparazzi.snapshot(
                                fixedWidthContainer(
                                    context = paparazzi.context,
                                    content = viewFactory(paparazzi.context),
                                ),
                            )
                        }
                    },
                    scenarioDescription,
                ).evaluate()
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = failure
                }
            } finally {
                testCase.reset()
            }
        }

        firstFailure?.let { throw it }
    }

    fun verify(assertions: (Context) -> Unit) {
        val testDescription = requireNotNull(description) {
            "NativeDoPaparazziRule must be applied as a JUnit rule"
        }
        paparazzi.apply(
            object : Statement() {
                override fun evaluate() {
                    initializeEnvironment(paparazzi.context)
                    assertions(paparazzi.context)
                }
            },
            testDescription,
        ).evaluate()
    }

    private fun initializeEnvironment(context: Context) {
        val applicationContext = PaparazziApplicationContext(context)
        applicationContext.seedThemeAsset("bluebubbles.attheme")
        applicationContext.seedThemeAsset("darkblue.attheme")
        ApplicationLoader.init(applicationContext)
        LocaleController.init(context)
        AndroidUtilities.init(context)
        Theme.getActiveTheme()
        Theme.createDialogsResources(context)
    }

    private fun fixedWidthContainer(
        context: Context,
        content: View,
    ): FrameLayout {
        val contentHeight = content.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                AndroidUtilities.dp(contentWidthDp),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    contentHeight,
                ),
            )
        }
    }
}

private class PaparazziApplicationContext(
    base: Context,
) : ContextWrapper(base) {
    private val sharedPreferences = mutableMapOf<String, SharedPreferences>()
    private val rootDirectory = File(
        System.getProperty("java.io.tmpdir", "/tmp"),
        "nativedo-paparazzi",
    )

    override fun getApplicationContext(): Context = this

    override fun getCacheDir(): File = directory("cache")

    override fun getFilesDir(): File = directory("files")

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return sharedPreferences.getOrPut(name) { InMemorySharedPreferences() }
    }

    override fun getSystemService(name: String): Any? {
        return if (name == Context.BATTERY_SERVICE) null else super.getSystemService(name)
    }

    fun seedThemeAsset(assetName: String) {
        val source = listOf(
            File("telegram/src/main/assets/$assetName"),
            File("apps/android/telegram/src/main/assets/$assetName"),
            File("../telegram/src/main/assets/$assetName"),
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Missing $assetName for Paparazzi Theme tests")
        source.copyTo(File(filesDir, assetName), overwrite = true)
    }

    private fun directory(name: String): File {
        return File(rootDirectory, name).apply { mkdirs() }
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    }
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any?>()
        private val removals = linkedSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = update(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = update(key, values?.toSet())
        override fun putInt(key: String, value: Int) = update(key, value)
        override fun putLong(key: String, value: Long) = update(key, value)
        override fun putFloat(key: String, value: Float) = update(key, value)
        override fun putBoolean(key: String, value: Boolean) = update(key, value)

        override fun remove(key: String): SharedPreferences.Editor = apply {
            removals += key
            updates -= key
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
            updates.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            val changedKeys = linkedSetOf<String>()
            if (clear) {
                changedKeys += values.keys
                values.clear()
            }
            removals.forEach { key ->
                if (values.remove(key) != null) {
                    changedKeys += key
                }
            }
            updates.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
                changedKeys += key
            }
            changedKeys.forEach { key ->
                listeners.toList().forEach { listener ->
                    listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, key)
                }
            }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
            updates[key] = value
            removals -= key
        }
    }
}

internal interface PaparazziConfigOption {
    fun apply(deviceConfig: DeviceConfig): DeviceConfig = deviceConfig

    fun initialize(context: Context) = Unit

    fun reset() = Unit
}

internal enum class SystemAppearance(
    private val nightMode: NightMode,
) : PaparazziConfigOption {
    LightTheme(NightMode.NOTNIGHT),
    DarkTheme(NightMode.NIGHT),
    ;

    override fun apply(deviceConfig: DeviceConfig): DeviceConfig {
        return deviceConfig.copy(nightMode = nightMode)
    }

    override fun initialize(context: Context) {
        val dark = this == DarkTheme
        val themeInfo = if (dark) Theme.getCurrentNightTheme() else Theme.getCurrentTheme()
        Theme.applyTheme(themeInfo, false, dark)
    }

    override fun reset() {
        Theme.applyTheme(Theme.getCurrentTheme(), false, false)
    }
}

internal enum class FontSize(
    private val scaleFactor: Float,
) : PaparazziConfigOption {
    DefaultFont(1f),
    LargeFont(1.5f),
    ;

    override fun apply(deviceConfig: DeviceConfig): DeviceConfig {
        return deviceConfig.copy(fontScale = scaleFactor)
    }
}

private fun Array<out List<PaparazziConfigOption>>.toTestCases(): List<TestCase> {
    if (isEmpty()) {
        return listOf(TestCase(emptyList()))
    }
    return createPermutations(this).map(::TestCase)
}

private fun createPermutations(
    options: Array<out List<PaparazziConfigOption>>,
): List<List<PaparazziConfigOption>> {
    return options.toSet().fold(listOf(emptyList())) { permutations, optionSet ->
        permutations.flatMap { permutation ->
            optionSet.map { option -> permutation + option }
        }
    }
}

private data class TestCase(
    val configOptions: List<PaparazziConfigOption>,
) {
    val name: String
        get() = configOptions.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        )

    fun apply(deviceConfig: DeviceConfig): DeviceConfig {
        return configOptions.fold(deviceConfig) { current, option -> option.apply(current) }
    }

    fun initialize(context: Context) {
        configOptions.forEach { it.initialize(context) }
    }

    fun reset() {
        configOptions.forEach(PaparazziConfigOption::reset)
    }
}

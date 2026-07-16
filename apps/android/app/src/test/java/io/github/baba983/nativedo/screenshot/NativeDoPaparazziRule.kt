package io.github.baba983.nativedo.screenshot

import android.content.Context
import android.content.ContextWrapper
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
) : TestRule {
    private val testCases = configOptions.toTestCases()
    private val defaultDeviceConfig = DeviceConfig.PIXEL_6.copy(softButtons = false)
    private val paparazzi = Paparazzi(
        deviceConfig = defaultDeviceConfig,
        maxPercentDifference = 0.001,
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

    private fun initializeEnvironment(context: Context) {
        ApplicationLoader.init(PaparazziApplicationContext(context))
        LocaleController.init(context)
        AndroidUtilities.init(context)
        Theme.init(context)
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
    private val rootDirectory = File(
        System.getProperty("java.io.tmpdir", "/tmp"),
        "nativedo-paparazzi",
    )

    override fun getApplicationContext(): Context = this

    override fun getCacheDir(): File = directory("cache")

    override fun getFilesDir(): File = directory("files")

    private fun directory(name: String): File {
        return File(rootDirectory, name).apply { mkdirs() }
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

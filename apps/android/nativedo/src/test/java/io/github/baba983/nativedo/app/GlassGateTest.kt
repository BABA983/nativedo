package io.github.baba983.nativedo.app

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities
import io.github.baba983.nativedo.topic.TopicFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.LiteMode
import org.telegram.messenger.MessagesController
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode

/**
 * The blur gate behind the topic header's capsules: which source the ActionBar's glass drawable
 * samples, and the runtime configuration that decides it.
 *
 * <p>Two failure modes are pinned here, both invisible on a low-performance device because the
 * opaque fallback is the correct answer there:</p>
 * <ul>
 *   <li>a device class where upstream blurs must take the render node, not the flat colour;</li>
 *   <li>a process whose SharedConfig/LiteMode classes initialised before the app had a context
 *       (the fields then keep their default 0 = PERFORMANCE_CLASS_LOW and {@code loadConfig} is
 *       never retried) must be repaired by the app's own startup, or a flagship phone shows the
 *       opaque glass for the life of the process.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = NativeDoApplication::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class GlassGateTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        // Robolectric gives every test method a fresh app data directory but reuses the sandbox's
        // statics, so a singleton built by an earlier test would keep writing to (and reading from)
        // that earlier test's files. Drop it, the way the harness drops upstream's image and
        // photo-viewer singletons at boot.
        runCatching {
            val field = MessagesController::class.java.getDeclaredField("Instance").apply { isAccessible = true }
            field.set(null, arrayOfNulls<MessagesController>(4))
        }
        site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        // Robolectric's battery reports 0%, which upstream's own power-saver rule turns into
        // PRESET_POWER_SAVER; the gate under test is the class preset, not the power saver.
        LiteMode.setPowerSaverLevel(0)
    }

    @After
    fun tearDown() {
        site.stop()
        SharedConfig.overrideDevicePerformanceClass(-1)
        MessagesController.getGlobalMainSettings().edit().remove("lite_mode6").apply()
        NativeDo.setContainerFactory { context -> AppContainer(context) }
    }

    /**
     * What the capsules actually sample: the ActionBar's glass drawable carries the source the
     * factory gave it, so the assertion is on the object the header draws, not on a flag.
     */
    private fun headerSource(): Any {
        val fragment = harness.visibleFragment() as TopicFragment
        val actionBarField = BaseFragment::class.java.getDeclaredField("actionBar").apply { isAccessible = true }
        val actionBar = actionBarField.get(fragment) as ActionBar
        val glassField = ActionBar::class.java.getDeclaredField("glassDrawable").apply { isAccessible = true }
        val drawable = glassField.get(actionBar) as BlurredBackgroundDrawable
        return drawable.source
    }

    private fun openTopic() {
        harness.bootApp()
        harness.await("the shell root") { harness.visibleFragment() != null }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Welcome to NativeDo Android"))
    }

    @Test
    fun aLowDeviceKeepsUpstreamsOpaqueHeader() {
        SharedConfig.overrideDevicePerformanceClass(-1)
        LiteMode.setAllFlags(LiteMode.PRESET_LOW)

        openTopic()

        // This sandbox has one CPU, so upstream itself stands the header over its flat colour
        // here; that is the fallback, not a bug.
        assertFalse(
            "a LOW device must not take a render node",
            headerSource() is BlurredBackgroundSourceRenderNode,
        )
        assertTrue(
            "the LOW header samples upstream's flat colour source",
            headerSource() is BlurredBackgroundSourceColor,
        )
    }

    @Test
    fun aBlurringDeviceSamplesTheRenderNode() {
        // The same state a phone that upstream blurs on reaches: performance class HIGH, the
        // chat-blur wire on (upstream's debug class override and its Power Saving flag).
        SharedConfig.overrideDevicePerformanceClass(SharedConfig.PERFORMANCE_CLASS_HIGH)
        LiteMode.setAllFlags(LiteMode.PRESET_HIGH)

        openTopic()

        assertTrue(
            "on a HIGH class with the chat blur on, the header's source is the render node",
            headerSource() is BlurredBackgroundSourceRenderNode,
        )
    }

    @Test
    fun theStartupHookRepairsAClassThatInitialisedBeforeTheAppHadAContext() {
        // Type the state a context-less static initialiser leaves: loadConfig() returned early
        // (configLoaded false, both fields at their default zero = LOW) and LiteMode cached the
        // preset that followed that zero.
        setField(SharedConfig::class.java, "configLoaded", false)
        setField(SharedConfig::class.java, "overrideDevicePerformanceClass", 0)
        setField(SharedConfig::class.java, "devicePerformanceClass", 0)
        setField(LiteMode::class.java, "loaded", true)
        setField(LiteMode::class.java, "value", LiteMode.PRESET_LOW)
        RuntimeEnvironment.getApplication().getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE)
            .edit()
            .remove("lite_mode6")
            .putInt("overrideDevicePerformanceClass", SharedConfig.PERFORMANCE_CLASS_HIGH)
            .apply()
        assertFalse(
            "the broken state is exactly the opaque branch",
            SharedConfig.chatBlurEnabled(),
        )

        NativeDoApplication.loadRuntimeConfig()

        assertEquals(
            "the startup hook resolves the class the preference names",
            SharedConfig.PERFORMANCE_CLASS_HIGH,
            SharedConfig.getDevicePerformanceClass(),
        )
        assertTrue(
            "and the gate followed it, as upstream's startup would have",
            SharedConfig.chatBlurEnabled(),
        )
    }

    private fun setField(clazz: Class<*>, name: String, value: Any) {
        val field = clazz.getDeclaredField(name).apply { isAccessible = true }
        field.set(null, value)
    }

    private companion object {
        const val TOPIC_ID = 101L
    }
}

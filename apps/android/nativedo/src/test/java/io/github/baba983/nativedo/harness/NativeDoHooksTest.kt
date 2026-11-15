package io.github.baba983.nativedo.harness

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
import io.github.baba983.nativedo.app.NativeDoApplication
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesStorage
import org.telegram.tgnet.ConnectionsManager
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheetTabs
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay
import org.telegram.ui.ActionBar.INavigationLayout

/**
 * Proves the two hooks are what keep Telegram out of this client, by turning them off on the same
 * code path, and that the browser host belongs to the shell that registered it. Without this,
 * "tgnet never initialised" and "no database was opened" could pass merely because nothing ever
 * constructed the objects that would do it, and a second shell's destroy could silently take a
 * live one's host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
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
internal class NativeDoHooksTest {

    private val harness = UpstreamUiHarness()

    @Test
    fun mtprotoHookDecidesWhetherTgnetInitialises() {
        harness.bootViews()

        NativeDoHooks.setMtprotoEnabled(true)
        ConnectionsManager(1)
        val withHookOff = ShadowConnectionsManager.NATIVE_INIT_CALLS
        println("hooks: mtproto enabled  -> native_init=$withHookOff")

        NativeDoHooks.setMtprotoEnabled(false)
        ConnectionsManager(2)
        val withHookOn = ShadowConnectionsManager.NATIVE_INIT_CALLS
        println("hooks: mtproto disabled -> native_init=$withHookOn")

        assertEquals("the probe is blind: tgnet did not initialise even with the hook off", 1, withHookOff)
        assertEquals("the hook did not stop tgnet", withHookOff, withHookOn)
    }

    @Test
    fun storageHookDecidesWhetherTheTelegramDatabaseOpens() {
        harness.bootViews()

        // Calls openDatabase on this thread rather than through the storage queue: the queue's
        // open-retry loop never settles against a stubbed SQLite, and the question here is only
        // whether the hook stops the open, which the same call answers either way.
        NativeDoHooks.setTelegramStorageEnabled(true)
        MessagesStorage.getInstance(0).openDatabase(1)
        val withHookOff = ShadowSQLiteDatabase.OPEN_DB_CALLS
        println("hooks: storage enabled  -> sqlite_opendb=$withHookOff")

        NativeDoHooks.setTelegramStorageEnabled(false)
        ShadowSQLiteDatabase.resetCounters()
        MessagesStorage.getInstance(0).openDatabase(1)
        val withHookOn = ShadowSQLiteDatabase.OPEN_DB_CALLS
        println("hooks: storage disabled -> sqlite_opendb=$withHookOn")

        assertTrue("the probe is blind: the database did not open even with the hook off", withHookOff > 0)
        assertEquals("the hook did not stop the database", 0, withHookOn)
    }

    /**
     * The app's own startup switch, on the real gate: with the hooks left at their upstream
     * defaults tgnet initialises and the database opens; after the call
     * {@code NativeDoApplication}'s static initializer makes, neither does. Without this the
     * defaults could flip and the suite would stay green while a shipped app started Telegram's
     * network and storage.
     */
    @Test
    fun theApplicationStartupSwitchKeepsTgnetAndTheDatabaseOff() {
        harness.bootViews()
        NativeDoHooks.setMtprotoEnabled(true)
        NativeDoHooks.setTelegramStorageEnabled(true)
        ShadowConnectionsManager.resetCounters()
        ShadowSQLiteDatabase.resetCounters()

        NativeDoApplication.disableTelegramServices()
        ConnectionsManager(3)
        MessagesStorage.getInstance(0).openDatabase(1)

        assertEquals("tgnet initialised after the app's own switch", 0, ShadowConnectionsManager.NATIVE_INIT_CALLS)
        assertEquals("the Telegram database opened after the app's own switch", 0, ShadowSQLiteDatabase.OPEN_DB_CALLS)
    }

    /**
     * The host belongs to its activity: a second shell's host survives the first shell's destroy,
     * and an older handle cannot clear a newer registration for the same activity. This is the
     * ordering a VIEW link produces (a second instance in the caller's task, destroyed first) and
     * the one a single static host got wrong.
     */
    @Test
    fun aDestroyedShellClearsOnlyItsOwnHost() {
        NativeDoHooks.resetBrowserHosts()
        val first = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val second = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val firstHost = FakeBrowserHost()
        val secondHost = FakeBrowserHost()

        NativeDoHooks.setBrowserHost(first, firstHost)
        NativeDoHooks.setBrowserHost(second, secondHost)
        assertTrue("the first shell lost its own host", NativeDoHooks.browserHostPresent(first))
        assertTrue("the second shell lost its own host", NativeDoHooks.browserHostPresent(second))

        // The first shell dies while the second is still alive: only its own entry goes.
        NativeDoHooks.clearBrowserHost(first, firstHost)
        assertFalse("the destroyed shell still answers", NativeDoHooks.browserHostPresent(first))
        assertTrue("the surviving shell lost its host", NativeDoHooks.browserHostPresent(second))

        // A newer registration for the same activity outlives the older handle's clear.
        val replacement = FakeBrowserHost()
        NativeDoHooks.setBrowserHost(first, replacement)
        NativeDoHooks.clearBrowserHost(first, firstHost)
        assertTrue("a stale clear removed the newer host", NativeDoHooks.browserHostPresent(first))
        NativeDoHooks.clearBrowserHost(first, replacement)
        assertFalse("the activity's own host survived its destroy", NativeDoHooks.browserHostPresent(first))
        NativeDoHooks.resetBrowserHosts()
    }

    /**
     * The window's FLAG_SECURE gate is per message: a post this client built never secures the
     * window, while a message it did not build keeps upstream's answer - the one that protects
     * Telegram's paid and view-once media in a real host. E2E cannot reach the second branch,
     * because this app never builds a non-client message.
     */
    @Test
    fun onlyClientBuiltMessagesSkipTheWindowSecureGate() {
        harness.bootViews()
        val clientMessage = io.github.baba983.nativedo.topic.DiscourseRows.localMessage(
            1, 0, 42L, 7L, org.telegram.tgnet.TLRPC.TL_messageMediaEmpty(),
        )
        val clientObject = io.github.baba983.nativedo.topic.DiscourseRows.messageObject(
            clientMessage, HashMap(),
        )
        assertFalse(
            "a post this client built secured the window",
            NativeDoHooks.messageMaySecureWindow(clientObject),
        )

        val foreignMessage = org.telegram.tgnet.TLRPC.TL_message().apply {
            id = 2
            noforwards = true
        }
        val foreignObject = io.github.baba983.nativedo.topic.DiscourseRows.messageObject(
            foreignMessage, HashMap(),
        )
        assertTrue(
            "a message this client did not build lost upstream's secure gate",
            NativeDoHooks.messageMaySecureWindow(foreignObject),
        )
    }

    /** A host with no browser: only its registration identity matters here. */
    private class FakeBrowserHost : NativeDoHooks.BrowserHost {
        override fun getBottomSheetTabs(): BottomSheetTabs? = null
        override fun getBottomSheetTabsOverlay(): BottomSheetTabsOverlay? = null
        override fun getLastFragment(): BaseFragment? = null
        override fun getSafeLastFragment(): BaseFragment? = null
        override fun checkSystemBarColors() {}
        override fun getSheetFragmentsStack(): ArrayList<INavigationLayout>? = null
        override fun createBrowserSettings(): BaseFragment? = null
    }
}
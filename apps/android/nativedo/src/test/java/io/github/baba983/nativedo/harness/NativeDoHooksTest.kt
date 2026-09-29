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
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesStorage
import org.telegram.tgnet.ConnectionsManager

/**
 * Proves the two hooks are what keep Telegram out of this client, by turning them off on the same
 * code path. Without this, "tgnet never initialised" and "no database was opened" could pass
 * merely because nothing ever constructed the objects that would do it.
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
}
package io.github.baba983.nativedo.session

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.baba983.nativedo.core.session.SessionDiagnostics
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Resource retention, opt-in persistence and export isolation not exercised by the UI flow. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SessionDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun recordingSurvivesRestartAndExportsOnlyBoundedRecentAllowlistedEvents() {
        SessionDiagnostics.initialize(context)
        assertFalse(SessionDiagnostics.isEnabled(context))
        SessionDiagnostics.setEnabled(context, true)
        val restarted = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
        }
        SessionDiagnostics.initialize(restarted)
        assertTrue("the opt-in survives reinitialization", SessionDiagnostics.isEnabled(restarted))
        File(context.filesDir, "unrelated-private-data").writeText("PRIVATE-DATA-MARKER")
        repeat(5000) { revision ->
            SessionDiagnostics.response(revision.toLong(), 403, false, "Challenge", "session", "other",
                true, true, true, false, false, false)
        }
        SessionDiagnostics.record("PRIVATE-EVENT-MARKER", 0)
        SessionDiagnostics.response(5000, 403, false, "PRIVATE-EVIDENCE-MARKER", "PRIVATE-URL-MARKER",
            "PRIVATE-OPERATION-MARKER", true, false, false, false, false, false)
        val archive = await<File?> { SessionDiagnostics.export(restarted, it) }!!
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals(setOf("build.txt", "session.log", "session.previous.log"), entries.map { it.name }.toSet())
            val logs = entries.filter { it.name.endsWith(".log") }
            assertTrue("the input must exercise rotation", logs.all { it.size > 0 })
            assertTrue("each retained event file is capped", logs.all { it.size <= 256 * 1024 })
            val text = entries.joinToString("\n") { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }
            assertTrue("recent responses survive rotation", text.contains("revision=4999 "))
            assertFalse("old responses are evicted", text.contains("revision=0 status=403"))
            assertFalse("unknown strings and unrelated files never enter the archive", text.contains("PRIVATE-"))
        }
        SessionDiagnostics.setEnabled(restarted, false)
    }

    @Test
    fun disablingAndClearingDoNotRestoreOldOrQueuedEventsOrTheSharedArchive() {
        SessionDiagnostics.initialize(context)
        SessionDiagnostics.setEnabled(context, true)
        repeat(1000) { SessionDiagnostics.record("login_committed", it.toLong()) }
        val shared = await<File?> { SessionDiagnostics.export(context, it) }!!
        assertTrue(shared.exists())
        SessionDiagnostics.setEnabled(context, false)
        SessionDiagnostics.record("login_committed", 99999)
        assertTrue(await<Boolean> { SessionDiagnostics.clear(context, it) })
        assertFalse("clear also removes the previously shared ZIP", shared.exists())
        val empty = await<File?> { SessionDiagnostics.export(context, it) }!!
        ZipFile(empty).use { zip ->
            assertEquals("only build metadata remains after clear", listOf("build.txt"),
                zip.entries().asSequence().map { it.name }.toList())
        }
        assertFalse("export and clear never enable recording", SessionDiagnostics.isEnabled(context))
    }

    private fun <T> await(start: (Consumer<T>) -> Unit): T {
        val value = AtomicReference<T>()
        val complete = java.util.concurrent.atomic.AtomicBoolean(false)
        start(Consumer { value.set(it); complete.set(true) })
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
        val looper = shadowOf(Looper.getMainLooper())
        while (!complete.get() && System.nanoTime() < deadline) {
            if (!looper.isIdle) looper.runOneTask() else Thread.sleep(2)
        }
        assertTrue("diagnostics operation completed", complete.get())
        return value.get()
    }
}

package io.github.baba983.nativedo.core.session

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.baba983.nativedo.BuildConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Opt-in, bounded session evidence; never arbitrary logs, URLs, identities or cookie values. */
internal object SessionDiagnostics {
    private const val PREFERENCES = "nativedo_diagnostics"
    private const val MAX_FILE_BYTES = 256 * 1024
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "NativeDo-session-diagnostics").apply { isDaemon = true }
    }
    private var application: Context? = null
    private var enabled = false
    private var generation = 0L
    private val events = setOf("app_started", "diagnostics_enabled", "webview_login_unreadable",
        "login_committed", "auth_cookie_missing", "legacy_cookie_migration", "fenced_read_returned") +
        SessionExpiredReason.values().map { "expired_${it.name}" }

    @JvmStatic
    fun initialize(context: Context) {
        synchronized(lock) {
            val app = context.applicationContext
            if (application === app) return
            application = app
            generation++
            enabled = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean("enabled", false)
            record("app_started", 0)
        }
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        initialize(context)
        return synchronized(lock) { enabled }
    }

    @JvmStatic
    fun setEnabled(context: Context, value: Boolean) {
        initialize(context)
        synchronized(lock) {
            generation++ // Queued writes from a previous recording interval cannot resume later.
            enabled = value
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean("enabled", value).apply()
            if (value) record("diagnostics_enabled", 0)
        }
    }

    fun response(revision: Long, status: Int, accepted: Boolean, evidence: String, route: String,
        operation: String,
        hasToken: Boolean, hasForumSession: Boolean, hasClearance: Boolean, hasRuntime: Boolean,
        setsToken: Boolean, deletesToken: Boolean) {
        // These strings are closed categories, not a future route for logging a URL/body by mistake.
        val safeEvidence = evidence.takeIf { it in SessionResponseEvidenceNames } ?: "Other"
        val safeRoute = route.takeIf { it in setOf("session", "csrf", "feed", "chat", "other") } ?: "other"
        val safeOperation = operation.takeIf { it in setOf("posts", "timings", "other") } ?: "other"
        append("event=native_response revision=$revision status=$status accepted=$accepted " +
            "evidence=$safeEvidence route=$safeRoute operation=$safeOperation hasToken=$hasToken hasForumSession=$hasForumSession " +
            "hasClearance=$hasClearance hasRuntime=$hasRuntime setsToken=$setsToken deletesToken=$deletesToken")
    }

    private val SessionResponseEvidenceNames = setOf("Challenge", "LoggedOut", "Csrf", "Forbidden", "RateLimited", "Other")

    fun record(event: String, revision: Long, status: Int = 0, accepted: Boolean = true) {
        if (event in events) append("event=$event revision=$revision status=$status accepted=$accepted")
    }

    private fun directory(context: Context) = File(context.noBackupFilesDir, "session-diagnostics")
    private fun archive(context: Context) = File(context.cacheDir, "diagnostics/session-diagnostics.zip")

    private fun append(event: String) {
        synchronized(lock) {
            if (!enabled) return
            val app = application ?: return
            val epoch = generation
            val line = "time_ms=${System.currentTimeMillis()} $event\n".toByteArray(Charsets.UTF_8)
            writer.execute {
                synchronized(lock) {
                    if (!enabled || epoch != generation) return@execute
                    // Storage or logging failure must never change the request/session result.
                    runCatching {
                        val dir = directory(app).apply { mkdirs() }
                        val current = File(dir, "session.log")
                        if (current.length() + line.size > MAX_FILE_BYTES) {
                            val previous = File(dir, "session.previous.log")
                            check(!previous.exists() || previous.delete())
                            check(current.renameTo(previous))
                        }
                        current.appendBytes(line)
                        Log.i("NativeDoSession", event)
                    }
                }
            }
        }
    }

    /** Runs after pending writes, and shares only the explicitly named files, never a directory scan. */
    @JvmStatic
    fun export(context: Context, callback: Consumer<File?>) {
        initialize(context)
        val app = context.applicationContext
        writer.execute {
            val zip = archive(app)
            val result = runCatching {
                zip.parentFile!!.mkdirs()
                ZipOutputStream(zip.outputStream().buffered()).use { output ->
                    output.putNextEntry(ZipEntry("build.txt"))
                    output.write(("format=1\npackage=${BuildConfig.APPLICATION_ID}\n" +
                        "version=${BuildConfig.VERSION_NAME}\nversion_code=${BuildConfig.VERSION_CODE}\n" +
                        "build_type=${BuildConfig.BUILD_TYPE}\nandroid_sdk=${Build.VERSION.SDK_INT}\n" +
                        "export_time_ms=${System.currentTimeMillis()}\nrecording=${isEnabled(app)}\n").toByteArray(Charsets.UTF_8))
                    output.closeEntry()
                    for (name in listOf("session.previous.log", "session.log")) {
                        val file = File(directory(app), name)
                        if (file.exists()) {
                            output.putNextEntry(ZipEntry(name))
                            file.inputStream().use { it.copyTo(output) }
                            output.closeEntry()
                        }
                    }
                }
                zip
            }.getOrElse { zip.delete(); null }
            Handler(Looper.getMainLooper()).post { callback.accept(result) }
        }
    }

    @JvmStatic
    fun clear(context: Context, callback: Consumer<Boolean>) {
        initialize(context)
        synchronized(lock) {
            generation++
            val app = context.applicationContext
            writer.execute {
                val success = runCatching {
                    (listOf("session.log", "session.previous.log").map { File(directory(app), it) } + archive(app))
                        .map { !it.exists() || it.delete() }.all { it }
                }.getOrDefault(false)
                Handler(Looper.getMainLooper()).post { callback.accept(success) }
            }
        }
    }
}

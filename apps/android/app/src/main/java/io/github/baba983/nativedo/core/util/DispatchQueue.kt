package io.github.baba983.nativedo.core.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.CountDownLatch

class DispatchQueue(
    threadName: String,
) : Thread(threadName) {
    @Volatile
    private var handler: Handler? = null

    private val syncLatch = CountDownLatch(1)

    @Volatile
    private var lastTaskTime: Long = 0L

    init {
        start()
    }

    fun sendMessage(
        message: Message,
        delayMillis: Int,
    ) {
        val targetHandler = awaitHandler() ?: return
        if (delayMillis <= 0) {
            targetHandler.sendMessage(message)
        } else {
            targetHandler.sendMessageDelayed(message, delayMillis.toLong())
        }
    }

    fun postRunnable(runnable: Runnable): Boolean {
        lastTaskTime = SystemClock.elapsedRealtime()
        return postRunnable(runnable, 0L)
    }

    fun postToFrontRunnable(runnable: Runnable): Boolean {
        return awaitHandler()?.postAtFrontOfQueue(runnable) ?: false
    }

    fun postRunnable(
        runnable: Runnable,
        delayMillis: Long,
    ): Boolean {
        val targetHandler = awaitHandler() ?: return false
        return if (delayMillis <= 0L) {
            targetHandler.post(runnable)
        } else {
            targetHandler.postDelayed(runnable, delayMillis)
        }
    }

    fun cancelRunnable(runnable: Runnable) {
        awaitHandler()?.removeCallbacks(runnable)
    }

    fun cancelRunnables(runnables: Array<Runnable>) {
        val targetHandler = awaitHandler() ?: return
        runnables.forEach(targetHandler::removeCallbacks)
    }

    fun cleanupQueue() {
        awaitHandler()?.removeCallbacksAndMessages(null)
    }

    fun getLastTaskTime(): Long = lastTaskTime

    fun isReady(): Boolean = syncLatch.count == 0L

    fun recycle() {
        awaitHandler()?.looper?.quit()
    }

    fun shutdown() {
        recycle()
    }

    open fun handleMessage(message: Message) = Unit

    fun getHandler(): Handler? = handler

    override fun run() {
        Looper.prepare()
        handler = Handler(Looper.myLooper()!!) { message ->
            handleMessage(message)
            true
        }
        syncLatch.countDown()
        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        Looper.loop()
    }

    private fun awaitHandler(): Handler? {
        return try {
            syncLatch.await()
            handler
        } catch (_: InterruptedException) {
            interrupt()
            null
        }
    }
}

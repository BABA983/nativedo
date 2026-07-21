package org.telegram.messenger

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileLoaderPriorityQueueContractTest {
    @Test
    fun addOrdersByPriorityAndReaddingMovesOperation() {
        val queue = FileLoaderPriorityQueue(0, "test", FileLoaderPriorityQueue.TYPE_SMALL, TestDispatchQueue())
        val low = TestOperation(10)
        val high = TestOperation(20)
        val middle = TestOperation(15)

        queue.add(low)
        queue.add(high)
        queue.add(middle)
        assertThat(queue.allOperations).containsExactly(high, middle, low).inOrder()

        low.priority = 30
        queue.add(low)
        assertThat(queue.allOperations).containsExactly(low, high, middle).inOrder()
    }

    @Test
    fun immediateCheckStartsOnlyConfiguredNumberOfOperations() {
        val queue = FileLoaderPriorityQueue(0, "test", FileLoaderPriorityQueue.TYPE_SMALL, TestDispatchQueue())
        val operations = (0 until 6).map { TestOperation(100 - it) }
        operations.forEach(queue::add)

        queue.checkLoadingOperations(true)

        assertThat(operations.take(5).map { it.startCount }).containsExactly(1, 1, 1, 1, 1)
        assertThat(operations[5].startCount).isEqualTo(0)
    }

    @Test
    fun lowerPriorityStartedOperationIsPausedAfterPriorityBoundary() {
        val queue = FileLoaderPriorityQueue(0, "test", FileLoaderPriorityQueue.TYPE_SMALL, TestDispatchQueue())
        val active = TestOperation(100).apply { started = true }
        val low = TestOperation(FileLoaderPriorityQueue.PRIORITY_VALUE_LOW).apply { started = true }
        queue.add(active)
        queue.add(low)

        queue.checkLoadingOperations(true)

        assertThat(low.pauseCount).isEqualTo(1)
    }

    private class TestDispatchQueue : DispatchQueue("file-loader-test", false) {
        override fun cancelRunnable(runnable: Runnable) = Unit
        override fun postRunnable(runnable: Runnable, delay: Long): Boolean {
            runnable.run()
            return true
        }
    }

    private class TestOperation(priority: Int) : FileLoadOperation() {
        var startCount = 0
        var pauseCount = 0
        var cancelCount = 0
        var started = false

        init {
            this.priority = priority
        }

        override fun start(): Boolean {
            startCount++
            started = true
            return true
        }

        override fun pause() {
            pauseCount++
            started = false
        }

        override fun cancel() {
            cancelCount++
            started = false
        }

        override fun wasStarted() = started
    }
}

package io.github.baba983.nativedo.topic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Two downloads of one cache file that overlap.
 *
 * <p>The failure this pins: both writers use one temporary name (`<target>.part`), so the second
 * download truncates the file the first is still writing and the two move a mixture into place.
 * The bytes a reader then finds at the cache path are neither download's, and a writer that fails
 * (a cancelled transfer) deletes the shared temporary name out from under a live one. The oracle is
 * the final file's contents, not a flag: it has to be exactly one writer's whole payload, and a
 * failure has to take only its own bytes.</p>
 */
internal class AtomicFileWriteTest {

    @Test
    fun overlappingWritersLeaveOneWholeFileAndNoTemporary() {
        val dir = newDir()
        try {
            val target = File(dir, "picture.png")
            val first = ByteArray(120_000) { 0xAA.toByte() }
            val second = ByteArray(80_000) { 0xBB.toByte() }

            val firstHalfWritten = CountDownLatch(1)
            val secondCommitted = CountDownLatch(1)
            val firstFinished = CountDownLatch(1)
            val firstError = AtomicReference<Throwable?>(null)
            val secondError = AtomicReference<Throwable?>(null)
            val firstCommit = AtomicBoolean(false)
            val secondCommit = AtomicBoolean(false)

            // The first writer stops halfway and waits until the second has written and moved its
            // whole payload; then it writes the rest and moves its own in. Both moves have to land -
            // the last one wins - so the file has to be exactly the first writer's whole payload.
            // A shared temporary name would let the second's truncation and the first's resumed
            // write mix, and one of the two moves would fail.
            val a = Thread {
                try {
                    firstCommit.set(
                        AtomicFileWrite.write(target) { output ->
                            output.write(first, 0, 60_000)
                            output.flush()
                            firstHalfWritten.countDown()
                            secondCommitted.await(5, TimeUnit.SECONDS)
                            output.write(first, 60_000, 60_000)
                        },
                    )
                } catch (error: Throwable) {
                    firstError.set(error)
                } finally {
                    firstFinished.countDown()
                }
            }
            val b = Thread {
                try {
                    firstHalfWritten.await(5, TimeUnit.SECONDS)
                    secondCommit.set(AtomicFileWrite.write(target) { output -> output.write(second) })
                } catch (error: Throwable) {
                    secondError.set(error)
                } finally {
                    secondCommitted.countDown()
                }
            }
            a.start()
            b.start()
            assertTrue("the first writer finished", firstFinished.await(5, TimeUnit.SECONDS))
            assertTrue("the second writer finished", secondCommitted.await(5, TimeUnit.SECONDS))
            a.join(5_000)
            b.join(5_000)

            assertNull("the first writer ended without an error", firstError.get())
            assertNull("the second writer ended without an error", secondError.get())
            assertTrue("the first writer's move landed", firstCommit.get())
            assertTrue("the second writer's move landed", secondCommit.get())
            assertTrue(
                "the cache file is the first writer's whole payload (the last move wins), not a mixture",
                target.readBytes().contentEquals(first),
            )
            assertNoTemporaryLeft(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aWriterThatFailsTakesOnlyItsOwnTemporary() {
        val dir = newDir()
        try {
            val target = File(dir, "picture.png")
            val survivorBytes = ByteArray(50_000) { 0xCC.toByte() }

            val survivorHalfWritten = CountDownLatch(1)
            val doomedFailed = CountDownLatch(1)
            val survivorFinished = CountDownLatch(1)
            val survivorError = AtomicReference<Throwable?>(null)
            val survivorCommit = AtomicBoolean(false)

            // The survivor is halfway through its own temporary when the doomed writer fails and
            // cleans up. With a private temporary per write, that cleanup cannot see the survivor's
            // bytes, and the survivor's resumed write and move have to land whole.
            val survivor = Thread {
                try {
                    survivorCommit.set(
                        AtomicFileWrite.write(target) { output ->
                            output.write(survivorBytes, 0, 25_000)
                            output.flush()
                            survivorHalfWritten.countDown()
                            doomedFailed.await(5, TimeUnit.SECONDS)
                            output.write(survivorBytes, 25_000, 25_000)
                        },
                    )
                } catch (error: Throwable) {
                    survivorError.set(error)
                } finally {
                    survivorFinished.countDown()
                }
            }
            val doomed = Thread {
                try {
                    survivorHalfWritten.await(5, TimeUnit.SECONDS)
                    runCatching {
                        AtomicFileWrite.write(target) { output ->
                            output.write(ByteArray(1_000) { 0xDD.toByte() })
                            throw IOException("cancelled")
                        }
                    }
                } finally {
                    doomedFailed.countDown()
                }
            }
            survivor.start()
            doomed.start()
            assertTrue("the failed writer is over", doomedFailed.await(5, TimeUnit.SECONDS))
            assertTrue("the survivor finished", survivorFinished.await(5, TimeUnit.SECONDS))
            survivor.join(5_000)
            doomed.join(5_000)

            assertNull("the surviving writer ended without an error", survivorError.get())
            assertTrue("the surviving writer's move landed", survivorCommit.get())
            assertTrue(
                "the failed writer left the survivor's file whole",
                target.readBytes().contentEquals(survivorBytes),
            )
            assertNoTemporaryLeft(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun newDir(): File =
        File(System.getProperty("java.io.tmpdir"), "nativedo-atomic-" + System.nanoTime()).apply { mkdirs() }

    private fun assertNoTemporaryLeft(dir: File) {
        val leftovers = dir.listFiles { file -> file.name.endsWith(AtomicFileWrite.TEMP_SUFFIX) }
        assertEquals("no temporary file was left behind", 0, leftovers?.size ?: 0)
    }
}

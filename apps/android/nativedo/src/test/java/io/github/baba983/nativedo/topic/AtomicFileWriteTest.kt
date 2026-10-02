package io.github.baba983.nativedo.topic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            val firstCommitted = CountDownLatch(1)

            // The first writer stops halfway and waits until the second has written and moved its
            // whole payload. With a shared temporary name the second's truncation and the first's
            // resumed write land in the same file; with a private name they cannot.
            val a = Thread {
                AtomicFileWrite.write(target) { output ->
                    output.write(first, 0, 60_000)
                    output.flush()
                    firstHalfWritten.countDown()
                    secondCommitted.await(5, TimeUnit.SECONDS)
                    output.write(first, 60_000, 60_000)
                }
                firstCommitted.countDown()
            }
            val b = Thread {
                firstHalfWritten.await(5, TimeUnit.SECONDS)
                AtomicFileWrite.write(target) { output -> output.write(second) }
                secondCommitted.countDown()
            }
            a.start()
            b.start()
            assertTrue("the first writer finished", firstCommitted.await(5, TimeUnit.SECONDS))
            assertTrue("the second writer finished", secondCommitted.await(5, TimeUnit.SECONDS))

            val written = target.readBytes()
            val whole = written.contentEquals(first) || written.contentEquals(second)
            assertTrue(
                "the cache file is one download's whole payload, not a mixture (was ${written.size} bytes)",
                whole,
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

            val doomedStarted = CountDownLatch(1)
            val doomedFailed = CountDownLatch(1)
            val survivorCommitted = CountDownLatch(1)

            // The doomed writer has written a little when the survivor starts and commits its whole
            // payload; the doomed writer then throws, the way a cancelled transfer does. Its cleanup
            // must not touch the file the survivor already moved into place.
            val doomed = Thread {
                runCatching {
                    AtomicFileWrite.write(target) { output ->
                        output.write(ByteArray(1_000) { 0xDD.toByte() })
                        output.flush()
                        doomedStarted.countDown()
                        survivorCommitted.await(5, TimeUnit.SECONDS)
                        throw IOException("cancelled")
                    }
                }
                doomedFailed.countDown()
            }
            val survivor = Thread {
                doomedStarted.await(5, TimeUnit.SECONDS)
                AtomicFileWrite.write(target) { output -> output.write(survivorBytes) }
                survivorCommitted.countDown()
            }
            doomed.start()
            survivor.start()
            assertTrue("the failed writer is over", doomedFailed.await(5, TimeUnit.SECONDS))
            assertTrue("the survivor finished", survivorCommitted.await(5, TimeUnit.SECONDS))

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

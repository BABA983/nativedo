package io.github.baba983.nativedo.core.media

import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The lossless AAC editor behind the composer's trim/join review: it crops and concatenates an
 * `.m4a` by its real container sample table, without re-encoding.
 *
 * <p>These are the failure modes it earns its place on, none of which a constant mirror could
 * catch:</p>
 * <ul>
 *   <li><b>A trim is not a no-op and not the whole file.</b> A real window must retain exactly the
 *       frames inside it - a strict, non-empty subset - as a contiguous, byte-identical slice of the
 *       input's own samples. A crop that returned the input, an empty file, or re-encoded audio
 *       fails here.</li>
 *   <li><b>Bounds and empty selections are refused, not silently repaired.</b> A negative start, a
 *       non-positive window, a window past the end, or a window with no whole sample inside it must
 *       throw and leave the input alone; "clamp it and carry on" is exactly the fallback the caller
 *       forbade.</li>
 *   <li><b>The whole recording is fixed.</b> Trimming the full span keeps every sample, so the
 *       boundary math does not nibble the tail.</li>
 *   <li><b>Join preserves order and adds durations.</b> `A+B` is the samples of `A` followed by
 *       those of `B`; `B+A` is a different file, and the reported duration is the retained span, not
 *       an estimate. A join that sorted tracks, dropped the junction or reordered samples fails
 *       here.</li>
 *   <li><b>Incompatible inputs are refused.</b> A different sample rate/channel layout, an input
 *       with no audio track, and a file that is not a readable MP4 must all throw rather than
 *       writing a broken container.</li>
 *   <li><b>A failure writes nothing and never touches the inputs.</b> When the output cannot be
 *       created, the input bytes are unchanged and no partial file is left behind.</li>
 * </ul>
 *
 * <p>The fixtures under `src/test/resources/voice-editor` are real, small AAC files generated once
 * with ffmpeg (`voice-alpha.m4a` = 1.5 s pink noise, mono 44.1 kHz; `voice-beta.m4a` = 1.0 s 880 Hz
 * tone, mono 44.1 kHz; `voice-wide.m4a` = 0.5 s white noise, stereo 48 kHz; `silent-video.mp4` =
 * a video-only clip). The tests read their actual sample tables through `isoparser` and compare the
 * editor's output against them rather than against a recorded extractor interaction.</p>
 */
internal class VoiceRecordingEditorTest {

    private lateinit var workspace: File
    private val editor = VoiceRecordingEditor()

    @Before
    fun setUp() {
        workspace = Files.createTempDirectory("voice-editor-test").toFile()
    }

    @After
    fun tearDown() {
        workspace.walkBottomUp().forEach { it.setWritable(true, false) }
        workspace.deleteRecursively()
    }

    // ---------------------------------------------------------------------------------------------
    // Trim
    // ---------------------------------------------------------------------------------------------

    @Test
    fun trimRetainsOnlyTheFramesInsideTheWindow() {
        val input = fixture("voice-alpha.m4a")
        val inputAudio = audioOf(input)
        val inputHash = sha256(input)
        val startMillis = 250L
        val endMillis = 1050L

        val trimmed = editor.trim(recording(input, inputAudio), startMillis, endMillis)

        val outAudio = audioOf(trimmed.file)

        // Independent oracle: every full AAC frame here is 1024 samples at 44.1 kHz, so the retained
        // frame range follows from the requested window alone - not from the editor's own math.
        val frameMillis = FULL_AAC_FRAME_SAMPLES.toDouble() * 1000.0 / inputAudio.timescale
        val expectedFirstFrame = ceil(startMillis / frameMillis).toInt()
        val expectedLastFrameExclusive = floor(endMillis / frameMillis).toInt()
        val expectedFrames = expectedLastFrameExclusive - expectedFirstFrame

        assertTrue("a real trim must drop samples", expectedFrames in 1 until inputAudio.samples.size)
        assertEquals("retains exactly the frames inside the window", expectedFrames, outAudio.samples.size)

        // Lossless: the output is a contiguous, byte-identical slice of the input's own samples.
        val offset = inputAudio.samples.indexOfFirst { it.contentEquals(outAudio.samples[0]) }
        assertEquals("the crop starts at the window's first frame", expectedFirstFrame, offset)
        for (i in outAudio.samples.indices) {
            assertTrue(
                "sample $i must equal the input's sample ${offset + i}",
                inputAudio.samples[offset + i].contentEquals(outAudio.samples[i]),
            )
        }

        // The reported duration is the retained span as the container actually stores it.
        assertEquals("duration matches the written container", outAudio.durationMillis, trimmed.durationMillis)
        assertTrue(
            "duration is within one frame of the requested window",
            abs(trimmed.durationMillis - (endMillis - startMillis)) <= frameMillis.toLong() + 1,
        )

        // A new sibling file, never the input, and never a mutation of it.
        assertEquals(input.parentFile, trimmed.file.parentFile)
        assertTrue("the output is a fresh file", trimmed.file != input)
        assertTrue("the output exists and is non-empty", trimmed.file.length() > 0L)
        assertEquals(trimmed.file.name, trimmed.name)
        assertTrue("the input is untouched", input.exists() && sha256(input) == inputHash)
    }

    @Test
    fun trimmingTheWholeRecordingKeepsEverySample() {
        val input = fixture("voice-alpha.m4a")
        val inputAudio = audioOf(input)

        val trimmed = editor.trim(recording(input, inputAudio), 0L, inputAudio.durationMillis)

        val outAudio = audioOf(trimmed.file)
        assertEquals("a full-span trim keeps every sample", inputAudio.samples.size, outAudio.samples.size)
        assertEquals(inputAudio.durationMillis, trimmed.durationMillis)
    }

    @Test
    fun trimRefusesOutOfBoundsAndEmptySelections() {
        val input = fixture("voice-alpha.m4a")
        val inputAudio = audioOf(input)
        val inputHash = sha256(input)
        val before = workspace.listFiles()!!.map { it.name }.toSet()

        val windows = listOf(
            -1L to 500L, // a negative start
            500L to 500L, // an empty window
            500L to 400L, // an inverted window
            0L to inputAudio.durationMillis + 1L, // past the end
            300L to 305L, // a window with no whole sample inside it
        )
        for ((start, end) in windows) {
            assertThrows(
                "trim($start, $end) must be refused",
                IllegalArgumentException::class.java,
            ) { editor.trim(recording(input, inputAudio), start, end) }
        }

        assertEquals("no output may be left behind", before, workspace.listFiles()!!.map { it.name }.toSet())
        assertTrue("the input is untouched", sha256(input) == inputHash)
    }

    // ---------------------------------------------------------------------------------------------
    // Join
    // ---------------------------------------------------------------------------------------------

    @Test
    fun joinPreservesOrderAndAddsDurations() {
        val first = fixture("voice-alpha.m4a")
        val second = fixture("voice-beta.m4a")
        val firstAudio = audioOf(first)
        val secondAudio = audioOf(second)

        val joined = editor.join(recording(first, firstAudio), recording(second, secondAudio))

        val outAudio = audioOf(joined.file)
        assertEquals(
            "the joined file holds both recordings' samples",
            firstAudio.samples.size + secondAudio.samples.size,
            outAudio.samples.size,
        )
        for (i in firstAudio.samples.indices) {
            assertTrue("first recording comes first (sample $i)", firstAudio.samples[i].contentEquals(outAudio.samples[i]))
        }
        for (i in secondAudio.samples.indices) {
            val at = firstAudio.samples.size + i
            assertTrue("second recording follows (sample $i)", secondAudio.samples[i].contentEquals(outAudio.samples[at]))
        }
        assertEquals("duration matches the written container", outAudio.durationMillis, joined.durationMillis)
        assertTrue(
            "the joined duration is the sum of the parts",
            abs(joined.durationMillis - (firstAudio.durationMillis + secondAudio.durationMillis)) <= 1L,
        )
        assertTrue(joined.durationMillis >= firstAudio.durationMillis)
        assertTrue(joined.durationMillis >= secondAudio.durationMillis)

        // Order matters: the reverse join is the same length but a different file.
        val reversed = editor.join(recording(second, secondAudio), recording(first, firstAudio))
        val reversedAudio = audioOf(reversed.file)
        assertEquals(outAudio.samples.size, reversedAudio.samples.size)
        assertFalse(
            "A+B and B+A must differ at the first sample",
            outAudio.samples.first().contentEquals(reversedAudio.samples.first()),
        )
        assertTrue(
            "A+B and B+A must differ at the last sample",
            !outAudio.samples.last().contentEquals(reversedAudio.samples.last()),
        )
    }

    @Test
    fun joinRefusesIncompatibleOrNonAudioInputs() {
        val first = fixture("voice-alpha.m4a")
        val firstAudio = audioOf(first)
        val wide = fixture("voice-wide.m4a")
        val videoOnly = fixture("silent-video.mp4")
        val broken = File(workspace, "broken.m4a").apply { writeBytes(ByteArray(64) { (it * 7 + 1).toByte() }) }
        val before = workspace.listFiles()!!.map { it.name }.toSet()

        val incompatible = listOf(
            "a different sample rate and channel layout" to recording(wide, 0L),
            "an input with no audio track" to recording(videoOnly, 0L),
            "a file that is not a readable MP4" to recording(broken, 0L),
        )
        for ((why, other) in incompatible) {
            assertThrows(
                "join must refuse $why",
                IllegalArgumentException::class.java,
            ) { editor.join(recording(first, firstAudio), other) }
        }

        assertEquals("no output may be left behind", before, workspace.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun aRefusedWriteLeavesNoPartialOutputAndKeepsTheInput() {
        val locked = File(workspace, "locked").apply { mkdirs() }
        val input = File(locked, "voice-alpha.m4a").apply { writeBytes(fixture("voice-alpha.m4a").readBytes()) }
        val inputAudio = audioOf(input)
        val inputHash = sha256(input)

        locked.setWritable(false, false)
        // A privileged runner can write anywhere; then the failure cannot be induced.
        assumeTrue("the environment must enforce a read-only directory", !locked.canWrite())
        try {
            assertThrows(IllegalStateException::class.java) {
                editor.trim(recording(input, inputAudio), 250L, 1050L)
            }
        } finally {
            locked.setWritable(true, true)
        }

        assertEquals("only the input may remain", setOf(input.name), locked.listFiles()!!.map { it.name }.toSet())
        assertTrue("the input is untouched", sha256(input) == inputHash)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun fixture(name: String): File {
        val bytes = javaClass.getResourceAsStream("/voice-editor/$name")?.use { it.readBytes() }
            ?: error("missing test fixture /voice-editor/$name")
        return File(workspace, name).apply { writeBytes(bytes) }
    }

    /** A recording over [file] whose stored duration is [durationMillis]. */
    private fun recording(file: File, durationMillis: Long): VoiceRecording =
        VoiceRecording(file, file.name, VOICE_RECORDING_MIME_TYPE, durationMillis)

    /** A recording over [file] whose stored duration is the file's real audio duration. */
    private fun recording(file: File, parsed: ParsedAudio): VoiceRecording = recording(file, parsed.durationMillis)

    /** An audio track read straight from the container: its samples and its real stored duration. */
    private class ParsedAudio(val samples: List<ByteArray>, val timescale: Long, val durationUnits: Long) {
        val durationMillis: Long get() = durationUnits * 1000 / timescale
    }

    private fun audioOf(file: File): ParsedAudio {
        val movie = MovieCreator.build(file.absolutePath)
        val track = movie.tracks.firstOrNull { it.handler == "soun" } ?: error("no audio track in ${file.name}")
        val units = track.sampleDurations.sum()
        val samples = track.samples.map { sample ->
            val buffer: ByteBuffer = sample.asByteBuffer()
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        }
        return ParsedAudio(samples, track.trackMetaData.timescale, units)
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        /** Every full AAC-LC frame in these fixtures is 1024 PCM samples. */
        const val FULL_AAC_FRAME_SAMPLES = 1024
    }
}

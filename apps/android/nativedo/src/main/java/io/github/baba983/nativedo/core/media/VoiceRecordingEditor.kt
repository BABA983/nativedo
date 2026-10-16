// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.core.media

import com.googlecode.mp4parser.FileDataSourceImpl
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Lossless AAC sample-table edits. Returns new files; callers own async work and input deletion. */
class VoiceRecordingEditor {
    fun durationMillis(file: File): Long = withTrack(file) {
        it.sampleDurations.sum() * 1000 / it.trackMetaData.timescale
    }

    /** Keep only whole frames inside the selected window; never fabricate a successful crop. */
    fun trim(recording: VoiceRecording, startMillis: Long, endMillis: Long): VoiceRecording =
        withTrack(recording.file) { source ->
            require(startMillis >= 0 && endMillis > startMillis) { "empty or invalid selection" }
            val durations = source.sampleDurations
            val timescale = source.trackMetaData.timescale
            val totalUnits = durations.sum()
            val totalMillis = totalUnits * 1000 / timescale
            require(endMillis <= totalMillis) { "selection exceeds recording duration" }
            val startUnits = startMillis * timescale / 1000
            val endUnits = if (endMillis == totalMillis) totalUnits else endMillis * timescale / 1000
            var from = 0
            var to = 0
            var time = 0L
            for (index in durations.indices) {
                if (time < startUnits) from = index + 1
                time += durations[index]
                if (time <= endUnits) to = index + 1
            }
            require(to > from) { "selection contains no whole AAC frame" }
            val waveform = recording.waveform
            val croppedWaveform = if (waveform.isEmpty()) waveform else {
                val left = (from.toLong() * waveform.size / durations.size).toInt()
                val right = ((to.toLong() * waveform.size + durations.size - 1) / durations.size).toInt()
                waveform.copyOfRange(left, right)
            }
            val output = write(recording, "trim", CroppedTrack(source, from.toLong(), to.toLong()))
            recording.copy(file = output, name = output.name,
                durationMillis = (from until to).sumOf { durations[it] } * 1000 / timescale,
                waveform = croppedWaveform)
        }

    fun join(first: VoiceRecording, second: VoiceRecording): VoiceRecording =
        withTrack(first.file) { a ->
            withTrack(second.file) { b ->
                require(a.trackMetaData.timescale == b.trackMetaData.timescale) { "incompatible sample rates" }
                val appended = try { AppendTrack(a, b) } catch (error: IOException) {
                    throw IllegalArgumentException("incompatible audio formats", error)
                }
                val output = write(first, "join", appended)
                first.copy(file = output, name = output.name,
                    durationMillis = (a.sampleDurations.sum() + b.sampleDurations.sum()) * 1000 / a.trackMetaData.timescale,
                    waveform = first.waveform + second.waveform)
            }
        }

    private fun <T> withTrack(file: File, action: (Track) -> T): T =
        FileDataSourceImpl(file).use { data ->
            val movie = try { MovieCreator.build(data) } catch (error: Exception) {
                throw IllegalArgumentException("unreadable audio container", error)
            }
            val track = movie.tracks.firstOrNull { it.handler == "soun" }
                ?: throw IllegalArgumentException("no audio track")
            require(track.samples.isNotEmpty()) { "empty audio track" }
            action(track)
        }

    private fun write(source: VoiceRecording, marker: String, track: Track): File {
        var output: File? = null
        try {
            val target = File.createTempFile("${source.file.nameWithoutExtension}-$marker-", ".m4a", source.file.parentFile)
            output = target
            val movie = Movie().apply { addTrack(track) }
            val container = DefaultMp4Builder().build(movie)
            FileOutputStream(target).use { container.writeContainer(it.channel) }
            return target
        } catch (error: Exception) {
            output?.delete()
            throw IllegalStateException("could not write edited recording", error)
        }
    }
}

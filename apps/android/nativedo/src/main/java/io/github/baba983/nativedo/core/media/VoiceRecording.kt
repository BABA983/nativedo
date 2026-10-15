package io.github.baba983.nativedo.core.media

import android.content.Context
import java.io.File

/**
 * The container this client records to: an MPEG-4/AAC `.m4a`, which is what a Discourse audio
 * upload is when the site's own composer records in the browser (the recorder writes `audio/webm`
 * there, but `.m4a` is the one extension every Android device encodes and the site reads as audio).
 * The mime follows the container, never the other way around.
 */
const val VOICE_RECORDING_MIME_TYPE = "audio/mp4"

/** The extension of [VOICE_RECORDING_MIME_TYPE]; the site's own kind test is by filename. */
const val VOICE_RECORDING_EXTENSION = "m4a"

/**
 * A finished recording: our own private cache file, the mime its container is, and how long it ran.
 * [name] is what the upload is sent under and what the site's markdown labels the upload with - the
 * site answers `original_filename` and cooks `![name|audio](upload://...)` from it.
 */
data class VoiceRecording(
    val file: File,
    val name: String,
    val mimeType: String,
    val durationMillis: Long,
)

/**
 * Where a recording lives while it exists: a private subdirectory of the app cache, named so two
 * recordings in the same millisecond cannot collide. Nothing here is user-visible storage, and a
 * cancelled or too-short recording is deleted rather than left behind.
 */
object VoiceRecordingFiles {

    private const val DIRECTORY = "voice-recordings"

    fun directory(context: Context): File = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    /** A fresh, unused cache file for a recording started now. */
    fun newFile(context: Context, nowMillis: Long = System.currentTimeMillis()): File =
        File(directory(context), "voice-$nowMillis.$VOICE_RECORDING_EXTENSION")

    /** The upload name for a recording whose file is [file]. */
    fun nameFor(file: File): String = file.name
}

/**
 * The audio extensions the site reads as audio (its own `Upload` model's kind test, by filename
 * rather than by mime, which is why `.m4a` is audio and `.mp4` is video). Kept here so the reply
 * markup and any received-audio classification agree on one list.
 */
private val AUDIO_EXTENSIONS = setOf(
    "m4a", "mp3", "ogg", "oga", "opus", "wav", "aac", "flac", "m4b", "amr", "weba", "aiff", "aif",
)

/** Whether a mime is an audio mime (its type is `audio`). */
fun isAudioMimeType(mimeType: String?): Boolean =
    mimeType?.trim()?.startsWith("audio/", ignoreCase = true) == true

/** Whether a file name's extension is one the site reads as audio. */
fun isAudioFileName(name: String?): Boolean {
    val extension = name?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
    return extension.isNotEmpty() && extension in AUDIO_EXTENSIONS
}

/**
 * Whether an upload is audio: the mime says so, or - when the picker/recorder did not carry one -
 * the file name does. This is the same precedence the reply markup and any received-audio render
 * must use, so a `.m4a` recorded here is never drawn as a generic attachment.
 */
fun isAudioUpload(mimeType: String?, name: String?): Boolean =
    isAudioMimeType(mimeType) || isAudioFileName(name)

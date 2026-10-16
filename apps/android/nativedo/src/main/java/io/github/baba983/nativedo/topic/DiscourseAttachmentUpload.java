package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.media.PickedMedia;
import io.github.baba983.nativedo.core.media.PickedMediaResolver;

/**
 * The one real URI-to-markup upload path, shared by every composer that can attach a file.
 *
 * <p>A reply, a new topic and a private message all attach the same way: a local file is read,
 * uploaded to the site with the session's own credentials, and the answer becomes the Markdown the
 * site's composer would have written. Only the field it lands in differs. This class is that path
 * with no screen attached, so a host can hand it any URI - the system picker's result, or a
 * FileProvider URI a recorder wrote itself - without going through an Activity result.</p>
 *
 * <p>The upload runs on {@link DiscourseLoader#uploadReplyMedia}, which resolves the file, asks the
 * session-aware client to upload it, and builds the markup from the site's answer; a site that
 * refuses the upload reports the failure here rather than pretending it landed.</p>
 */
public final class DiscourseAttachmentUpload {

    /** What the host screen does as the upload starts, lands, or fails. */
    public interface Sink {
        /** The upload is on its way; the entry that opened the picker should show it. */
        void onUploadStarted();

        /** The site accepted the file; this is the Markdown to put in the field. */
        void onUploaded(String markup);

        /** The file could not be read or the site refused it; the field is untouched. */
        void onUploadFailed(Throwable error);
    }

    private DiscourseAttachmentUpload() {
    }

    /** Preserve selection order; a rejected file stops the batch without losing earlier inserts. */
    public static void upload(@NonNull Context context, @NonNull AppContainer container,
            java.util.List<Uri> uris, @NonNull Sink sink) {
        final int generation = container.getSessionCoordinator().currentRequestContext().getGeneration();
        final var owner = container.getSessionStore().snapshot();
        java.util.Iterator<Uri> remaining = new java.util.ArrayList<>(uris).iterator();
        Sink sequential = new Sink() {
            @Override
            public void onUploadStarted() {
                sink.onUploadStarted();
            }

            @Override
            public void onUploaded(String markup) {
                var current = container.getSessionStore().snapshot();
                if (generation != container.getSessionCoordinator().currentRequestContext().getGeneration()
                        || !java.util.Objects.equals(owner == null ? null : owner.getUsername(),
                                current == null ? null : current.getUsername())) {
                    sink.onUploadFailed(new java.io.IOException("登录状态已改变，请重新选择附件"));
                    return;
                }
                sink.onUploaded(markup);
                if (remaining.hasNext()) upload(context, container, remaining.next(), this);
            }

            @Override
            public void onUploadFailed(Throwable error) {
                sink.onUploadFailed(error);
            }
        };
        if (remaining.hasNext()) upload(context, container, remaining.next(), sequential);
    }

    /**
     * Uploads [uri] for [container]'s session and reports the result to [sink]. A URI whose bytes
     * cannot be read fails at once, before any request, so a missing file never reaches the site.
     */
    public static void upload(@NonNull Context context, @NonNull AppContainer container,
            Uri uri, @NonNull Sink sink) {
        if (uri == null) {
            sink.onUploadFailed(new java.io.IOException("无法读取所选文件"));
            return;
        }
        PickedMedia picked = PickedMediaResolver.INSTANCE.resolve(context, uri);
        if (picked == null) {
            sink.onUploadFailed(new java.io.IOException("无法读取所选文件"));
            return;
        }
        sink.onUploadStarted();
        DiscourseLoader.INSTANCE.uploadReplyMedia(container, picked, markup -> {
            sink.onUploaded(markup);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            sink.onUploadFailed(error);
            return kotlin.Unit.INSTANCE;
        });
    }
}

package io.github.baba983.nativedo.topic;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;

import io.github.baba983.nativedo.hooks.NativeDoHooks;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a post's image full screen in upstream's {@link PhotoViewer}.
 *
 * <p>A tap on a rich media block goes to the cell's delegate as {@code openArticlePhoto}, not as
 * {@code didPressImage}: upstream's own implementation of it lives in its chat screen and is
 * private, because it wraps the page's media blocks in an adapter. This is that adapter over our
 * rich message, so the viewer is handed the photo the bridge cached rather than Telegram's chat
 * media list, which our messages do not have.</p>
 */
public final class ArticlePhotoViewer {

    /**
     * The clip a block names, or null when it is not a clip this client carries. The caller that
     * has to fetch it (a tap on it) is the one that needs this.
     */
    public static PostVideo videoOf(TL_iv.PageBlock block) {
        if (!(block instanceof TL_iv.pageBlockVideo)) {
            return null;
        }
        return DiscourseImageBridge.INSTANCE.videoOf(((TL_iv.pageBlockVideo) block).video_id);
    }

    public static boolean open(BaseFragment fragment, MessageObject message, TL_iv.PageBlock block) {
        return open(fragment, message, block, null);
    }

    public static boolean open(BaseFragment fragment, MessageObject message, TL_iv.PageBlock block,
                               RecyclerListView list) {
        if (fragment == null || fragment.getParentActivity() == null || message == null
                || message.messageOwner == null || message.richLayout == null || block == null) {
            return false;
        }
        // A video block is drawn from its cover, and the clip's own bytes are fetched when play is
        // pressed: a clip this client does not hold - one it would not fetch, one the site would
        // not hand over - is streamed by whatever the device plays videos with, rather than handed
        // to a player that would sit on a file that is not there.
        if (block instanceof TL_iv.pageBlockVideo
                && !DiscourseImageBridge.INSTANCE.videoFileExists(((TL_iv.pageBlockVideo) block).video_id)) {
            return stream(fragment, block);
        }
        ArrayList<TL_iv.PageBlock> media = new ArrayList<>();
        message.richLayout.collectMediaBlocks(media);
        int index = media.indexOf(block);
        if (index < 0) {
            return false;
        }
        groupMedia(message, media);
        PhotoViewer viewer = PhotoViewer.getInstance();
        viewer.setParentActivity(fragment.getParentActivity(), fragment.getResourceProvider());
        return viewer.openPhoto(index, new BlocksAdapter(message, media), new SourceProvider(message, media, list));
    }

    /**
     * Gives the viewer's strip what upstream's own chat media has and this client's page blocks do
     * not: a group id per cell and the cell's own thumb.
     *
     * <p>Upstream groups the strip by {@code PageBlock.groupId} and draws each cell from
     * {@code PageBlock.thumb}/{@code thumbObject} - the same fields its article viewer fills in
     * {@code ArticleViewer.addAllMediaFromBlock}. Our blocks are built by the bridge and carry
     * neither, so the strip never appeared. Every picture this message holds goes into one group,
     * named by the message, so the strip offers exactly this message's media and never stitches a
     * neighbouring post's pictures onto it. A message with one picture is left ungrouped - upstream
     * groups only galleries, and an id of its own would make it draw an empty strip. The thumb
     * prefers the site's own smaller preview (the same one the block draws), falling back to the
     * closest regular size - the original - when there is none.</p>
     */
    private static void groupMedia(MessageObject message, List<TL_iv.PageBlock> media) {
        if (media.size() < 2) {
            return;
        }
        TL_iv.RichMessage page = message.messageOwner.rich_message;
        int groupId = message.getId();
        if (groupId == 0) {
            groupId = 1;
        }
        for (TL_iv.PageBlock block : media) {
            block.groupId = groupId;
            if (block instanceof TL_iv.pageBlockPhoto) {
                TLRPC.Photo photo = photoOf(page, ((TL_iv.pageBlockPhoto) block).photo_id);
                if (photo == null) {
                    continue;
                }
                TLRPC.PhotoSize thumb = NativeDoHooks.photoThumbSize(photo.sizes);
                if (thumb == null) {
                    thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 56, true);
                }
                block.thumb = thumb;
                block.thumbObject = photo;
            } else if (block instanceof TL_iv.pageBlockVideo) {
                TLRPC.Document document = documentOf(page, ((TL_iv.pageBlockVideo) block).video_id);
                if (document == null) {
                    continue;
                }
                block.thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 56, true);
                block.thumbObject = document;
            }
        }
    }

    private static TLRPC.Photo photoOf(TL_iv.RichMessage page, long photoId) {
        if (page == null || page.photos == null) {
            return null;
        }
        for (TLRPC.Photo photo : page.photos) {
            if (photo != null && photo.id == photoId) {
                return photo;
            }
        }
        return null;
    }

    private static TLRPC.Document documentOf(TL_iv.RichMessage page, long documentId) {
        if (page == null || page.documents == null) {
            return null;
        }
        for (TLRPC.Document document : page.documents) {
            if (document != null && document.id == documentId) {
                return document;
            }
        }
        return null;
    }

    /** ChatActivity's rich-media source lookup, using this topic's list and actual content insets. */
    private static class SourceProvider extends PhotoViewer.EmptyPhotoViewerProvider {
        private final MessageObject message;
        private final List<TL_iv.PageBlock> blocks;
        private final RecyclerListView list;

        SourceProvider(MessageObject message, List<TL_iv.PageBlock> blocks, RecyclerListView list) {
            this.message = message;
            this.blocks = blocks;
            this.list = list;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject,
                TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (list == null || !list.isAttachedToWindow() || !list.isShown()
                    || index < 0 || index >= blocks.size()) return null;
            int[] listCoords = new int[2];
            list.getLocationInWindow(listCoords);
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (!(child instanceof ChatMessageCell) || !child.isShown()) continue;
                ChatMessageCell cell = (ChatMessageCell) child;
                MessageObject current = cell.getMessageObject();
                if (current != message || current.richLayout == null) continue;
                int[] offset = new int[2];
                ImageReceiver receiver = current.richLayout.findMediaImageReceiver(blocks.get(index), offset);
                if (receiver == null) continue;
                int[] coords = new int[2];
                cell.getLocationInWindow(coords);
                coords[0] += cell.getTextX() + offset[0];
                coords[1] += cell.getTextY() + offset[1];
                // A long text cell can stay attached while its photo is outside the viewport.
                RectF bounds = new RectF(receiver.getDrawRegion());
                bounds.offset(coords[0], coords[1]);
                if (!bounds.intersect(listCoords[0] + list.getPaddingLeft(),
                        listCoords[1] + list.getPaddingTop(),
                        listCoords[0] + list.getWidth() - list.getPaddingRight(),
                        listCoords[1] + list.getHeight() - list.getPaddingBottom())) continue;
                PhotoViewer.PlaceProviderObject source = new PhotoViewer.PlaceProviderObject();
                source.viewX = coords[0];
                source.viewY = coords[1];
                source.parentView = list;
                source.imageReceiver = receiver;
                source.thumb = receiver.getBitmapSafe();
                source.radius = receiver.getRoundRadius(true);
                source.clipTopAddition = list.getPaddingTop();
                source.clipBottomAddition = list.getPaddingBottom();
                return source;
            }
            return null;
        }
    }

    /**
     * Hands a clip to the player the device has, with the site's own address for it: the file
     * streams instead of waiting for a download, and nothing is copied into an app cache that a
     * long video would fill.
     */
    public static boolean stream(BaseFragment fragment, TL_iv.PageBlock block) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return false;
        }
        PostVideo video = videoOf(block);
        if (video == null) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(video.getUrl()), "video/*");
            fragment.getParentActivity().startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            // A device with no player for it is not a crash; the block stays as it was drawn.
            FileLog.e(e);
            return false;
        }
    }

    private static class BlocksAdapter implements PhotoViewer.PageBlocksAdapter {
        private final MessageObject messageObject;
        private final List<TL_iv.PageBlock> blocks;

        BlocksAdapter(MessageObject messageObject, List<TL_iv.PageBlock> blocks) {
            this.messageObject = messageObject;
            this.blocks = blocks;
        }

        private TL_iv.RichMessage page() {
            return messageObject.messageOwner.rich_message;
        }

        private TL_iv.PageBlock block(int index) {
            return index >= 0 && index < blocks.size() ? blocks.get(index) : null;
        }

        @Override
        public int getItemsCount() {
            return blocks.size();
        }

        @Override
        public TL_iv.PageBlock get(int index) {
            return block(index);
        }

        @Override
        public List<TL_iv.PageBlock> getAll() {
            return blocks;
        }

        @Override
        public boolean isVideo(int index) {
            return ArticleViewer.WebPageUtils.isVideo(page(), block(index));
        }

        @Override
        public TLObject getMedia(int index) {
            return ArticleViewer.WebPageUtils.getMedia(page(), block(index));
        }

        @Override
        public File getFile(int index) {
            return ArticleViewer.WebPageUtils.getMediaFile(page(), block(index));
        }

        @Override
        public String getFileName(int index) {
            TLObject media = getMedia(index);
            if (media instanceof TLRPC.Photo) {
                media = FileLoader.getClosestPhotoSizeWithSize(
                        ((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
            }
            return FileLoader.getAttachFileName(media);
        }

        @Override
        public CharSequence getCaption(int index) {
            return null;
        }

        @Override
        public TLRPC.PhotoSize getFileLocation(TLObject media, int[] size) {
            if (!(media instanceof TLRPC.Photo)) {
                return null;
            }
            TLRPC.PhotoSize full = FileLoader.getClosestPhotoSizeWithSize(
                    ((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
            if (full == null) {
                return null;
            }
            size[0] = full.size == 0 ? -1 : full.size;
            return full;
        }

        @Override
        public void updateSlideshowCell(TL_iv.PageBlock currentPageBlock) {
            if (messageObject.richLayout != null) {
                messageObject.richLayout.setSlideshowPage(currentPageBlock);
            }
        }

        @Override
        public Object getParentObject() {
            return messageObject;
        }

        @Override
        public boolean isHardwarePlayer(int index) {
            return false;
        }
    }

    private ArticlePhotoViewer() {
    }
}

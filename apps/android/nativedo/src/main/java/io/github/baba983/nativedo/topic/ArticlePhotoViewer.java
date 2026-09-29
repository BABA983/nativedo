package io.github.baba983.nativedo.topic;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ArticleViewer;
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

    public static boolean open(BaseFragment fragment, MessageObject message, TL_iv.PageBlock block) {
        if (fragment == null || fragment.getParentActivity() == null || message == null
                || message.messageOwner == null || message.richLayout == null || block == null) {
            return false;
        }
        ArrayList<TL_iv.PageBlock> media = new ArrayList<>();
        message.richLayout.collectMediaBlocks(media);
        int index = media.indexOf(block);
        if (index < 0) {
            return false;
        }
        PhotoViewer viewer = PhotoViewer.getInstance();
        viewer.setParentActivity(fragment.getParentActivity(), fragment.getResourceProvider());
        return viewer.openPhoto(index, new BlocksAdapter(message, media), new PhotoViewer.EmptyPhotoViewerProvider());
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

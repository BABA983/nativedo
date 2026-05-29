package org.telegram.ui;

import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.List;

public class PhotoViewer {
    private static final PhotoViewer INSTANCE = new PhotoViewer();

    public static PhotoViewer getInstance() { return INSTANCE; }
    public static boolean hasInstance() { return false; }
    public boolean isVisibleOrAnimating() { return false; }
    public void setParentActivity(BaseFragment fragment) {}
    public boolean openPhoto(int index, PageBlocksAdapter adapter, EmptyPhotoViewerProvider provider) { return false; }
    public VideoPlayer getVideoPlayer() { return null; }
    public TextureView getVideoTextureView() { return null; }
    public SurfaceView getVideoSurfaceView() { return null; }

    public interface PageBlocksAdapter {
        int getItemsCount();
        TLRPC.PageBlock get(int index);
        List<TLRPC.PageBlock> getAll();
        boolean isVideo(int index);
        boolean isHardwarePlayer(int index);
        TLObject getMedia(int index);
        File getFile(int index);
        String getFileName(int index);
        CharSequence getCaption(int index);
        TLRPC.PhotoSize getFileLocation(TLObject media, int[] size);
        void updateSlideshowCell(TLRPC.PageBlock currentPageBlock);
        Object getParentObject();
    }

    public static class EmptyPhotoViewerProvider {
        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            return null;
        }
        public void onClose() {}
        public void onReleasePlayerBeforeClose(int photoIndex) {}
    }

    public static class PlaceProviderObject {
        public int viewX;
        public int viewY;
        public View parentView;
        public ImageReceiver imageReceiver;
        public Object thumb;
        public int[] radius;
        public int clipTopAddition;
    }
}

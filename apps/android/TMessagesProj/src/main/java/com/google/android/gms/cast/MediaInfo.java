// NativeDo stub.
package com.google.android.gms.cast;

public final class MediaInfo {
    public static final int STREAM_TYPE_BUFFERED = 1;
    public static final int STREAM_TYPE_LIVE = 2;

    public static final class Builder {
        public Builder(String contentId) {}
        public Builder setStreamType(int type) { return this; }
        public Builder setContentType(String type) { return this; }
        public Builder setMetadata(MediaMetadata metadata) { return this; }
        public MediaInfo build() { return new MediaInfo(); }
    }
}

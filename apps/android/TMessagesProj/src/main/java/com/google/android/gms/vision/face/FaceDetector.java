// NativeDo stub.
package com.google.android.gms.vision.face;

import android.util.SparseArray;
import com.google.android.gms.vision.Frame;

public final class FaceDetector {
    public static final class Builder {
        public Builder(android.content.Context ctx) {}
        public Builder setTrackingEnabled(boolean tracking) { return this; }
        public Builder setLandmarkType(int type) { return this; }
        public Builder setMode(int mode) { return this; }
        public FaceDetector build() { return new FaceDetector(); }
    }

    public SparseArray<Face> detect(Frame frame) {
        return new SparseArray<>();
    }

    public boolean isOperational() { return false; }

    public void release() {}
}

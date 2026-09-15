package com.google.zxing.common.detector;

/** Minimal ZXing MathUtils surface used by pin SearchStateDrawable. */
public final class MathUtils {
    private MathUtils() {}

    public static float distance(float aX, float aY, float bX, float bY) {
        float xDiff = aX - bX;
        float yDiff = aY - bY;
        return (float) Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }
}

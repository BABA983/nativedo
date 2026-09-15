package org.telegram.ui.Components;

public interface MovableCell {
    void setMoving(boolean moving);
    boolean isMoving();
    void setClipProgress(float value);
    float getClipProgress();
    void setTopClip(int value);
    void setBottomClip(int value);
    int getMeasuredHeight();
}

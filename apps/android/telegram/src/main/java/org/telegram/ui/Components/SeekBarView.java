package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

public class SeekBarView extends View {
    public interface SeekBarViewDelegate {
        void onSeekBarDrag(boolean stop, float progress);
        default void onSeekBarPressed(boolean pressed) {}
        default CharSequence getContentDescription() { return null; }
        default int getStepsCount() { return 0; }
    }
    private float progress;

    public SeekBarView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
    }

    public void setReportChanges(boolean value) {}
    public void setSeparatorsCount(int count) {}
    public void setDelegate(SeekBarViewDelegate delegate) {}
    public void setProgress(float progress) { this.progress = progress; }
    public float getProgress() { return progress; }
}

package io.github.baba983.nativedo.ui.topic.body;

import android.view.View;

public interface EmbeddedPostBodyBlock extends PostBodyBlock {
    View embeddedView();

    default void layoutEmbeddedView(int left, int top, int width, int height) {
        embeddedView().layout(left, top, left + width, top + height);
    }
}

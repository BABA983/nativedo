package io.github.baba983.nativedo.ui.components;

import android.view.View;

import java.util.ArrayList;

public interface UniversalListCallbacks {
    interface Fill {
        void run(ArrayList<UItem> items, UniversalAdapter adapter);
    }

    interface Click {
        void run(UItem item, View view, int position, float x, float y);
    }

    interface LongClick {
        boolean run(UItem item, View view, int position, float x, float y);
    }
}

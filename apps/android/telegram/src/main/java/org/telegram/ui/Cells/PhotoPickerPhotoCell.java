/* NativeDo stub of org.telegram.ui.Cells.PhotoPickerPhotoCell. */
package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

public class PhotoPickerPhotoCell extends FrameLayout {
    public Object photoImage;
    public Object checkBox;

    public PhotoPickerPhotoCell(Context context, boolean singlePhoto) {
        super(context);
    }

    public PhotoPickerPhotoCell(Context context) {
        super(context);
    }

    public void setNum(int num) {}
    public void setOnCheckClick(Runnable onCheckClick) {}
    public boolean isChecked() { return false; }
    public void setChecked(int num, boolean checked, boolean animated) {}
}

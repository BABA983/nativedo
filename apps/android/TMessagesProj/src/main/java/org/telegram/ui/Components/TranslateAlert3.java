package org.telegram.ui.Components;

import android.app.Dialog;
import android.content.Context;

import org.telegram.messenger.Utilities;

/** Host stub: the pin's translation alert needs ML Kit's LanguageDetector; this client never translates. */
public class TranslateAlert3 extends Dialog {
    public TranslateAlert3(Context context, Object resourcesProvider) { super(context); }

    public TranslateAlert3 setText(CharSequence text) { return this; }

    public TranslateAlert3 setOnUse(Utilities.Callback<CharSequence> onUse) { return this; }
}

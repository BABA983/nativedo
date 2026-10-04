package io.github.baba983.nativedo.topic;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import io.github.baba983.nativedo.core.model.CategorySummary;

/**
 * The site's own name and color for one category, as the text that carries both.
 *
 * <p>A Discourse category is read by its color before its name: the site badges it with a colored
 * mark next to the name. This is that mark - the site's color as the dot in front of the name - so a
 * category looks the same wherever this client offers one: on the topic list's filter strip and on
 * the row a new topic's category is chosen on.</p>
 */
public final class CategoryLabels {

    /** The category's name, with its site color as the dot in front of it. */
    public static CharSequence of(CategorySummary category) {
        SpannableString label = new SpannableString("● " + category.getName());
        Integer color = color(category);
        if (color != null) {
            label.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return label;
    }

    /** The category's color as the site sends it: hex, with or without the leading '#'. */
    public static Integer color(CategorySummary category) {
        String hex = category.getColorHex();
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        try {
            return Color.parseColor(hex.startsWith("#") ? hex : "#" + hex);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private CategoryLabels() {
    }
}

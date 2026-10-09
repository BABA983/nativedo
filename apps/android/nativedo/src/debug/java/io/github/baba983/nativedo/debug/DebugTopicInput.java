package io.github.baba983.nativedo.debug;

import io.github.baba983.nativedo.core.web.DiscourseLinks;
import io.github.baba983.nativedo.core.web.DiscourseTopicLink;

/**
 * The text a debug jump accepts, read into a topic - or {@code null} when it names none.
 *
 * <p>Two shapes are accepted:</p>
 * <ul>
 *   <li>a bare numeric topic id ({@code 2193859});</li>
 *   <li>a topic link, read by the app's own parser exactly as a tapped link is: the site's own
 *       {@code https://linux.do/t/<slug>/<id>[/<floor>]} URL, or the app aliases
 *       {@code nativedo://topic/<id>[/<floor>]} and {@code linuxdo://topic/<id>[/<floor>]}
 *       (and the app-owned {@code /t/...} shape under those schemes).</li>
 * </ul>
 *
 * <p>The validator is the app's real link rule, so a bare id is the only extra shape: everything a
 * link may not be (a foreign host, userinfo, another scheme such as {@code tg}, a zero, negative,
 * non-numeric or out-of-range id or floor) is refused here too, before anything is opened.</p>
 */
public final class DebugTopicInput {

    private DebugTopicInput() {
    }

    /** The topic [raw] names, or {@code null} when it names none. */
    public static DiscourseTopicLink parse(String raw, String baseUrl) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (isAsciiDigits(value)) {
            try {
                long topicId = Long.parseLong(value);
                return topicId > 0 ? new DiscourseTopicLink(topicId, 0) : null;
            } catch (NumberFormatException overflow) {
                return null;
            }
        }
        return DiscourseLinks.topicLink(value, baseUrl);
    }

    /** ASCII digits only, so `+1`, `1.0`, `-1` and non-Latin numerals are not numbers here. */
    private static boolean isAsciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !value.isEmpty();
    }
}

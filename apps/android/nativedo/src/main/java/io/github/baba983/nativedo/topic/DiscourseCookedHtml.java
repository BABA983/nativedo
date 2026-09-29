package io.github.baba983.nativedo.topic;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.iv.BlockRow;
import org.telegram.ui.iv.RichHtml;

import java.util.ArrayList;

/**
 * Turns a prepared Discourse post body into the rich-message model upstream's cells draw.
 *
 * <p>The preparation itself (unwrapping Discourse's presentational {@code <div>}s, replacing post
 * images with media references) happens in {@link DiscourseImageBridge}, because it also has to
 * fetch those images; this class is only the last step into {@link RichHtml}, upstream's own
 * importer.</p>
 */
public final class DiscourseCookedHtml {

    public static TL_iv.RichMessage richMessage(PreparedPostBody body) {
        TL_iv.RichMessage richMessage = new TL_iv.RichMessage();
        richMessage.blocks = new ArrayList<>();
        for (BlockRow row : RichHtml.parse(body.getHtml())) {
            richMessage.blocks.add(row.block);
        }
        richMessage.photos = new ArrayList<>(body.getPhotos());
        richMessage.documents = new ArrayList<>();
        return richMessage;
    }

    private DiscourseCookedHtml() {
    }
}

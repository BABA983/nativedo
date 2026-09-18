package org.telegram.tgnet;

import android.graphics.Path;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;

import java.util.ArrayList;

/**
 * Minimal TLRPC stub for NativeDo image pipeline.
 * Only types referenced by ImageLocation/ImageLoader/ImageReceiver/FileLoader are declared.
 * Methods and fields are added as needed when compilation requires them.
 */
public class TLRPC {

    public static final int MESSAGE_FLAG_HAS_FROM_ID = 0x00000100;
    public static final int MESSAGE_FLAG_HAS_MEDIA = 0x00000200;

    public static class TL_fileLocationToBeDeprecated extends FileLocation {
    }

    public static class TL_fileLocationUnavailable extends FileLocation {
    }

    public static class FileLocation extends TLObject {
        public int local_id;
        public long volume_id;
        public int dc_id;
        public long secret;
        public byte[] file_reference;
        public byte[] key;
        public byte[] iv;
    }

    public static class SecureFile extends TLObject {
        public long id;
        public long access_hash;
        public int size;
        public int dc_id;
        public int date;
        public byte[] file_hash;
        public byte[] secret;
    }

    public static class TL_secureFile extends SecureFile {
    }

    public static class PhotoSize extends TLObject {
        public FileLocation location;
        public int size;
        public int w;
        public int h;
        public byte[] bytes;
        public String type;
        public android.graphics.Bitmap strippedBitmap;
    }

    public static class TL_photoStrippedSize extends PhotoSize {
        public byte[] stripped;
    }

    public static class TL_photoPathSize extends PhotoSize {
        public static final int constructor = 0xd8214d41;
        public Path svgPath;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            type = stream.readString(exception);
            bytes = stream.readByteArray(exception);
            w = h = 512;
            svgPath = SvgHelper.doPath(SvgHelper.decompress(bytes));
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(type);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_photoCachedSize extends PhotoSize {
    }

    public static class TL_photoSize extends PhotoSize {
    }

    public static class TL_photoSizeProgressive extends PhotoSize {
        public ArrayList<Integer> sizes;
    }

    public static class TL_photoSize_layer127 extends TL_photoSize {
    }

    public static class TL_photoSize_layer97 extends TL_photoSize {
    }

    public static class TL_photoSizeEmpty extends PhotoSize {
    }

    public static class Photo extends TLObject {
        public long id;
        public long access_hash;
        public ArrayList<PhotoSize> sizes;
        public ArrayList<VideoSize> video_sizes;
        public int dc_id;
        public int date;
        public byte[] file_reference;
        public boolean has_stickers;
    }

    public static class TL_photo extends Photo {
    }

    public static class TL_photoEmpty extends Photo {
    }

    public static class VideoSize extends TLObject {
        public FileLocation location;
        public int w;
        public int h;
        public int size;
        public double video_start_ts;
        public String type;
        public int flags;
    }

    public static class TL_videoSizeEmojiMarkup extends VideoSize {
    }

    public static class TL_videoSizeStickerMarkup extends VideoSize {
    }

    public static class TL_videoSize extends VideoSize {
    }

    public static class Document extends TLObject {
        public long id;
        public long access_hash;
        public int date;
        public String mime_type;
        public int size;
        public int dc_id;
        public ArrayList<PhotoSize> thumbs;
        public ArrayList<VideoSize> video_thumbs;
        public FileLocation file_reference_location;
        public byte[] file_reference;
        public byte[] key;
        public byte[] iv;
        public String ext;
        public String localPath;
        public String localThumbPath;
        public String file_name;
        public String file_name_fixed;
        public int version;
        public ArrayList<DocumentAttribute> attributes = new ArrayList<>();
    }

    public static class DocumentAttribute extends TLObject {
        public int w;
        public int h;
        public int duration;
        public String alt;
        public InputStickerSet stickerset;
        public String file_name;
    }

    public static class TL_documentAttributeFilename extends DocumentAttribute {
    }

    public static class TL_documentAttributeAudio extends DocumentAttribute {
    }

    public static class TL_documentAttributeVideo extends DocumentAttribute {
    }

    public static class TL_documentAttributeAnimated extends DocumentAttribute {
    }

    public static class TL_documentAttributeSticker extends DocumentAttribute {
    }

    /** Upstream's declaration (12.10.2); our shell TLRPC carries the fields, not the wire code. */
    public static class TL_documentAttributeCustomEmoji extends DocumentAttribute {
        public boolean free;
        public boolean text_color;
    }

    public static class TL_documentAttributeImageSize extends DocumentAttribute {
    }

    public static class TL_document extends Document {
    }

    public static class TL_documentEncrypted extends Document {
    }

    public static class Message extends TLObject {
        public int flags;
        public long id;
        public long dialog_id;
        public String message;
        public TL_iv.RichMessage rich_message;
        public MessageMedia media;
        public MessageAction action;
        public Peer from_id;
        public Peer peer_id;
        public boolean noforwards;
        public boolean restricted;
        public String attachPath;
        public long grouped_id;
        public Object fwd_from;
        public CharSequence translatedText;
        public String translatedToLanguage;
        public int ttl;
        public long random_id;
        public java.util.ArrayList<Object> restriction_reason = new java.util.ArrayList<>();
        public java.util.ArrayList<MessageEntity> entities = new java.util.ArrayList<>();
        public Object suggested_post;
        public boolean scheduled;
        public int date;
        public boolean post;
        public int realId; //custom
        public TL_iv.RichMessage translatedRichMessage; //custom
    }

    public static class TL_message extends Message {
        public boolean out;
    }

    public static class MessageAction extends TLObject {
        public Photo photo;
    }

    public static class TL_messageActionChatAddUser extends MessageAction {
        public ArrayList<Long> users = new ArrayList<>();
    }

    public static class MessageEntity extends TLObject {
        public int flags;
        public int offset;
        public int length;
        public String url;
        public String language;
        public boolean collapsed;
    }

    /** Data holder for the pin's formatted-date entity; the flag table is local because this shell has no
     * shared one. */
    public static class TL_messageEntityFormattedDate extends MessageEntity {
        public static final int FLAG_RELATIVE = 1;
        public static final int FLAG_SHORT_TIME = 1 << 1;
        public static final int FLAG_LONG_TIME = 1 << 2;
        public static final int FLAG_SHORT_DATE = 1 << 3;
        public static final int FLAG_LONG_DATE = 1 << 4;
        public static final int FLAG_DAY_OF_WEEK = 1 << 5;

        public boolean relative;
        public boolean short_time;
        public boolean long_time;
        public boolean short_date;
        public boolean long_date;
        public boolean day_of_week;
        public int date;

        public void applyFlags() {
            relative = hasFlag(flags, FLAG_RELATIVE);
            short_time = hasFlag(flags, FLAG_SHORT_TIME);
            long_time = hasFlag(flags, FLAG_LONG_TIME);
            short_date = hasFlag(flags, FLAG_SHORT_DATE);
            long_date = hasFlag(flags, FLAG_LONG_DATE);
            day_of_week = hasFlag(flags, FLAG_DAY_OF_WEEK);
        }
    }

    public static class TL_messageEntityUrl extends MessageEntity {
    }

    public static class TL_messageEntityBlockquote extends MessageEntity {
    }

    public static class Peer extends TLObject {
        public long user_id;
        public long chat_id;
        public long channel_id;
    }

    public static class TL_peerUser extends Peer {
    }

    public static class TL_peerChannel extends Peer {
    }

    public static class TL_peerChat extends Peer {
    }

    public static class User extends TLObject {
        public long id;
        public long access_hash;
        public String first_name;
        public String last_name;
        public String username;
        public UserProfilePhoto photo;
        public boolean premium;
        public long fromMessageDialogId;
        public int fromMessageId;
    }

    public static class TL_user extends User {
    }

    public static class Chat extends TLObject {
        public long id;
        public long access_hash;
        public String title;
        public String username;
        public boolean left;
        public boolean kicked;
        public boolean min;
        public ChatPhoto photo;
    }

    public static class UserProfilePhoto extends TLObject {
        public long photo_id;
        public byte[] stripped_thumb;
        public android.graphics.drawable.BitmapDrawable strippedBitmap;
        public FileLocation photo_small;
        public FileLocation photo_big;
        public int dc_id;
        public boolean has_video;
        public boolean personal;
    }

    public static class ChatPhoto extends TLObject {
        public long photo_id;
        public byte[] stripped_thumb;
        public android.graphics.drawable.BitmapDrawable strippedBitmap;
        public FileLocation photo_small;
        public FileLocation photo_big;
        public int dc_id;
        public boolean has_video;
    }

    public static class InputPeer extends TLObject {
        public long user_id;
        public long chat_id;
        public long channel_id;
        public long access_hash;
    }

    public static class TL_inputPeerUser extends InputPeer {
    }

    public static class TL_inputPeerUserFromMessage extends InputPeer {
        public InputPeer peer;
        public int msg_id;
    }

    public static class TL_inputPeerChat extends InputPeer {
    }

    public static class TL_inputPeerChannel extends InputPeer {
    }

    public static class TL_inputPeerEmpty extends InputPeer {
    }

    public static class InputChannel extends TLObject {
        public long channel_id;
        public long access_hash;
    }

    public static class InputStickerSet extends TLObject {
        public long id;
        public long access_hash;
        public String short_name;
    }

    public static class TL_inputStickerSetID extends InputStickerSet {
    }

    public static class TL_inputStickerSetShortName extends InputStickerSet {
    }

    public static class TL_inputStickerSetEmpty extends InputStickerSet {
    }

    public static class TL_inputPhoto extends InputPhoto {
    }

    public static class InputPhoto extends TLObject {
        public long id;
        public long access_hash;
        public byte[] file_reference;
    }

    public static class TL_inputDocument extends TLObject {
        public long id;
        public long access_hash;
        public byte[] file_reference;
    }

    public static class InputDocument extends TLObject {
        public long id;
        public long access_hash;
        public byte[] file_reference;
    }

    public static class BotInlineResult extends TLObject {
        public String id;
        public String type;
        public Photo photo;
        public Document document;
        public String title;
        public String description;
        public String url;
        public WebDocument content;
        public WebDocument thumb;
        public String query_id;
    }

    public static class TL_botInlineResult extends BotInlineResult {
    }

    public static class TL_botInlineMediaResult extends BotInlineResult {
    }

    public static class WallPaper extends TLObject {
        public long id;
        public int flags;
        public boolean creator;
        public boolean isDefault;
        public Document document;
        public WallPaperSettings settings;
        public boolean pattern;
        public boolean dark;
        public long access_hash;
        public String slug;
        public String uploadingImage;
        public Bitmap stripedThumb;
        public Drawable thumbDrawable;
    }

    public static class TL_wallPaper extends WallPaper {
    }

    public static class TL_wallPaperNoFile extends WallPaper {
    }

    public static class WallPaperSettings extends TLObject {
        public int flags;
        public int background_color;
        public int second_background_color;
        public int third_background_color;
        public int fourth_background_color;
        public int intensity;
        public int rotation;
        public boolean blur;
        public boolean motion;
    }

    public static class ThemeSettings extends TLObject {
        public int flags;
        public int accent_color;
        public int outbox_accent_color;
        public ArrayList<Integer> message_colors = new ArrayList<>();
        public WallPaper wallpaper;
        public BaseTheme base_theme;
        public boolean animate_outgoing;
        public boolean message_colors_animated;
    }

    public static class TL_themeSettings extends ThemeSettings {
    }

    public static class BaseTheme extends TLObject {
    }

    public static class TL_baseThemeClassic extends BaseTheme {
    }

    public static class TL_baseThemeDay extends BaseTheme {
    }

    public static class TL_baseThemeNight extends BaseTheme {
    }

    public static class TL_baseThemeTinted extends BaseTheme {
    }

    public static class TL_baseThemeArctic extends BaseTheme {
    }

    public static class InputWallPaper extends TLObject {
    }

    public static class TL_inputWallPaperSlug extends InputWallPaper {
        public String slug;
    }

    public static class InputTheme extends TLObject {
    }

    public static class TL_inputTheme extends InputTheme {
        public long id;
        public long access_hash;
    }

    public static abstract class Theme extends TLObject {
        public static TL_theme TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            throw new UnsupportedOperationException("NativeDo remote themes must be decoded by ThemeBackend");
        }
    }

    public static class TL_theme extends Theme {
        public int flags;
        public boolean creator;
        public boolean isDefault;
        public boolean for_chat;
        public long id;
        public long access_hash;
        public String slug;
        public String title;
        public Document document;
        public ArrayList<ThemeSettings> settings = new ArrayList<>();
        public String emoticon;
        public int installs_count;

        public static TL_theme TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return Theme.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            throw new UnsupportedOperationException("NativeDo remote themes must be encoded by ThemeBackend");
        }
    }

    public static class InputFile extends TLObject {
    }

    public static class InputEncryptedFile extends TLObject {
    }

    public static class InputInvoice extends TLObject {
    }

    public static class WebDocument {
        public String url;
        public int size;
        public String mime_type;
        public long access_hash;
        public ArrayList<DocumentAttribute> attributes = new ArrayList<>();
    }

    public static class TL_webDocument extends WebDocument {
    }

    public static class TL_webDocumentNoProxy extends WebDocument {
    }

    public static class TL_upload_getWebFile extends TLObject {
        public InputWebFileLocation location;
        public int offset;
        public int limit;
    }

    public static class MessageMedia extends TLObject {
        public int flags;
        public Photo photo;
        public Document document;
        public WebPage webpage;
        public WebDocument webPhoto;
        public WebDocument webDocument;
        public boolean alt_document_set;
        public Document alt_document;
        public byte[] bytes;
        public int ttl_seconds;
        public ArrayList<MessageExtendedMedia> extended_media = new ArrayList<>();
    }

    public static class MessageExtendedMedia extends TLObject {
        public PhotoSize thumb;
        public int w;
        public int h;
        public int video_duration;
        public MessageMedia media;
    }

    public static class TL_messageMediaPhoto extends MessageMedia {
    }

    public static class TL_messageMediaDocument extends MessageMedia {
    }

    public static class TL_messageMediaWebPage extends MessageMedia {
    }

    public static class TL_messageMediaInvoice extends MessageMedia {
    }

    public static class TL_messageMediaGeo extends MessageMedia {
    }

    public static class TL_messageMediaGeoLive extends MessageMedia {
    }

    public static class TL_messageMediaPaidMedia extends MessageMedia {
    }








    /** Instant View HTML h1 (pin: TL_iv.pageBlockHeading1). A6. */

    /** Instant View HTML h2 (pin: TL_iv.pageBlockHeading2). A6. */

    /** Instant View HTML h3 (pin: TL_iv.pageBlockHeading3). A6. */

    /** Instant View HTML h4 (pin: TL_iv.pageBlockHeading4). A6. */

    /** Instant View HTML h5 (pin: TL_iv.pageBlockHeading5). A6. */

    /** Instant View HTML h6 (pin: TL_iv.pageBlockHeading6). A6. */








    /**
     * Nested multi-block quote (pin: TL_iv.pageBlockBlockquoteBlocks).
     * Children live in {@link PageBlock#blocks}; {@link PageBlock#quoteLevels} is set while flattening.
     */









































    /**
     * Instant View / rich-text spoiler wrapper (pin: TL_iv.textSpoiler).
     * Content production for ArticleViewer A3: getText applies
     * {@code TextStyleSpan.FLAG_STYLE_SPOILER} on the nested text.
     */




    public static class TL_messageExtendedMediaPreview extends MessageExtendedMedia {
    }

    public static class TL_messageExtendedMedia extends MessageExtendedMedia {
    }

    public static class TL_messageService extends Message {
    }

    public static class WebPage extends TLObject {
        public int flags;
        public long id;
        public String url;
        public String display_url;
        public String type;
        public String site_name;
        public String title;
        public String description;
        public String author;
        public Photo photo;
        public Document document;
        public TL_iv.Page cached_page;
        public String embed_url;
        public String embed_type;
        public int embed_width;
        public int embed_height;
        public int duration;
        public int hash;
        public int date;
        public ArrayList<WebPageAttribute> attributes = new ArrayList<>();
    }

    public static class TL_webPage extends WebPage {
    }

    public static class TL_webPageNotModified extends WebPage {
        public int cached_page_views;
    }

    public static class WebPageAttribute extends TLObject {
    }

    public static class BotApp extends TLObject {
        public long id;
    }




    public static class TL_null extends TLObject {
        public static final int constructor = 0x56730bcc;
    }

    public static class GeoPoint extends TLObject {
        public double lat;
        public double _long;
    }

    public static class StickerSet extends TLObject {
        public long id;
        public long access_hash;
        public String title;
        public String short_name;
        public int count;
        public int hash;
        public int thumb_dc_id;
        public int thumb_version;
        public ArrayList<PhotoSize> thumbs = new ArrayList<>();
    }

    public static class UserFull extends TLObject {
        public long id;
        public boolean personal;
        public Photo profile_photo;
        public Photo personal_photo;
        public Photo fallback_photo;
        public boolean premium;
    }

    public static class TL_userProfilePhoto extends UserProfilePhoto {
    }

    public static class TL_chatPhoto extends ChatPhoto {
    }

    public static class InputGeoPoint extends TLObject {
        public double lat;
        public double _long;
    }

    public static class InputWebFileLocation extends TLObject {
        public String url;
        public long access_hash;
    }

    public static class TL_inputWebFileLocation extends InputWebFileLocation {
    }

    public static class TL_inputWebFileGeoPointLocation extends InputWebFileLocation {
        public InputGeoPoint geo_point;
        public int w;
        public int h;
        public int zoom;
        public int scale;
    }

    public static class Update extends TLObject {
    }

    public static class TL_updateNewChannelMessage extends Update {
        public Message message;
        public int pts;
        public int pts_count;
    }

    public static class Updates extends TLObject {
        public ArrayList<Update> updates = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public int date;
        public int seq;
    }

    public static class TL_contacts_resolveUsername extends TLObject {
        public String username;
    }

    public static class TL_contacts_resolvedPeer extends TLObject {
        public Peer peer;
        public ArrayList<User> users = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
    }

    public static class TL_channels_joinChannel extends TLObject {
        public InputPeer channel;
    }

    public static class TL_messages_getWebPage extends TLObject {
        public String url;
        public int hash;
    }

    public static class TL_messages_webPage extends TLObject {
        public WebPage webpage;
        public ArrayList<User> users = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
    }

    public static class TL_messages_messages extends TLObject {
        public ArrayList<Message> messages = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
    }

    public static abstract class ReplyMarkup extends TLObject {
        public int flags;
        public boolean resize;
        public boolean single_use;
        public boolean is_persistent;
        public boolean selective;
        public boolean force_reply;
        public String placeholder;
    }

    public static class TL_replyKeyboardMarkup extends ReplyMarkup {
        public static final int constructor = 0x85dd99d1;

        public ArrayList<TL_keyboard.KeyboardButtonRow> rows = new ArrayList<>();
    }

    public static class TL_replyKeyboardHide extends ReplyMarkup {
        public static final int constructor = 0xa03e5b85;
    }

    public static class TL_replyKeyboardForceReply extends ReplyMarkup {
        public static final int constructor = 0x86b40b08;
    }

    public static class TL_replyInlineMarkup extends ReplyMarkup {
        public static final int constructor = 0xB2B15770;

        public ArrayList<TL_keyboard.KeyboardInlineButtonRow> rows = new ArrayList<>();
    }

    public static class TL_messageEntityMention extends MessageEntity {
        public static final int constructor = 0xfa04579d;
    }

    public static class TL_messageEntityHashtag extends MessageEntity {
        public static final int constructor = 0x6f635b0d;
    }

    public static class TL_messageEntityCashtag extends MessageEntity {
        public static final int constructor = 0x4c4e743f;
    }

    public static class PeerColor extends TLObject {
        public int flags;
        public int color;
        public long background_emoji_id;
        public long collectible_id;
        public long gift_emoji_id;
        public int accent_color;
        public ArrayList<Integer> colors;
        public int dark_accent_color;
        public ArrayList<Integer> dark_colors;
    }

    public static class TL_peerColor extends PeerColor {
        public static final int constructor = 0xb54b5acf;
    }

    public static class TL_peerColorCollectible extends PeerColor {
        public static final int constructor = 0xb9c0639a;
    }
}

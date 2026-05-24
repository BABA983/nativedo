package org.telegram.tgnet;

import java.util.ArrayList;

/**
 * Minimal TLRPC stub for NativeDo image pipeline.
 * Only types referenced by ImageLocation/ImageLoader/ImageReceiver/FileLoader are declared.
 * Methods and fields are added as needed when compilation requires them.
 */
public class TLRPC {

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
        public byte[] stripped;
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

    public static class TL_documentAttributeImageSize extends DocumentAttribute {
    }

    public static class TL_document extends Document {
    }

    public static class TL_documentEncrypted extends Document {
    }

    public static class Message extends TLObject {
        public long id;
        public long dialog_id;
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
        public java.util.ArrayList<Object> entities = new java.util.ArrayList<>();
        public Object suggested_post;
        public boolean scheduled;
        public int date;
    }

    public static class MessageAction extends TLObject {
        public Photo photo;
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
    }

    public static class Chat extends TLObject {
        public long id;
        public long access_hash;
        public String title;
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

    public static class TL_inputPeerChat extends InputPeer {
    }

    public static class TL_inputPeerChannel extends InputPeer {
    }

    public static class TL_inputPeerEmpty extends InputPeer {
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
        public Document document;
        public WallPaperSettings settings;
        public boolean pattern;
        public boolean dark;
    }

    public static class WallPaperSettings {
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
        public int accent_color;
        public int outbox_accent_color;
        public ArrayList<Integer> message_colors = new ArrayList<>();
        public WallPaper wallpaper;
        public BaseTheme base_theme;
        public boolean animate_outgoing;
    }

    public static class BaseTheme extends TLObject {
    }

    public static class InputFile extends TLObject {
    }

    public static class InputEncryptedFile extends TLObject {
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

    public static class PageBlock extends TLObject {
        public Photo photo;
        public Document document;
        public String url;
        public String caption;
    }

    public static class TL_pageBlockPhoto extends PageBlock {
        public long photo_id;
    }

    public static class TL_pageBlockVideo extends PageBlock {
        public long video_id;
    }

    public static class TL_messageExtendedMediaPreview extends MessageExtendedMedia {
    }

    public static class TL_messageExtendedMedia extends MessageExtendedMedia {
    }

    public static class TL_messageService extends Message {
    }

    public static class WebPage extends TLObject {
        public long id;
        public String url;
        public String display_url;
        public String type;
        public String site_name;
        public String title;
        public String description;
        public Photo photo;
        public Document document;
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
}

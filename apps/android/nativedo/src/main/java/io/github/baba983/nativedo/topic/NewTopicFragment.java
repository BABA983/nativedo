package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.CreatedTopic;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
import io.github.baba983.nativedo.feature.topic.data.NewTopicDraft;
import io.github.baba983.nativedo.feature.topic.data.NewTopicDraftKey;
import io.github.baba983.nativedo.feature.topic.data.PrivateMessageDraft;
import io.github.baba983.nativedo.feature.topic.data.PrivateMessageDraftKey;
import io.github.baba983.nativedo.messages.PrivateMessagesLoader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Starting a topic on linux.do: a title, a category and a body, sent as the site's own
 * {@code POST /posts.json}.
 *
 * <p>The body is the reply composer - upstream's {@link ChatActivityEnterView}, the same field a
 * topic's reply box is - so a reader writes a topic in the field they write replies in. The title is
 * upstream's titled input ({@link EditTextBoldCursor}, the field its own name screens use), and the
 * category is one of the site's own, chosen from a list and carried on the row with the site's
 * color.</p>
 */
public class NewTopicFragment extends BaseFragment {

    private static final int FIELD_HEIGHT_DP = 36;
    private static final int CATEGORY_ROW_HEIGHT_DP = 50;

    /** The request the attachment sheet's content picker answers on. */
    private static final int PICK_ATTACHMENT_REQUEST = 0x4e45;

    /** The bundle flag that puts the same form into private-message mode, and the recipient to prefill. */
    private static final String ARG_PRIVATE_MESSAGE = "private_message";
    private static final String ARG_RECIPIENT = "recipient";

    private SizeNotifierFrameLayout contentView;
    private EditTextBoldCursor titleField;
    private TextSettingsCell categoryRow;
    /** The recipients field, which stands in for the category row in private-message mode. */
    private EditTextBoldCursor recipientsField;
    /** The recipient a profile passed in, prefilled the first time the form is shown. */
    private String initialRecipient;
    private ChatActivityEnterView composer;
    /**
     * The composer's own delegate, kept so the voice recording host can be bound to it (the delegate
     * carries upstream's microphone gesture in, see {@link DiscourseComposerDelegate}).
     */
    private DiscourseComposerDelegate composerDelegate;
    /** Upstream's floating-composer host: the island bubble the enter view rides in. */
    private org.telegram.ui.Components.chat.ChatInputViewsContainer inputViews;
    private org.telegram.ui.Components.inset.WindowInsetsStateHolder inputInsets;
    private io.github.baba983.nativedo.app.GlassPanelHost glassHost;
    private DiscourseComposerTools composerTools;
    private int bottomInset;
    private final List<CategorySummary> categories = new ArrayList<>();
    /** The category the topic goes to. Nothing is posted without one, so it starts empty. */
    private CategorySummary category;
    private AppContainer container;
    private boolean sending;
    /** True while a stored draft is being put back, so the restore is not written over itself. */
    private boolean restoringDraft;
    private boolean posted;
    private NewTopicDraftKey formDraftKey;
    /** The category id a restored draft asked for, held until the site's categories arrive. */
    private Integer pendingCategoryId;
    /** True when this form writes a private message instead of a public topic. */
    private boolean privateMessageMode;
    /** The private-message draft key, resolved once both the container and the session are known. */
    private PrivateMessageDraftKey pmDraftKey;
    /** Set once the fragment is destroyed, so a write that lands afterwards is dropped. */
    private boolean destroyed;
    /** The attachment sheet, kept so the screen takes its dialog down when it leaves. */
    private DiscourseAttachSheet attachSheet;
    /** True while a picked file is uploading; the sheet's entry takes no second tap. */
    private boolean uploadingAttachment;

    public NewTopicFragment(android.os.Bundle args) {
        super(args);
    }

    public static NewTopicFragment create() {
        return new NewTopicFragment(new android.os.Bundle());
    }

    /**
     * The same composer, writing a private message: the title and the body stay where they are, the
     * category row becomes a recipients field, and [recipient] (a username an entry point already
     * knows, such as a profile the reader pressed 私信 on) starts filled in. It is one screen because
     * the two writes take the same route - {@code POST /posts.json} - and differ only in their
     * fields, so a reader who can start a topic can start a private message in the field they know.
     */
    public static NewTopicFragment privateMessage(String recipient) {
        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean(ARG_PRIVATE_MESSAGE, true);
        if (recipient != null && !recipient.trim().isEmpty()) {
            args.putString(ARG_RECIPIENT, recipient.trim());
        }
        return new NewTopicFragment(args);
    }

    @Override
    protected View createView(Context context) {
        container = NativeDo.container(getParentActivity());
        privateMessageMode = arguments != null && arguments.getBoolean(ARG_PRIVATE_MESSAGE, false);
        initialRecipient = arguments == null ? null : arguments.getString(ARG_RECIPIENT);
        actionBar.setTitle(privateMessageMode ? "写私信" : "发新帖");
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && onBackPressed(true)) {
                    finishFragment();
                } else if (id == 1) {
                    openRichEditor();
                }
            }
        });
        actionBar.createMenu().addItem(1, org.telegram.messenger.R.drawable.msg_edit)
                .setContentDescription("富文本");

        contentView = new SizeNotifierFrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleField = createTitleField(context);
        form.addView(titleField, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, FIELD_HEIGHT_DP, 24, 12, 24, 0));
        if (privateMessageMode) {
            // A private message has no category; it names its recipients instead. The field is the
            // same titled input the title uses, so the form reads as one row of names over a body.
            recipientsField = createRecipientsField(context);
            form.addView(recipientsField, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, FIELD_HEIGHT_DP, 24, 8, 24, 0));
        } else {
            categoryRow = new TextSettingsCell(context, resourceProvider);
            categoryRow.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), 3));
            categoryRow.setOnClickListener(v -> pickCategory());
            form.addView(categoryRow, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, CATEGORY_ROW_HEIGHT_DP));
        }
        contentView.addView(form, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // The body rides in upstream's floating composer island (ChatActivity.createView:4587-4599),
        // the same capsule the reply box uses, instead of a strip pinned across the screen.
        glassHost = new io.github.baba983.nativedo.app.GlassPanelHost(form, contentView);
        inputViews = new org.telegram.ui.Components.chat.ChatInputViewsContainer(context);
        inputViews.setClipChildren(false);
        inputInsets = new org.telegram.ui.Components.inset.WindowInsetsStateHolder(() -> {
            if (inputViews != null) {
                inputViews.checkInsets();
                if (composerTools != null) {
                    composerTools.updatePosition(0);
                }
            }
        });
        inputViews.setWindowInsetsProvider(inputInsets);
        inputViews.getInputIslandBubbleContainer().setClipChildren(false);
        org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider inputProvider =
                org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
                        .bottomPanelChatActivity(resourceProvider);
        inputViews.setInputIslandBubbleDrawable(glassHost.createPanel(inputViews, inputProvider));
        inputViews.setUnderKeyboardBackgroundDrawable(glassHost.createPanel(inputViews, inputProvider));
        contentView.addView(inputViews, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        inputViews.getInputIslandBubbleContainer().addView(createComposer(contentView),
                LayoutHelper.createFrame(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                        Gravity.LEFT | Gravity.BOTTOM, 7, 0, 7, 0));

        composerTools = new DiscourseComposerTools(
                this, container, composer, inputViews, inputInsets, glassHost, null, composerDelegate);
        fragmentView = contentView;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onInsetsInternal);
        if (privateMessageMode) {
            applyInitialRecipient();
        } else {
            showCategory();
            loadCategories();
        }
        // A half-written draft is handed back whole: title, recipients (or category) and body.
        loadDraft();
        return fragmentView;
    }

    /**
     * The title, in upstream's own field for one: the title of a topic is one line, and the field is
     * the one upstream's screens that ask for a name draw.
     */
    private EditTextBoldCursor createTitleField(Context context) {
        EditTextBoldCursor field = new EditTextBoldCursor(context) {
            @Override
            protected Theme.ResourcesProvider getResourcesProvider() {
                return resourceProvider;
            }
        };
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        field.setBackgroundDrawable(null);
        field.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        field.setMaxLines(1);
        field.setLines(1);
        field.setSingleLine(true);
        field.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        field.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        field.setHint("标题");
        // The keyboard's own "next", as upstream's name screens wire it: the next thing a topic has
        // to say is its body, so the press puts the caret there.
        field.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && composer != null) {
                composer.setFieldFocused();
                AndroidUtilities.showKeyboard(composer.getEditField());
                return true;
            }
            return false;
        });
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        field.setCursorSize(AndroidUtilities.dp(20));
        field.setCursorWidth(1.5f);
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable text) {
                updateComposerActions();
                saveDraft();
            }
        });
        return field;
    }

    /**
     * Who a private message goes to: a comma-separated list of usernames, which is what the site's
     * own {@code target_recipients} field takes. It is upstream's titled input, the same control the
     * title uses, and a change in it is part of the draft.
     */
    private EditTextBoldCursor createRecipientsField(Context context) {
        EditTextBoldCursor field = new EditTextBoldCursor(context) {
            @Override
            protected Theme.ResourcesProvider getResourcesProvider() {
                return resourceProvider;
            }
        };
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        field.setBackgroundDrawable(null);
        field.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        field.setMaxLines(1);
        field.setLines(1);
        field.setSingleLine(true);
        field.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        field.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        field.setHint("收件人（用逗号分隔）");
        field.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && composer != null) {
                composer.setFieldFocused();
                AndroidUtilities.showKeyboard(composer.getEditField());
                return true;
            }
            return false;
        });
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        field.setCursorSize(AndroidUtilities.dp(20));
        field.setCursorWidth(1.5f);
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable text) {
                updateComposerActions();
                saveDraft();
            }
        });
        return field;
    }

    /**
     * The recipient an entry point already knew (a profile the reader pressed 私信 on) starts filled
     * in - but only when the reader has no half-written message of their own in the draft, which is
     * theirs and wins.
     */
    private void applyInitialRecipient() {
        if (recipientsField == null || initialRecipient == null || initialRecipient.isEmpty()) {
            return;
        }
        if (container != null) {
            PrivateMessageDraft draft = container.getTopicReplyDraftStore().loadPrivateMessage(pmDraftKey());
            if (!draft.getRecipients().isEmpty()) {
                return;
            }
        }
        recipientsField.setText(initialRecipient);
    }

    /**
     * The body, in upstream's own composer - the field a reply is written in, with the emoji panel
     * and the send button that comes with it.
     */
    private View createComposer(SizeNotifierFrameLayout parent) {
        ChatActivityEnterView view = new ChatActivityEnterView(
                getParentActivity(), parent, null, true, resourceProvider) {
            @Override
            protected void onChangedIslandTotalHeight(float height) {
                if (inputViews != null) {
                    inputViews.setInputBubbleHeight(Math.max(height, AndroidUtilities.dp(44)));
                    if (composerTools != null) {
                        composerTools.updatePosition(0);
                    }
                }
            }
        };
        view.shouldDrawBackground = false;
        view.setMinimumHeight(AndroidUtilities.dp(51));
        view.setInAppInsetsController(inputInsets);
        view.setViewParentForEmoji(inputViews.getInAppKeyboardBubbleContainer());
        view.setAllowStickersAndGifs(false, false, false);
        composerDelegate = new SendDelegate();
        view.setDelegate(composerDelegate);
        // The composer decides the button again whenever it is laid out (a keyboard, an emoji panel,
        // a first layout), so the decision is repeated there too; the island's own height is what
        // the capsule and the insets follow.
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            updateComposerActions();
            if (inputViews != null) {
                inputViews.setInputBubbleHeight(Math.max(view.getIslandTotalHeight(false), AndroidUtilities.dp(44)));
                inputViews.checkInsets();
                if (composerTools != null) {
                    composerTools.updatePosition(0);
                }
            }
        });
        composer = view;
        return view;
    }

    /**
     * The composer's own paperclip: the same Telegram-shaped sheet a reply opens, over the actions
     * the site can carry. A new topic and a private message attach the way a reply does; only the
     * draft identity differs, which is the store's own key, not the sheet's.
     */
    private void openAttachmentSheet() {
        if (getParentActivity() == null || uploadingAttachment || container == null) {
            return;
        }
        attachSheet = new DiscourseAttachSheet(getParentActivity(), resourceProvider,
                this::onAttachAction, this::insertPollRaw);
        showDialog(attachSheet);
    }

    /** The sheet's answer: a picker for a file or the rich editor. The poll stays in the sheet. */
    private void onAttachAction(DiscourseAttachSheet.Action action) {
        switch (action) {
            case GALLERY:
                openAttachmentPicker("image/*");
                break;
            case FILES:
                openAttachmentPicker("*/*");
                break;
            case RICH_EDITOR:
                openRichEditor();
                break;
        }
    }

    /** The system's content picker; what it answers is uploaded by [onActivityResultFragment]. */
    private void openAttachmentPicker(String mimeType) {
        if (getParentActivity() == null || uploadingAttachment) {
            return;
        }
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        startActivityForResult(intent, PICK_ATTACHMENT_REQUEST);
    }

    /**
     * Opens upstream's own rich editor on what is already in the field; its blocks come back here
     * as the markdown the site stores. The same entry the reply screen has, shared with the sheet.
     */
    private void openRichEditor() {
        if (getParentActivity() == null || composer == null) {
            return;
        }
        CharSequence raw = composer.getFieldText();
        presentFragment(new DiscourseRichEditor(raw == null ? "" : raw.toString(), applied -> {
            if (composer != null) {
                composer.setFieldText(applied);
            }
            saveDraft();
        }));
    }

    /**
     * The inline poll's raw, written into the body field at the caret like any other markup. The
     * sheet hands it here when its own Poll form is submitted, so the poll is written where it is
     * read instead of in a second popup.
     */
    private void insertPollRaw(String raw) {
        if (composer == null) {
            return;
        }
        io.github.baba983.nativedo.core.media.ReplyMarkupKt.insertReplyMarkup(composer.getEditField(), raw);
        updateComposerActions();
        saveDraft();
    }

    /** The picker's answer: the file is uploaded and the field gets the site's own markdown. */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != PICK_ATTACHMENT_REQUEST
                || resultCode != android.app.Activity.RESULT_OK
                || data == null) {
            return;
        }
        attachMediaUri(data.getData());
    }

    /**
     * Uploads one readable URI into the body field - the seam a recorder or another screen can
     * drive with a locally written FileProvider URI, without faking an Activity result. A failure
     * leaves the form exactly as the reader typed it and reports the site's or the reader's error.
     */
    public void attachMediaUri(android.net.Uri uri) {
        if (getParentActivity() == null || container == null || uri == null || uploadingAttachment) {
            return;
        }
        DiscourseAttachmentUpload.upload(getParentActivity(), container, uri,
                new DiscourseAttachmentUpload.Sink() {
                    @Override
                    public void onUploadStarted() {
                        uploadingAttachment = true;
                    }

                    @Override
                    public void onUploaded(String markup) {
                        uploadingAttachment = false;
                        io.github.baba983.nativedo.core.media.ReplyMarkupKt.insertReplyMarkup(
                                composer == null ? null : composer.getEditField(), markup);
                        updateComposerActions();
                        saveDraft();
                    }

                    @Override
                    public void onUploadFailed(Throwable error) {
                        uploadingAttachment = false;
                        showError("上传失败", error);
                    }
                });
    }

    /**
     * The site's categories, for the row to choose from. A failure leaves the row saying that no
     * category can be chosen, which is also what the send button reads.
     */
    private void loadCategories() {
        DiscourseLoader.INSTANCE.categories(container,
                loaded -> {
                    categories.clear();
                    for (CategorySummary loadedCategory : loaded) {
                        if (loadedCategory.getParentCategoryId() == null) {
                            categories.add(loadedCategory);
                        }
                    }
                    showCategory();
                    applyPendingCategory();
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    FileLog.e("new topic categories failed", error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** The site's own list of categories, the way upstream offers a choice of its own. */
    private void pickCategory() {
        if (getParentActivity() == null || categories.isEmpty()) {
            return;
        }
        CharSequence[] items = new CharSequence[categories.size()];
        for (int index = 0; index < categories.size(); index++) {
            items[index] = CategoryLabels.of(categories.get(index));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("选择分类");
        builder.setItems(items, (dialog, which) -> {
            if (which >= 0 && which < categories.size()) {
                category = categories.get(which);
                showCategory();
                updateComposerActions();
                saveDraft();
            }
        });
        builder.setNegativeButton("取消", null);
        showDialog(builder.create());
    }

    /** What the row says: the site's name and color, or that there is nothing chosen yet. */
    private void showCategory() {
        if (categoryRow == null) {
            return;
        }
        if (category != null) {
            categoryRow.setTextAndValue("分类", CategoryLabels.of(category), true);
        } else if (categories.isEmpty()) {
            categoryRow.setTextAndValue("分类", "无法读取分类", true);
        } else {
            categoryRow.setTextAndValue("分类", "选择分类", true);
        }
    }

    /**
     * The key a new-topic draft is stored under: the site and the account. There is no topic id
     * yet, so the whole form is one draft per reader, the way the site keeps it.
     */
    private NewTopicDraftKey draftKey() {
        if (formDraftKey != null) {
            return formDraftKey;
        }
        String username = "";
        if (container != null) {
            SessionSnapshot snapshot = container.getSessionStore().snapshot();
            if (snapshot != null) {
                username = snapshot.getUsername();
            }
        }
        return formDraftKey = new NewTopicDraftKey(container == null ? "" : container.getBaseUrl(), username);
    }

    /**
     * The key a private-message draft is stored under: the site and the account, kept apart from the
     * public new-topic draft so the two half-written forms never overwrite each other.
     */
    private PrivateMessageDraftKey pmDraftKey() {
        if (pmDraftKey != null) {
            return pmDraftKey;
        }
        String username = "";
        if (container != null) {
            SessionSnapshot snapshot = container.getSessionStore().snapshot();
            if (snapshot != null) {
                username = snapshot.getUsername();
            }
        }
        return pmDraftKey = new PrivateMessageDraftKey(container == null ? "" : container.getBaseUrl(), username);
    }

    /** Puts the form back: the title, the body, and the recipients (or the category) it carried. */
    private void loadDraft() {
        if (container == null) {
            return;
        }
        restoringDraft = true;
        try {
            if (privateMessageMode) {
                PrivateMessageDraft draft = container.getTopicReplyDraftStore().loadPrivateMessage(pmDraftKey());
                pendingCategoryId = null;
                if (titleField != null && !draft.getTitle().isEmpty()) {
                    titleField.setText(draft.getTitle());
                }
                if (recipientsField != null && !draft.getRecipients().isEmpty()) {
                    recipientsField.setText(draft.getRecipients());
                }
                if (composer != null && !draft.getRaw().isEmpty()) {
                    composer.setFieldText(draft.getRaw());
                }
                updateComposerActions();
                return;
            }
            NewTopicDraft draft = container.getTopicReplyDraftStore().loadNewTopic(draftKey());
            pendingCategoryId = draft.getCategoryId();
            if (titleField != null && !draft.getTitle().isEmpty()) {
                titleField.setText(draft.getTitle());
            }
            if (composer != null && !draft.getRaw().isEmpty()) {
                composer.setFieldText(draft.getRaw());
            }
        } finally {
            restoringDraft = false;
        }
        applyPendingCategory();
        updateComposerActions();
    }

    /** The saved category, once the site's own list has arrived to find it in. */
    private void applyPendingCategory() {
        if (pendingCategoryId == null || categories.isEmpty()) {
            return;
        }
        for (CategorySummary candidate : categories) {
            if (candidate.getId() == pendingCategoryId) {
                category = candidate;
                pendingCategoryId = null;
                showCategory();
                return;
            }
        }
    }

    /** Writes the form down; a restore does not write, and a category-less form still saves. */
    private void saveDraft() {
        if (container == null || restoringDraft || posted) {
            return;
        }
        String title = titleField == null ? "" : titleField.getText().toString();
        String raw = composer == null || composer.getFieldText() == null
                ? "" : composer.getFieldText().toString();
        if (privateMessageMode) {
            String recipients = recipientsField == null ? "" : recipientsField.getText().toString();
            container.getTopicReplyDraftStore().savePrivateMessage(
                    pmDraftKey(), new PrivateMessageDraft(title, recipients, raw));
            return;
        }
        Integer categoryId = category == null ? null : category.getId();
        container.getTopicReplyDraftStore().saveNewTopic(
                draftKey(), new NewTopicDraft(title, categoryId, raw));
    }

    /** Only a write the site accepted earns this; a failure keeps the form for the next press. */
    private void clearDraft() {
        if (container == null) {
            return;
        }
        if (privateMessageMode) {
            container.getTopicReplyDraftStore().savePrivateMessage(
                    pmDraftKey(), new PrivateMessageDraft("", "", ""));
            return;
        }
        container.getTopicReplyDraftStore().saveNewTopic(draftKey(), new NewTopicDraft("", null, ""));
    }

    /** The reader is leaving: the form is written down and the composer told, as a chat screen does. */
    @Override
    public void onPause() {
        super.onPause();
        saveDraft();
        if (composerTools != null) {
            composerTools.onPause();
        }
        if (composer != null) {
            composer.onPause();
        }
    }

    /** The microphone permission answer; the recording host waits for it before a second request. */
    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (composerTools != null) {
            composerTools.onPermissionResult(requestCode);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (composer != null) {
            composer.onResume();
        }
        if (composerTools != null) {
            composerTools.onResume();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (composerTools != null && composerTools.onBackPressed(invoked)) {
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onFragmentDestroy() {
        // A write that has not landed yet must not be written into a screen that is gone.
        destroyed = true;
        saveDraft();
        if (attachSheet != null) {
            attachSheet.dismiss();
            attachSheet = null;
        }
        if (composerTools != null) {
            composerTools.onDestroy();
            composerTools = null;
        }
        if (composer != null) {
            composer.onDestroy();
            composer = null;
        }
        inputViews = null;
        super.onFragmentDestroy();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public androidx.core.view.WindowInsetsCompat onInsetsInternal(@androidx.annotation.NonNull View view,
            @androidx.annotation.NonNull androidx.core.view.WindowInsetsCompat insets) {
        androidx.core.graphics.Insets bars = AndroidUtilities.getDefaultWindowInsets(insets, false);
        onInsets(bars.left, bars.top, bars.right, bars.bottom);
        if (inputInsets != null) {
            inputInsets.setInsets(insets);
        }
        return androidx.core.view.WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        bottomInset = bottom;
        if (inputViews != null) {
            inputViews.checkInsets();
        }
    }

    /**
     * The composer's own state, decided the way the reply box decides it: the button is the send
     * arrow while the body has something in it and this client's own microphone while it is empty.
     * A topic needs more than a body, so what is missing is said when the press happens rather than
     * by a button that never appears.
     */
    private void updateComposerActions() {
        if (composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null
                && composer.getEditField().length() > 0;
        boolean hasMicrophone = composerTools != null && composerTools.isVoiceReady();
        View button = composer.getSendButton();
        if (button == null) {
            return;
        }
        int wanted = hasText || hasMicrophone ? View.VISIBLE : View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
        }
        // The field's own decision runs last, and it is upstream's: it is the same call it makes on
        // every change, and it is what puts the send arrow (or this client's microphone, when the
        // field is empty) in the button. Written the other way round, a change that arrives in one
        // piece - a pasted body, a restored draft - leaves the button drawn from the state before it.
        composer.checkSendButton(false);
    }

    /**
     * What the site is told: the title from the field, the category chosen, and the body as the
     * composer handed it over.
     *
     * <p>The body comes from the press, not from the field: upstream empties the field before it
     * asks its delegate to send - the message it passes is the text the reader wrote - so reading the
     * field here would post an empty topic.</p>
     */
    private void send(CharSequence message) {
        if (container == null || sending) {
            return;
        }
        String title = titleField == null ? "" : titleField.getText().toString().trim();
        String raw = message == null ? "" : message.toString().trim();
        if (raw.isEmpty()) {
            return;
        }
        if (privateMessageMode) {
            sendPrivateMessage(title, raw);
            return;
        }
        // A topic needs three things and the composer only knows the body, so a press that is missing
        // one of the other two says which, rather than doing nothing.
        String missing = title.isEmpty() ? "标题" : category == null ? "分类" : null;
        if (missing != null) {
            composer.setFieldText(raw);
            showError("还不能发帖", new java.io.IOException("请先填写" + missing));
            return;
        }
        sending = true;
        updateComposerActions();
        DiscourseLoader.INSTANCE.newTopic(container, title, category.getId(), raw, created -> {
            sending = false;
            onTopicCreated();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            sending = false;
            if (composer != null) {
                composer.setFieldText(raw);
            }
            saveDraft();
            updateComposerActions();
            showError("发帖失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * A private message goes the same route a topic does - {@code POST /posts.json} - with its
     * recipients and the {@code private_message} archetype in place of a category. The site owns the
     * permission check: a reader who may not message one of the recipients is refused with the
     * site's own words, and nothing is treated as sent. The sender is read from the authenticated
     * session at the press, and a completion that arrives after the account changed (or after this
     * screen is gone) is dropped rather than written into the next account's screen.
     */
    private void sendPrivateMessage(String title, String raw) {
        String recipients = recipientsField == null ? "" : recipientsField.getText().toString().trim();
        String missing = title.isEmpty() ? "标题" : recipients.isEmpty() ? "收件人" : null;
        if (missing != null) {
            composer.setFieldText(raw);
            showError("还不能发送", new java.io.IOException("请先填写" + missing));
            return;
        }
        final String sender = pmDraftKey().getUsername();
        if (sender.isEmpty() || !senderMatches(sender)) {
            composer.setFieldText(raw);
            saveDraft();
            showError("还不能发送", new java.io.IOException("账号已改变，请重新打开私信编辑器"));
            return;
        }
        final int generation = container.getSessionCoordinator().currentRequestContext().getGeneration();
        sending = true;
        updateComposerActions();
        PrivateMessagesLoader.INSTANCE.send(container, title, recipients, raw, created -> {
            sending = false;
            if (destroyed || !senderMatches(sender)
                    || generation != container.getSessionCoordinator().currentRequestContext().getGeneration()) {
                return kotlin.Unit.INSTANCE;
            }
            onPrivateMessageCreated(created);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            sending = false;
            if (destroyed || !senderMatches(sender)
                    || generation != container.getSessionCoordinator().currentRequestContext().getGeneration()) {
                return kotlin.Unit.INSTANCE;
            }
            if (composer != null) {
                composer.setFieldText(raw);
            }
            saveDraft();
            updateComposerActions();
            showError("发送失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The signed-in username, or the empty string when there is no usable session. */
    private String sessionUsername() {
        if (container == null) {
            return "";
        }
        SessionSnapshot snapshot = container.getSessionStore().snapshot();
        if (snapshot == null) {
            return "";
        }
        String username = snapshot.getUsername();
        return username == null ? "" : username;
    }

    /** Whether the account a write was sent for is still the signed-in one. */
    private boolean senderMatches(String sender) {
        return sender.equals(sessionUsername());
    }

    /**
     * The site made the topic for the message. A private message has no list the writer came from -
     * it can start from a profile or the inbox - so the created topic is opened directly, the way a
     * notification's own topic is, and this screen is replaced rather than left behind it.
     */
    private void onPrivateMessageCreated(CreatedTopic created) {
        posted = true;
        if (composer != null) {
            composer.setFieldText("");
        }
        clearDraft();
        String typed = titleField == null ? "" : titleField.getText().toString().trim();
        String topicTitle = created.getTitle().isEmpty() ? typed : created.getTitle();
        TopicFragment topic = TopicFragment.forTopic(created.getTopicId(), topicTitle);
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity()).replace(topic);
        } else {
            presentFragment(topic);
            finishFragment();
        }
    }

    /**
     * The topic is the site's now: this screen has nothing left to do, and the list the reader came
     * from reloads as it comes back - the new topic is the newest thing on it.
     */
    private void onTopicCreated() {
        posted = true;
        if (composer != null) {
            composer.setFieldText("");
        }
        // The topic is the site's now; the draft it came from is not.
        clearDraft();
        finishFragment();
    }

    private void showError(String title, Throwable error) {
        if (getParentActivity() == null) {
            return;
        }
        FileLog.e(title, error);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(error.getMessage() == null ? error.toString() : error.getMessage());
        builder.setPositiveButton("好", null);
        showDialog(builder.create());
    }

    /** The title, the composer and the row, as the render harness drives them. */
    EditTextBoldCursor titleField() {
        return titleField;
    }

    TextSettingsCell categoryRow() {
        return categoryRow;
    }

    /** The recipients field of a private message, for the harness; null on a public new topic. */
    EditTextBoldCursor recipientsField() {
        return recipientsField;
    }

    /** Whether this form writes a private message; the composer's own mode, for a screen test. */
    boolean isPrivateMessageMode() {
        return privateMessageMode;
    }

    /** The screen's own voice host, so a test drives the real record/review/upload contract. */
    ComposerVoiceController voiceControllerForTest() {
        return composerTools == null ? null : composerTools.getVoice();
    }

    /** What the composer tells us; the two messages this screen has an answer for. */
    private class SendDelegate extends DiscourseComposerDelegate {

        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
            send(message);
        }

        @Override
        public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
            updateComposerActions();
            saveDraft();
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }

        /** The paperclip opens the shared attachment sheet, the same one a reply opens. */
        @Override
        public void didPressAttachButton() {
            openAttachmentSheet();
        }
    }
}

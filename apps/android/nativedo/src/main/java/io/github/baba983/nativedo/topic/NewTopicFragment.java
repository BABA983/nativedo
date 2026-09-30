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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
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

    private SizeNotifierFrameLayout contentView;
    private EditTextBoldCursor titleField;
    private TextSettingsCell categoryRow;
    private ChatActivityEnterView composer;
    private final List<CategorySummary> categories = new ArrayList<>();
    /** The category the topic goes to. Nothing is posted without one, so it starts empty. */
    private CategorySummary category;
    private AppContainer container;
    private boolean sending;

    public NewTopicFragment(android.os.Bundle args) {
        super(args);
    }

    public static NewTopicFragment create() {
        return new NewTopicFragment(new android.os.Bundle());
    }

    @Override
    protected View createView(Context context) {
        container = NativeDo.container(getParentActivity());
        actionBar.setTitle("发新帖");
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        contentView = new SizeNotifierFrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleField = createTitleField(context);
        form.addView(titleField, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, FIELD_HEIGHT_DP, 24, 12, 24, 0));
        categoryRow = new TextSettingsCell(context, resourceProvider);
        categoryRow.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 3));
        categoryRow.setOnClickListener(v -> pickCategory());
        form.addView(categoryRow, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, CATEGORY_ROW_HEIGHT_DP));
        contentView.addView(form, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        contentView.addView(createComposer(contentView), LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        fragmentView = contentView;
        showCategory();
        loadCategories();
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
            }
        });
        return field;
    }

    /**
     * The body, in upstream's own composer - the field a reply is written in, with the emoji panel
     * and the send button that comes with it.
     */
    private View createComposer(SizeNotifierFrameLayout parent) {
        ChatActivityEnterView view = new ChatActivityEnterView(
                getParentActivity(), parent, null, true, resourceProvider);
        view.setAllowStickersAndGifs(false, false, false);
        view.setDelegate(new SendDelegate());
        // The composer decides the button again whenever it is laid out (a keyboard, an emoji panel,
        // a first layout), so the decision is repeated there too.
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateComposerActions());
        composer = view;
        return view;
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
     * The composer's own state, decided the way the reply box decides it: the send button is there
     * while the body has something in it, and upstream draws the send arrow into it - a topic needs
     * more than a body, so what is missing is said when the press happens rather than by a button
     * that never appears.
     */
    private void updateComposerActions() {
        if (composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null
                && composer.getEditField().length() > 0;
        View button = composer.getSendButton();
        if (button == null) {
            return;
        }
        int wanted = hasText ? View.VISIBLE : View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
        }
        // The field's own decision runs last, and it is upstream's: it is the same call it makes on
        // every change, and it is what puts the send arrow (not the empty circle of the record state)
        // in the button. Written the other way round, a change that arrives in one piece - a pasted
        // body, a restored draft - leaves the button drawn from the state before it.
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
        // A topic needs three things and the composer only knows the body, so a press that is missing
        // one of the other two says which, rather than doing nothing.
        String missing = title.isEmpty() ? "标题" : category == null ? "分类" : null;
        if (missing != null) {
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
            updateComposerActions();
            showError("发帖失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * The topic is the site's now: this screen has nothing left to do, and the list the reader came
     * from reloads as it comes back - the new topic is the newest thing on it.
     */
    private void onTopicCreated() {
        if (composer != null) {
            composer.setFieldText("");
        }
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

    /** What the composer tells us; the two messages this screen has an answer for. */
    private class SendDelegate extends DiscourseComposerDelegate {

        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
            send(message);
        }

        @Override
        public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
            updateComposerActions();
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }
    }
}

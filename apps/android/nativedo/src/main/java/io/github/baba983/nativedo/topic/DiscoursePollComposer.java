package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.List;

/**
 * A poll, written the way the site stores one.
 *
 * <p>Discourse does not take a Telegram poll: it stores a poll as Markdown raw between
 * {@code [poll]} and {@code [/poll]} with a bulleted list of options, and it parses that raw when
 * the post is cooked (see the site's own poll plugin). This composer asks for the question, the
 * options and the one setting the site reads from the opening tag - multiple answers - and hands
 * back the exact Markdown the site's own composer would write, so the field receives content the
 * site can store rather than a Telegram poll message that has no meaning here.</p>
 *
 * <p>The raw's shape is the site's own, verified against the poll plugin's Markdown rule: the
 * opening tag carries the attributes the plugin reads ({@code type}, {@code results},
 * {@code chartType}), a Markdown heading immediately after it becomes the poll's title
 * ({@code .poll-title}), and each option is one bullet before the closing {@code [/poll]}. The form
 * is a plain view ({@link #inline}) that the attachment sheet embeds above its tabs, so the poll
 * stays in the sheet instead of a second popup. A poll needs a question and at least two options,
 * and a failure keeps what was typed.</p>
 */
public final class DiscoursePollComposer {

    /** Where the finished raw goes; it is inserted into the composer like any other upload markup. */
    public interface Sink {
        void onPoll(String raw);
    }

    /**
     * This form asks for at least two options, on top of its question: a one-option poll is not a
     * poll. The site's own validator would accept a single option (it only requires at least one),
     * so this is the form's rule, not a site limit.
     */
    public static final int MIN_OPTIONS = 2;
    /**
     * The form stops at this many options, the default of the site setting
     * {@code poll_maximum_options} (which a site may set from 2 to 100). The server rejects a poll
     * with more options than that setting, so a form that stops at the default never builds one a
     * stock site would refuse.
     */
    public static final int MAX_OPTIONS = 20;

    private DiscoursePollComposer() {
    }

    /**
     * The site's poll Markdown for [question] and [options]. The question becomes the poll's title
     * line (a heading right after the opening tag, which the site's own poll plugin lifts into the
     * poll's title), and every option is one bullet. Blank questions and blank options are dropped.
     */
    public static String raw(String question, List<String> options) {
        return raw(question, options, false);
    }

    /**
     * The same raw with the one setting the site reads from the opening tag: {@code multiple} when
     * the poll allows more than one answer, {@code regular} otherwise. This is the setting the
     * form's switch writes; the site's poll plugin parses it back into the poll's type.
     */
    public static String raw(String question, List<String> options, boolean multiple) {
        StringBuilder builder = new StringBuilder();
        builder.append("[poll type=").append(multiple ? "multiple" : "regular")
                .append(" results=always chartType=bar]\n");
        if (question != null && !question.trim().isEmpty()) {
            builder.append("# ").append(question.trim()).append('\n');
        }
        if (options != null) {
            for (String option : options) {
                if (option != null && !option.trim().isEmpty()) {
                    builder.append("* ").append(option.trim()).append('\n');
                }
            }
        }
        builder.append("[/poll]");
        return builder.toString();
    }

    // --------------------------------------------------------------- the inline form

    /**
     * The poll form as a view, for a host that wants it in place (the attachment sheet) rather than
     * in its own dialog. The view owns the question field, the option fields, the multiple-answers
     * switch and the Create action; a successful Create hands the site's raw to [sink] and nothing
     * else. The host decides what to do with the sheet afterwards.
     */
    public static Form inline(@NonNull Context context, Theme.ResourcesProvider resourcesProvider,
            @NonNull Sink sink) {
        return new Form(context, resourcesProvider, sink);
    }

    /**
     * The inline poll form. It is a self-contained view: the caller only adds {@link #getView()} to
     * its own hierarchy. {@link #submit()} is what the form's own Create button calls, and it is
     * public so a host can drive the same validation from its own affordance.
     */
    public static final class Form {

        private final Context context;
        private final Theme.ResourcesProvider resourcesProvider;
        private final Sink sink;
        private final LinearLayout root;
        private final EditTextBoldCursor question;
        private final LinearLayout optionsHost;
        private final List<EditTextBoldCursor> optionFields = new ArrayList<>();
        private final TextView helper;
        private final TextView error;
        private final Switch multipleSwitch;

        Form(Context context, Theme.ResourcesProvider resourcesProvider, Sink sink) {
            this.context = context;
            this.resourcesProvider = resourcesProvider;
            this.sink = sink;

            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(6),
                    AndroidUtilities.dp(20), AndroidUtilities.dp(12));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setText("新建投票");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            header.addView(title, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));

            TextView create = new TextView(context);
            create.setText("创建");
            create.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            create.setTypeface(Typeface.DEFAULT_BOLD);
            create.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            create.setGravity(Gravity.CENTER);
            create.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6),
                    AndroidUtilities.dp(14), AndroidUtilities.dp(6));
            create.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                    Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider), 0.12f)));
            create.setContentDescription("创建投票");
            create.setFocusable(true);
            create.setClickable(true);
            create.setOnClickListener(view -> submit());
            header.addView(create, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            root.addView(header, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(6)));

            question = field(context, resourcesProvider, "问一个问题");
            root.addView(question, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(12)));

            // The helper is created before the option rows because adding one refreshes it; it is
            // added to the column below the "add an option" row.
            helper = new TextView(context);
            helper.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            helper.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));

            root.addView(section(context, resourcesProvider, "选项"), LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(2)));
            optionsHost = new LinearLayout(context);
            optionsHost.setOrientation(LinearLayout.VERTICAL);
            root.addView(optionsHost, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addOption();
            addOption();

            TextView add = new TextView(context);
            add.setText("+ 添加选项");
            add.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            add.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));
            add.setContentDescription("添加选项");
            add.setFocusable(true);
            add.setClickable(true);
            add.setOnClickListener(view -> addOption());
            root.addView(add, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(helper, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(12)));

            root.addView(section(context, resourcesProvider, "设置"), LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(2)));
            LinearLayout setting = new LinearLayout(context);
            setting.setOrientation(LinearLayout.HORIZONTAL);
            setting.setGravity(Gravity.CENTER_VERTICAL);
            TextView settingLabel = new TextView(context);
            settingLabel.setText("允许选择多项");
            settingLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            settingLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            setting.addView(settingLabel, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));
            multipleSwitch = new Switch(context, resourcesProvider);
            multipleSwitch.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                    Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            multipleSwitch.setChecked(false, false);
            setting.addView(multipleSwitch, LayoutHelper.createLinear(
                    AndroidUtilities.dp(37), AndroidUtilities.dp(20)));
            root.addView(setting, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            error = new TextView(context);
            error.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            error.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
            error.setVisibility(View.GONE);
            root.addView(error, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, AndroidUtilities.dp(8), 0, 0));

            updateHelper();
        }

        /** The form's view; a host adds it wherever the poll should live. */
        public View getView() {
            return root;
        }

        /** Gives the question field focus, so a sheet can raise the keyboard onto it. */
        public void focusQuestion() {
            question.requestFocus();
        }

        /** Adds one option field, up to the site's cap. */
        public void addOption() {
            if (optionFields.size() >= MAX_OPTIONS) {
                return;
            }
            EditTextBoldCursor field = field(context, resourcesProvider, "选项");
            optionFields.add(field);
            optionsHost.addView(field, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(4)));
            updateHelper();
        }

        /**
         * The form's Create action. A poll needs a question and at least two options; a missing one
         * is named in place and nothing is emitted, so the reader keeps what they typed.
         */
        public void submit() {
            String title = question.getText().toString().trim();
            List<String> parsed = new ArrayList<>();
            for (EditTextBoldCursor field : optionFields) {
                String option = field.getText().toString().trim();
                if (!option.isEmpty()) {
                    parsed.add(option);
                }
            }
            if (title.isEmpty()) {
                error.setText("投票需要一个标题");
                error.setVisibility(View.VISIBLE);
                return;
            }
            if (parsed.size() < MIN_OPTIONS) {
                error.setText("投票至少需要两个选项");
                error.setVisibility(View.VISIBLE);
                return;
            }
            error.setVisibility(View.GONE);
            sink.onPoll(raw(title, parsed, multipleSwitch.isChecked()));
        }

        private void updateHelper() {
            int remaining = MAX_OPTIONS - optionFields.size();
            helper.setText("还可以添加 " + remaining + " 个选项");
            helper.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
        }
    }

    // --------------------------------------------------------------- shared pieces

    private static TextView section(Context context, Theme.ResourcesProvider resourcesProvider, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
        return view;
    }

    private static EditTextBoldCursor field(Context context, Theme.ResourcesProvider resourcesProvider,
            String hint) {
        EditTextBoldCursor field = new EditTextBoldCursor(context) {
            @Override
            protected Theme.ResourcesProvider getResourcesProvider() {
                return resourcesProvider;
            }
        };
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setBackgroundDrawable(null);
        field.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        field.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setCursorSize(AndroidUtilities.dp(20));
        field.setCursorWidth(1.5f);
        return field;
    }
}

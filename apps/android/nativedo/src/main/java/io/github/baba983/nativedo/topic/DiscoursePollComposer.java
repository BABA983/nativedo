package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

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
 *
 * <p>The form is grouped the way the attachment sheet's own panes are: a rounded card per section
 * on the sheet's grey background, each with its inline section title, the create action at the top
 * right of the first card, and the section's rows grouped inside the card. The rows are the
 * upstream cells the real poll pane draws with - {@link HeaderCell} for a section title,
 * {@link PollEditTextCell} for the question and every option, {@link TextCheckCell} for the
 * setting, and {@code TextCell} for the "add an option" row - so the pane reads as the sheet's own
 * poll pane rather than a form bolted onto it. Only what the site stores is drawn: there is no
 * description, quiz mode, anonymous voters, duration, subscriber or media-attachment row, because
 * the site's poll raw has no field for any of them.</p>
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
        private final PollEditTextCell question;
        private final LinearLayout optionsHost;
        private final List<PollEditTextCell> optionFields = new ArrayList<>();
        private final TextCell addOptionRow;
        private final TextView helper;
        private final TextCheckCell multipleCell;
        private final TextView createButton;
        private final TextView error;

        /** Marks the create action as ready the moment the question and its two options are filled. */
        private final TextWatcher fieldWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshCreateAction();
            }
        };

        Form(Context context, Theme.ResourcesProvider resourcesProvider, Sink sink) {
            this.context = context;
            this.resourcesProvider = resourcesProvider;
            this.sink = sink;

            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            root.setClipToPadding(false);
            root.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));

            // --- Question: its own rounded card, with the section title and Create on one row.
            LinearLayout questionCard = card();
            createButton = createAction();
            LinearLayout questionHeader = new LinearLayout(context);
            questionHeader.setOrientation(LinearLayout.HORIZONTAL);
            questionHeader.setGravity(Gravity.CENTER_VERTICAL);
            questionHeader.addView(sectionTitle("问题"),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            questionHeader.addView(createButton, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
            questionCard.addView(questionHeader, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            question = optionField("问一个问题", null);
            questionCard.addView(question, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // --- Options: the title, then the option rows, then the "add an option" row.
            LinearLayout optionsCard = card();
            optionsCard.addView(sectionTitle("选项"), LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            optionsHost = new LinearLayout(context);
            optionsHost.setOrientation(LinearLayout.VERTICAL);
            optionsCard.addView(optionsHost, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addOptionRow = addOptionAction();
            optionsCard.addView(addOptionRow, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // The helper sits on the sheet's grey background, under the options card, the way the
            // sheet's own poll pane counts down the remaining options.
            helper = new TextView(context);
            helper.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            helper.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));

            // --- Settings: the card with the one setting the site reads.
            LinearLayout settingsCard = card();
            settingsCard.addView(sectionTitle("设置"), LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            multipleCell = new TextCheckCell(context, resourcesProvider);
            multipleCell.setTextAndCheck("允许选择多项", false, false);
            multipleCell.getCheckBox().setContentDescription("允许选择多项");
            multipleCell.setOnClickListener(view -> multipleCell.setChecked(!multipleCell.isChecked()));
            settingsCard.addView(multipleCell, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // --- A missing question or too few options is named in place.
            error = new TextView(context);
            error.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            error.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
            error.setVisibility(View.GONE);

            root.addView(questionCard, cardParams(0));
            root.addView(error, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 8, 21, 0));
            root.addView(optionsCard, cardParams(10));
            root.addView(helper, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 8, 21, 0));
            root.addView(settingsCard, cardParams(10));

            // Two option rows to start, the site's own minimum, added last so the option host is
            // already in the tree and the helper row already exists.
            addOption();
            addOption();
            refreshOptions();
        }

        /** The form's view; a host adds it wherever the poll should live. */
        public View getView() {
            return root;
        }

        /**
         * Whether the reader has started a poll: any question text or any option text. The
         * attachment sheet reads this before it lets the poll pane be dismissed, the way upstream's
         * poll pane checks its own fields.
         */
        public boolean hasContent() {
            if (!question.getText().trim().isEmpty()) {
                return true;
            }
            for (PollEditTextCell field : optionFields) {
                if (!field.getText().trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /** Gives the question field focus, so a sheet can raise the keyboard onto it. */
        public void focusQuestion() {
            question.getTextView().requestFocus();
        }

        /** Adds one option field, up to the site's cap. */
        public void addOption() {
            if (optionFields.size() >= MAX_OPTIONS) {
                return;
            }
            // The delete action is upstream's own: the cell's trailing icon removes the row. It is
            // the site's counterpart of the poll pane's per-option action; attaching media to an
            // option is a Telegram poll feature the site's raw has no field for, so it is skipped.
            PollEditTextCell field = optionField("选项",
                    view -> removeOption((PollEditTextCell) view.getParent()));
            optionFields.add(field);
            optionsHost.addView(field, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            refreshOptions();
        }

        /**
         * The form's Create action. A poll needs a question and at least two options; a missing one
         * is named in place and nothing is emitted, so the reader keeps what they typed.
         */
        public void submit() {
            String title = question.getText().trim();
            List<String> parsed = new ArrayList<>();
            for (PollEditTextCell field : optionFields) {
                String option = field.getText().trim();
                if (!option.isEmpty()) {
                    parsed.add(option);
                }
            }
            if (title.isEmpty()) {
                showError("投票需要一个标题");
                return;
            }
            if (parsed.size() < MIN_OPTIONS) {
                showError("投票至少需要两个选项");
                return;
            }
            error.setVisibility(View.GONE);
            sink.onPoll(raw(title, parsed, multipleCell.isChecked()));
        }

        // ----------------------------------------------------------- the form's own pieces

        /** One option row the reader added, dropped from the form; at least one row always stays. */
        private void removeOption(PollEditTextCell field) {
            if (optionFields.size() <= 1) {
                return;
            }
            int index = optionFields.indexOf(field);
            if (index < 0) {
                return;
            }
            optionFields.remove(index);
            optionsHost.removeView(field);
            refreshOptions();
        }

        private void refreshOptions() {
            int remaining = MAX_OPTIONS - optionFields.size();
            helper.setText("还可以添加 " + remaining + " 个选项");
            helper.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
            addOptionRow.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
            refreshCreateAction();
        }

        /**
         * The create action keeps the sheet's own look at either weight: exactly like the alert's
         * done item, it is full strength once the poll is complete and half strength until then,
         * and it stays pressable either way so an incomplete form names what is missing.
         */
        private void refreshCreateAction() {
            createButton.setAlpha(isComplete() ? 1.0f : 0.5f);
        }

        private boolean isComplete() {
            if (question.getText().trim().isEmpty()) {
                return false;
            }
            int filled = 0;
            for (PollEditTextCell field : optionFields) {
                if (!field.getText().trim().isEmpty()) {
                    filled++;
                }
            }
            return filled >= MIN_OPTIONS;
        }

        private void showError(String message) {
            error.setText(message);
            error.setVisibility(View.VISIBLE);
        }

        /** A section's own row, with the sheet's section title styling and heading semantics. */
        private HeaderCell sectionTitle(String text) {
            HeaderCell header = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader,
                    21, 0, false, resourcesProvider);
            header.setText(text);
            return header;
        }

        /** The alert's done item, inlined: a blue pill with its label, at the card's top right. */
        private TextView createAction() {
            TextView create = new TextView(context);
            create.setText("创建");
            create.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            create.setTypeface(AndroidUtilities.bold());
            create.setGravity(Gravity.CENTER);
            create.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            create.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            create.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            create.setContentDescription("创建投票");
            create.setFocusable(true);
            create.setClickable(true);
            ScaleStateListAnimator.apply(create);
            create.setOnClickListener(view -> submit());
            return create;
        }

        /** The "add an option" row: the poll pane's own circle-plus icon and blue label. */
        private TextCell addOptionAction() {
            TextCell add = new TextCell(context, resourcesProvider);
            add.setColors(-1, Theme.key_telegram_color_text);
            Drawable circle = ContextCompat.getDrawable(context,
                    org.telegram.messenger.R.drawable.poll_add_circle);
            Drawable plus = ContextCompat.getDrawable(context,
                    org.telegram.messenger.R.drawable.poll_add_plus);
            if (circle != null && plus != null) {
                circle.setColorFilter(new PorterDuffColorFilter(
                        Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider),
                        PorterDuff.Mode.MULTIPLY));
                plus.setColorFilter(new PorterDuffColorFilter(
                        Theme.getColor(Theme.key_checkboxCheck, resourcesProvider),
                        PorterDuff.Mode.MULTIPLY));
                add.setTextAndIcon("添加选项", new CombinedDrawable(circle, plus), false);
                add.imageLeft = 20;
                add.offsetFromImage = 58;
            } else {
                add.setText("添加选项", false);
            }
            add.setContentDescription("添加选项");
            add.setOnClickListener(view -> addOption());
            return add;
        }

        /**
         * One poll field: the poll pane's own cell, with the site's placeholder as its hint and the
         * cell's own bottom divider, so the rows read as one group inside their card. The question
         * has no per-row action; an option's action removes the row.
         */
        private PollEditTextCell optionField(String hint, View.OnClickListener onDelete) {
            PollEditTextCell field = new PollEditTextCell(context, false,
                    PollEditTextCell.TYPE_DEFAULT, onDelete, resourcesProvider);
            if (field.moveImageView != null) {
                // This form has no drag-reordering host; do not advertise a dead drag handle.
                field.moveImageView.setVisibility(View.INVISIBLE);
            }
            field.setTextAndHint("", hint, true);
            field.addTextWatcher(fieldWatcher);
            return field;
        }

        /** A section's rounded white card, on the sheet's grey background. */
        private LinearLayout card() {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return card;
        }

        private LinearLayout.LayoutParams cardParams(int topMarginDp) {
            return LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    12, topMarginDp, 12, 0);
        }
    }
}

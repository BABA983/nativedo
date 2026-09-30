package io.github.baba983.nativedo.topic;

import org.telegram.ui.Components.ChatActivityEnterView;

/**
 * The composer's delegate for a field that writes to linux.do.
 *
 * <p>Upstream's interface is the whole surface of its chat composer - recording, stickers, editing,
 * slow mode - and a screen that writes a forum post offers none of it. This base answers all of it
 * with "nothing to do", so a screen states only the messages it has an answer for: the text changed,
 * and the send button was pressed.</p>
 */
abstract class DiscourseComposerDelegate implements ChatActivityEnterView.ChatActivityEnterViewDelegate {

    @Override
    public void needSendTyping() {
    }

    @Override
    public void onTextSelectionChanged(int start, int end) {
    }

    @Override
    public void onTextSpansChanged(CharSequence text) {
    }

    @Override
    public void onAttachButtonHidden() {
    }

    @Override
    public void onAttachButtonShow() {
    }

    @Override
    public void onWindowSizeChanged(int size) {
    }

    @Override
    public void onStickersTab(boolean opened) {
    }

    @Override
    public void onMessageEditEnd(boolean loading) {
    }

    @Override
    public void didPressAttachButton() {
    }

    @Override
    public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId, long stars) {
    }

    @Override
    public void toggleVideoRecordingPause() {
    }

    @Override
    public boolean isVideoRecordingPaused() {
        return false;
    }

    @Override
    public void needChangeVideoPreviewState(int state, float seekProgress) {
    }

    @Override
    public void onSwitchRecordMode(boolean video) {
    }

    @Override
    public void onPreAudioVideoRecord() {
    }

    @Override
    public void needStartRecordAudio(int state) {
    }

    @Override
    public void needShowMediaBanHint() {
    }

    @Override
    public void onStickersExpandedChange() {
    }

    @Override
    public void onUpdateSlowModeButton(android.view.View button, boolean show, CharSequence time) {
    }

    @Override
    public void onSendLongClick() {
    }

    @Override
    public void onAudioVideoInterfaceUpdated() {
    }
}

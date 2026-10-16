package io.github.baba983.nativedo.settings;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import io.github.baba983.nativedo.core.session.SessionDiagnostics;

/** Explicit consent and sharing of the client's bounded, allowlisted session evidence. */
public class DiagnosticsFragment extends UniversalFragment {
    private static final int RECORD = 1, EXPORT = 2, CLEAR = 3;
    private boolean busy;
    private boolean destroyed;

    @Override
    protected CharSequence getTitle() {
        return "诊断";
    }

    @Override
    public View createView(Context context) {
        SessionDiagnostics.initialize(context);
        View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRippleCheck(RECORD, "记录会话诊断")
                .setChecked(SessionDiagnostics.isEnabled(getContext())));
        items.add(UItem.asShadow("默认关闭。仅记录会话事件、状态码、Cookie 是否存在及轮换标记，不记录 Cookie 值、账号、网址、正文或草稿。最多保留两份各 256 KiB 的日志，选择会在重启后保留。"));
        items.add(UItem.asButton(EXPORT, busy ? "正在处理…" : "导出诊断日志"));
        items.add(UItem.asShadow("导出包含应用版本与 Android 版本的 ZIP，由你选择分享给谁，不会自动上传。登录失效后仍可导出。关闭记录不会删除已有日志。"));
        items.add(UItem.asButton(CLEAR, "清除诊断日志").red());
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (busy || getParentActivity() == null) return;
        if (item.id == RECORD) {
            if (SessionDiagnostics.isEnabled(getContext())) {
                SessionDiagnostics.setEnabled(getContext(), false);
                listView.adapter.update(true);
            } else {
                showDialog(new AlertDialog.Builder(getParentActivity())
                        .setTitle("记录会话诊断")
                        .setMessage("开启后将保留脱敏的会话事件，帮助排查登录失效。不会记录凭据或正文，也不会自动上传。你可以随时关闭并清除日志。")
                        .setPositiveButton("开启", (dialog, which) -> {
                            SessionDiagnostics.setEnabled(getContext(), true);
                            listView.adapter.update(true);
                        }).setNegativeButton("取消", null).create());
            }
        } else if (item.id == EXPORT) {
            busy = true;
            listView.adapter.update(true);
            final Context context = getContext().getApplicationContext();
            SessionDiagnostics.export(context, file -> {
                busy = false;
                if (destroyed || getParentActivity() == null) return;
                listView.adapter.update(true);
                if (file == null) {
                    notice("导出失败", "无法创建诊断日志，请稍后重试。");
                    return;
                }
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                    Intent send = new Intent(Intent.ACTION_SEND).setType("application/zip")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri("会话诊断", uri));
                    getParentActivity().startActivity(Intent.createChooser(send, "分享诊断日志"));
                } catch (Exception ignored) {
                    notice("分享失败", "无法打开系统分享，请稍后重试。");
                }
            });
        } else if (item.id == CLEAR) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle("清除诊断日志")
                    .setMessage("删除本机的会话诊断日志与导出 ZIP，不影响登录或草稿。已分享出去的文件无法撤回；如果记录仍开启，后续事件会继续记录。")
                    .setPositiveButton("清除", (dialog, which) -> {
                        busy = true;
                        listView.adapter.update(true);
                        SessionDiagnostics.clear(getContext(), success -> {
                            busy = false;
                            if (destroyed || getParentActivity() == null) return;
                            listView.adapter.update(true);
                            notice(success ? "日志已清除" : "清除失败",
                                    success ? "本机诊断日志与导出文件已删除。" : "部分文件无法删除，请稍后重试。");
                        });
                    }).setNegativeButton("取消", null).create());
        }
    }

    private void notice(String title, String message) {
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(title).setMessage(message)
                .setPositiveButton("好", null).create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        listView.setPadding(0, listView.getPaddingTop(), 0, bottom);
        listView.setClipToPadding(false);
    }
}

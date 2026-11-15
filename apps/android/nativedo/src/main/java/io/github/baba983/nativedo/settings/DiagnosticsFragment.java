package io.github.baba983.nativedo.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import io.github.baba983.nativedo.core.session.SessionDiagnostics;

/** Explicit consent and sharing of the client's bounded, allowlisted session evidence. */
public class DiagnosticsFragment extends UniversalFragment {
    private static final int RECORD = 1, EXPORT = 2, CLEAR = 3, SAVE = 4;
    /** The system's document creator; what it answers is written by [onActivityResultFragment]. */
    private static final int SAVE_REQUEST = 4701;
    private boolean busy;
    private boolean destroyed;
    /** The export awaiting a destination, kept only between the export callback and the picker. */
    private File pendingSave;

    @Override
    protected CharSequence getTitle() {
        return "诊断";
    }

    /** Upstream's simple glass header, the same one its settings sub-pages use. */
    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public View createView(Context context) {
        SessionDiagnostics.initialize(context);
        View view = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRippleCheck(RECORD, "记录会话诊断")
                .setChecked(SessionDiagnostics.isEnabled(getContext())));
        items.add(UItem.asShadow("默认关闭。仅记录会话事件、状态码、Cookie 是否存在及轮换标记，不记录 Cookie 值、账号、网址、正文或草稿。最多保留两份各 256 KiB 的日志，选择会在重启后保留。"));
        items.add(UItem.asButton(EXPORT, busy ? "正在处理…" : "分享诊断日志"));
        items.add(UItem.asButton(SAVE, busy ? "正在处理…" : "保存诊断日志到文件"));
        items.add(UItem.asShadow("导出的是包含应用版本与 Android 版本的 ZIP：分享交给系统分享面板，保存则由你在系统文件选择器里挑位置（例如下载目录），两条路都不会自动上传。登录失效后仍可导出。关闭记录不会删除已有日志。"));
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
        } else if (item.id == SAVE) {
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
                pendingSave = file;
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, "session-diagnostics-" + stamp + ".zip");
                try {
                    startActivityForResult(create, SAVE_REQUEST);
                } catch (Exception ignored) {
                    pendingSave = null;
                    notice("保存失败", "无法打开系统文件选择器，请稍后重试。");
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

    /**
     * The document creator's answer: copy the exported ZIP to the place the reader picked. A
     * cancelled picker and a failed write both leave the cached export alone, so the next attempt
     * (or a share) still has it.
     */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != SAVE_REQUEST) return;
        File source = pendingSave;
        pendingSave = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || source == null) {
            return;
        }
        if (destroyed || getContext() == null || getParentActivity() == null) return;
        try (InputStream in = new FileInputStream(source);
             OutputStream out = getContext().getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new java.io.IOException("no output stream for the picked document");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            notice("已保存", "诊断日志已写入你选择的位置，可以回到浏览器上传它。");
        } catch (Exception ignored) {
            notice("保存失败", "无法写入所选位置，请换一个位置重试。");
        }
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
}

/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of org/telegram/ui/ThemeActivity.java, bound to NativeDo's
 * local theme workflow.
 */
package io.github.baba983.nativedo.ui.theme;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.time.SunDate;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ThemeEditorView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

public class ThemeSettingsFragment extends UniversalFragment {

    public static final int REQUEST_IMPORT_THEME = 4201;
    private static final int REQUEST_LOCATION_PERMISSION = 4202;
    public static final int REQUEST_PICK_WALLPAPER = 4203;
    private static final long MAX_THEME_FILE_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_WALLPAPER_FILE_BYTES = 25L * 1024L * 1024L;

    private static final int ACTION_IMPORT = 1;
    private static final int ACTION_CREATE = 2;
    private static final int ACTION_EDIT = 3;
    private static final int ACTION_EXPORT = 4;
    private static final int ACTION_DELETE = 5;
    private static final int ACTION_SCHEDULE_START = 6;
    private static final int ACTION_SCHEDULE_END = 7;
    private static final int ACTION_BRIGHTNESS = 8;
    private static final int ACTION_SCHEDULE_LOCATION = 9;
    private static final int ACTION_SCHEDULE_UPDATE_LOCATION = 10;
    private static final int ACTION_WALLPAPER_COLOR = 11;
    private static final int ACTION_WALLPAPER_LOCAL = 12;
    private static final int ACTION_WALLPAPER_DEFAULT = 13;
    private static final int ACTION_REDUCE_ANIMATIONS = 14;
    private static final int ACTION_DAY_SLOT = 20;
    private static final int ACTION_NIGHT_SLOT = 21;
    private static final int ACTION_AUTO_NIGHT_BASE = 100;

    private TopicThemePreviewContent previewSurface;
    private ThemesHorizontalListCell themesHorizontalListCell;
    private int themesHorizontalListCellType = -1;
    private ThemeAccentsListView accentsListView;
    private FontSizeCell fontSizeCell;
    private final ArrayList<Theme.ThemeInfo> defaultThemes = new ArrayList<>();
    private final ArrayList<Theme.ThemeInfo> customThemes = new ArrayList<>();
    private final ExecutorService themeIoExecutor = Executors.newSingleThreadExecutor();
    private boolean selectingNightTheme;
    private boolean updatingLocation;

    private final LocationListener gpsLocationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) { onLocationReceived(location); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };
    private final LocationListener networkLocationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) { onLocationReceived(location); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    private String getString(int resId) {
        Context context = getContext();
        return context != null ? context.getString(resId) : "";
    }

    @Override
    public View createView(Context context) {
        rebuildThemeLists();
        previewSurface = new TopicThemePreviewContent(context);
        previewSurface.setOnClickListener(view -> openThemePreview(getSelectedTheme(), -1));
        fontSizeCell = new FontSizeCell(context);
        ensureThemePickerViews(context);
        View view = super.createView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        return view;
    }

    private void ensureThemePickerViews(Context context) {
        int type = selectingNightTheme ? ThemesHorizontalListCell.THEME_TYPE_NIGHT : ThemesHorizontalListCell.THEME_TYPE_BASIC;
        if (themesHorizontalListCell == null || themesHorizontalListCellType != type) {
            themesHorizontalListCellType = type;
            themesHorizontalListCell = new ThemesHorizontalListCell(context, this, type, defaultThemes, customThemes) {
                @Override
                protected boolean onThemeClick(Theme.ThemeInfo themeInfo) {
                    openThemePreview(themeInfo, -1);
                    return true;
                }

                @Override
                protected void showOptionsForTheme(Theme.ThemeInfo themeInfo) {
                    if (themeInfo != null && themeInfo.pathToFile != null) {
                        editTheme(themeInfo);
                    }
                }

                @Override
                protected void updateRows() {
                    refreshRows();
                }
            };
            themesHorizontalListCell.setFocusable(false);
            themesHorizontalListCell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(148)
            ));
        }
        if (accentsListView == null) {
            accentsListView = new ThemeAccentsListView(context);
            accentsListView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(62)
            ));
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.theme_settings_title);
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rebuildThemeLists();
        if (getContext() != null) {
            ensureThemePickerViews(getContext());
        }
        Theme.ThemeInfo activeTheme = getSelectedTheme();
        boolean hasAccents = activeTheme != null && activeTheme.themeAccents != null && !activeTheme.themeAccents.isEmpty();

        items.add(UItem.asHeader(getString(R.string.theme_preview_section)));
        items.add(UItem.asCustom(previewSurface, 360));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.theme_picker_section)));
        items.add(UItem.asRadio(ACTION_DAY_SLOT, getString(R.string.theme_day_slot)).setChecked(!selectingNightTheme));
        items.add(UItem.asRadio(ACTION_NIGHT_SLOT, getString(R.string.theme_night_slot)).setChecked(selectingNightTheme));
        if (themesHorizontalListCell != null) {
            themesHorizontalListCell.setDrawDivider(hasAccents);
            items.add(UItem.asCustom(themesHorizontalListCell, 148));
        }
        if (hasAccents && accentsListView != null) {
            accentsListView.bind(activeTheme);
            items.add(UItem.asCustom(accentsListView, 62));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.theme_text_size_section)));
        items.add(UItem.asCustom(fontSizeCell, 52));
        items.add(UItem.asCheck(ACTION_REDUCE_ANIMATIONS, getString(R.string.theme_reduce_animations))
                .setChecked(SharedConfig.reduceAnimations));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.theme_wallpaper_section)));
        items.add(UItem.asButton(ACTION_WALLPAPER_COLOR, getString(R.string.theme_wallpaper_color)));
        items.add(UItem.asButton(ACTION_WALLPAPER_LOCAL, getString(R.string.theme_wallpaper_local)));
        items.add(UItem.asButton(ACTION_WALLPAPER_DEFAULT, getString(R.string.theme_wallpaper_default)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.theme_auto_night_section)));
        addAutoNightMode(items, Theme.AUTO_NIGHT_TYPE_NONE, R.string.theme_auto_night_off);
        addAutoNightMode(items, Theme.AUTO_NIGHT_TYPE_SYSTEM, R.string.theme_auto_night_system);
        addAutoNightMode(items, Theme.AUTO_NIGHT_TYPE_SCHEDULED, R.string.theme_auto_night_schedule);
        addAutoNightMode(items, Theme.AUTO_NIGHT_TYPE_AUTOMATIC, R.string.theme_auto_night_brightness);
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
            items.add(UItem.asCheck(ACTION_SCHEDULE_LOCATION, getString(R.string.theme_schedule_location)).setChecked(Theme.autoNightScheduleByLocation));
            if (Theme.autoNightScheduleByLocation) {
                String city = Theme.autoNightCityName;
                if (city == null || city.isEmpty()) {
                    city = getString(R.string.theme_schedule_location_unavailable);
                }
                items.add(UItem.asButton(ACTION_SCHEDULE_UPDATE_LOCATION, getString(R.string.theme_schedule_update_location), city));
                items.add(UItem.asShadow(getLocationSunString()));
            } else {
                items.add(UItem.asButton(ACTION_SCHEDULE_START, getString(R.string.theme_schedule_start), formatTime(Theme.autoNightDayStartTime)));
                items.add(UItem.asButton(ACTION_SCHEDULE_END, getString(R.string.theme_schedule_end), formatTime(Theme.autoNightDayEndTime)));
            }
        } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
            items.add(UItem.asButton(ACTION_BRIGHTNESS, getString(R.string.theme_brightness_threshold), Math.round(Theme.autoNightBrighnessThreshold * 100) + "%"));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.theme_local_section)));
        items.add(UItem.asButton(ACTION_IMPORT, getString(R.string.theme_import)).accent());
        items.add(UItem.asButton(ACTION_CREATE, getString(R.string.theme_create)));
        if (activeTheme != null && activeTheme.pathToFile != null) {
            items.add(UItem.asButton(ACTION_EDIT, getString(R.string.theme_edit)));
            items.add(UItem.asButton(ACTION_DELETE, getString(R.string.theme_delete)).red());
        }
        items.add(UItem.asButton(ACTION_EXPORT, getString(R.string.theme_export)));
        items.add(UItem.asShadow(null));
    }

    private void addAutoNightMode(ArrayList<UItem> items, int mode, int titleRes) {
        items.add(UItem.asRadio(ACTION_AUTO_NIGHT_BASE + mode, getString(titleRes))
                .setChecked(Theme.selectedAutoNightType == mode));
    }

    private void rebuildThemeLists() {
        defaultThemes.clear();
        customThemes.clear();
        if (Theme.themes == null) {
            return;
        }
        for (int i = 0; i < Theme.themes.size(); i++) {
            Theme.ThemeInfo theme = Theme.themes.get(i);
            if (theme == null) {
                continue;
            }
            if (theme.pathToFile != null) {
                customThemes.add(theme);
            } else {
                defaultThemes.add(theme);
            }
        }
        if (themesHorizontalListCell != null) {
            themesHorizontalListCell.notifyDataSetChanged(listView != null ? listView.getWidth() : 0);
            themesHorizontalListCell.scrollToCurrentTheme(listView != null ? listView.getWidth() : 0, false);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ACTION_AUTO_NIGHT_BASE && item.id < ACTION_AUTO_NIGHT_BASE + 10) {
            setAutoNightMode(item.id - ACTION_AUTO_NIGHT_BASE);
            return;
        }
        switch (item.id) {
            case ACTION_DAY_SLOT:
                selectingNightTheme = false;
                rebuildThemeLists();
                refreshRows();
                break;
            case ACTION_NIGHT_SLOT:
                selectingNightTheme = true;
                rebuildThemeLists();
                refreshRows();
                break;
            case ACTION_IMPORT:
                openThemeFile();
                break;
            case ACTION_CREATE:
                showCreateThemeDialog();
                break;
            case ACTION_EDIT:
                editTheme(getSelectedTheme());
                break;
            case ACTION_EXPORT:
                exportTheme(getSelectedTheme());
                break;
            case ACTION_DELETE:
                confirmDeleteTheme();
                break;
            case ACTION_SCHEDULE_LOCATION:
                Theme.autoNightScheduleByLocation = !Theme.autoNightScheduleByLocation;
                Theme.saveAutoNightThemeConfig();
                if (Theme.autoNightScheduleByLocation) {
                    updateSunTime(null, true);
                }
                Theme.checkAutoNightThemeConditions(true);
                refreshRows();
                break;
            case ACTION_SCHEDULE_UPDATE_LOCATION:
                updateSunTime(null, true);
                break;
            case ACTION_SCHEDULE_START:
                showTimePicker(true);
                break;
            case ACTION_SCHEDULE_END:
                showTimePicker(false);
                break;
            case ACTION_BRIGHTNESS:
                showBrightnessPicker();
                break;
            case ACTION_WALLPAPER_COLOR:
                showWallpaperColorPicker();
                break;
            case ACTION_WALLPAPER_LOCAL:
                openWallpaperFile();
                break;
            case ACTION_WALLPAPER_DEFAULT:
                applyWallpaperOverride(getSelectedTheme(), null);
                break;
            case ACTION_REDUCE_ANIMATIONS:
                SharedConfig.setReduceAnimations(!SharedConfig.reduceAnimations);
                refreshRows();
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void openThemePreview(Theme.ThemeInfo theme, int accentId) {
        if (theme != null) {
            presentFragment(new TopicThemePreviewFragment(theme, selectingNightTheme, accentId));
        }
    }

    private Theme.ThemeInfo getSelectedTheme() {
        return selectingNightTheme ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
    }

    private void setAutoNightMode(int mode) {
        if (Theme.selectedAutoNightType == mode) {
            return;
        }
        Theme.selectedAutoNightType = mode;
        Theme.saveAutoNightThemeConfig();
        if (mode == Theme.AUTO_NIGHT_TYPE_SCHEDULED && Theme.autoNightScheduleByLocation) {
            updateSunTime(null, true);
        }
        Theme.checkAutoNightThemeConditions(true);
        refreshRows();
    }

    private void showTimePicker(boolean start) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        int value = start ? Theme.autoNightDayStartTime : Theme.autoNightDayEndTime;
        new TimePickerDialog(activity, (picker, hour, minute) -> {
            if (start) {
                Theme.autoNightDayStartTime = hour * 60 + minute;
            } else {
                Theme.autoNightDayEndTime = hour * 60 + minute;
            }
            Theme.saveAutoNightThemeConfig();
            Theme.checkAutoNightThemeConditions(true);
            refreshRows();
        }, value / 60, value % 60, true).show();
    }

    private void showBrightnessPicker() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        String[] labels = {"10%", "15%", "20%", "25%", "30%", "40%", "50%"};
        float[] values = {.10f, .15f, .20f, .25f, .30f, .40f, .50f};
        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.theme_brightness_threshold)
                .setItems(labels, (dialog, which) -> {
                    Theme.autoNightBrighnessThreshold = values[which];
                    Theme.saveAutoNightThemeConfig();
                    Theme.checkAutoNightThemeConditions(true);
                    refreshRows();
                })
                .show();
    }

    private void openThemeFile() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "text/plain"});
        activity.startActivityForResult(intent, REQUEST_IMPORT_THEME);
    }

    private void openWallpaperFile() {
        Activity activity = getParentActivity();
        if (activity == null || getSelectedTheme() == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        activity.startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT_THEME && requestCode != REQUEST_PICK_WALLPAPER) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        if (requestCode == REQUEST_IMPORT_THEME) {
            importTheme(data.getData());
        } else {
            importWallpaper(data.getData(), getSelectedTheme());
        }
        return true;
    }

    private void importTheme(Uri uri) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        String displayName = queryDisplayName(uri);
        if (displayName != null && !displayName.toLowerCase(Locale.US).endsWith(".attheme")) {
            showToast(R.string.theme_import_invalid_extension);
            return;
        }
        long declaredSize = querySize(uri);
        if (declaredSize > MAX_THEME_FILE_BYTES) {
            showToast(R.string.theme_import_too_large);
            return;
        }
        themeIoExecutor.execute(() -> {
            String name = displayName;
            if (name == null || name.trim().isEmpty()) {
                name = "imported.attheme";
            }
            if (!name.toLowerCase(Locale.US).endsWith(".attheme")) {
                name += ".attheme";
            }
            name = name.replaceAll("[^a-zA-Z0-9._ -]", "_");
            name = uniqueThemeName(name);
            File source = new File(activity.getCacheDir(), "theme-import-" + System.currentTimeMillis() + ".attheme");
            Theme.ThemeInfo installed = null;
            int errorRes = R.string.theme_import_failed;
            try (InputStream input = activity.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(source)) {
                if (input == null) {
                    throw new IllegalStateException("theme input stream is null");
                }
                copyWithLimit(input, output, MAX_THEME_FILE_BYTES);
                if (!isValidThemeFile(source)) {
                    errorRes = R.string.theme_import_invalid_content;
                    throw new IllegalArgumentException("theme file has no recognized colors or wallpaper");
                }
                Theme.ThemeInfo parsed = Theme.fillThemeValues(source, name, null);
                if (parsed != null) {
                    installed = Theme.installLocalThemeFile(source, name, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (e instanceof FileTooLargeException) {
                    errorRes = R.string.theme_import_too_large;
                }
                installed = null;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                source.delete();
            }
            Theme.ThemeInfo finalInstalled = installed;
            int finalErrorRes = errorRes;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalInstalled == null) {
                    showToast(finalErrorRes);
                    return;
                }
                applyImportedTheme(finalInstalled);
            });
        });
    }

    private void importWallpaper(Uri uri, Theme.ThemeInfo theme) {
        Activity activity = getParentActivity();
        if (activity == null || theme == null) {
            return;
        }
        long declaredSize = querySize(uri);
        if (declaredSize > MAX_WALLPAPER_FILE_BYTES) {
            showToast(R.string.theme_wallpaper_too_large);
            return;
        }
        themeIoExecutor.execute(() -> {
            String fileName = theme.generateWallpaperName(theme.getAccent(false), false);
            File target = new File(ApplicationLoader.getFilesDirFixed(), fileName);
            Theme.OverrideWallpaperInfo info = null;
            int errorRes = R.string.theme_wallpaper_import_failed;
            try (InputStream input = activity.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(target)) {
                if (input == null) {
                    throw new IllegalStateException("wallpaper input stream is null");
                }
                copyWithLimit(input, output, MAX_WALLPAPER_FILE_BYTES);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(target.getAbsolutePath(), options);
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    throw new IllegalArgumentException("wallpaper is not a decodable image");
                }
                info = new Theme.OverrideWallpaperInfo();
                info.fileName = fileName;
                info.originalFileName = fileName;
                info.slug = "";
                info.intensity = 0.5f;
            } catch (Exception e) {
                FileLog.e(e);
                if (e instanceof FileTooLargeException) {
                    errorRes = R.string.theme_wallpaper_too_large;
                }
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            Theme.OverrideWallpaperInfo finalInfo = info;
            int finalErrorRes = errorRes;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalInfo == null) {
                    showToast(finalErrorRes);
                    return;
                }
                applyWallpaperOverride(theme, finalInfo);
            });
        });
    }

    private void showWallpaperColorPicker() {
        Activity activity = getParentActivity();
        Theme.ThemeInfo theme = getSelectedTheme();
        if (activity == null || theme == null) {
            return;
        }
        Theme.OverrideWallpaperInfo current = theme.overrideWallpaper;
        int[] colors = new int[4];
        colors[0] = current != null && current.color != 0
                ? current.color
                : Theme.getColor(Theme.key_chat_wallpaper);
        colors[1] = current != null ? current.gradientColor1 : 0;
        colors[2] = current != null ? current.gradientColor2 : 0;
        colors[3] = current != null ? current.gradientColor3 : 0;
        int count = 1;
        for (int i = 1; i < colors.length; i++) {
            if (colors[i] != 0) {
                count = i + 1;
            }
        }
        ColorPicker picker = new ColorPicker(activity, false, (color, num, applyNow) -> {
            if (num >= 0 && num < colors.length) {
                colors[num] = color;
            }
        });
        picker.setType(0, false, 4, count, false, current != null ? current.rotation : 45, false);
        for (int i = colors.length - 1; i >= 0; i--) {
            picker.setColor(colors[i], i);
        }
        FrameLayout container = new FrameLayout(activity);
        int side = AndroidUtilities.dp(12);
        container.setFocusableInTouchMode(true);
        container.setPadding(side, AndroidUtilities.dp(8), side, 0);
        container.addView(picker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(300)
        ));
        container.requestFocus();
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.theme_wallpaper_color)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (ignoredDialog, which) -> {
                    Theme.OverrideWallpaperInfo info = new Theme.OverrideWallpaperInfo();
                    info.slug = Theme.COLOR_BACKGROUND_SLUG;
                    info.color = colors[0] != 0 ? colors[0] : Color.WHITE;
                    info.gradientColor1 = colors[1];
                    info.gradientColor2 = colors[2];
                    info.gradientColor3 = colors[3];
                    info.rotation = current != null ? current.rotation : 45;
                    info.intensity = 0.5f;
                    applyWallpaperOverride(theme, info);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            );
        }
    }

    private void applyWallpaperOverride(Theme.ThemeInfo theme, Theme.OverrideWallpaperInfo info) {
        if (theme == null) {
            return;
        }
        theme.setOverrideWallpaper(info);
        if (theme == Theme.getActiveTheme()) {
            Theme.reloadWallpaper(true);
        }
        refreshRows();
    }

    private boolean isValidThemeFile(File file) {
        String[] wallpaperLink = new String[1];
        SparseIntArray colors = Theme.getThemeFileValues(file, null, wallpaperLink);
        return colors.size() > 1 || !TextUtils.isEmpty(wallpaperLink[0]);
    }

    private long copyWithLimit(InputStream input, FileOutputStream output, long maxBytes) throws Exception {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new FileTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        if (total == 0) {
            throw new IllegalArgumentException("empty file");
        }
        return total;
    }

    private void applyImportedTheme(Theme.ThemeInfo installed) {
        if (selectingNightTheme) {
            Theme.setCurrentNightTheme(installed);
            Theme.saveAutoNightThemeConfig();
            refreshRows();
            return;
        }
        if (parentLayout == null) {
            Theme.applyTheme(installed, false);
            Theme.turnOffAutoNight(this);
            refreshRows();
            return;
        }
        INavigationLayout.ThemeAnimationSettings settings = new INavigationLayout.ThemeAnimationSettings(
                installed, -1, false, !SharedConfig.animationsEnabled());
        settings.afterAnimationRunnable = this::refreshRows;
        parentLayout.animateThemedValues(settings, null);
        Theme.turnOffAutoNight(this);
    }

    private String uniqueThemeName(String original) {
        String candidate = original;
        int suffix = 2;
        while (Theme.getTheme(candidate) != null) {
            candidate = original.replaceFirst("(?i)\\.attheme$", "") + " (" + suffix++ + ").attheme";
        }
        return candidate;
    }

    private String queryDisplayName(Uri uri) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return null;
        }
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private long querySize(Uri uri) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return -1;
        }
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception ignore) {
        }
        return -1;
    }

    private void showCreateThemeDialog() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.theme_create_hint);
        int padding = AndroidUtilities.dp(24);
        input.setPadding(padding, 0, padding, 0);
        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.theme_create_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "NativeDo Theme";
                    }
                    createTheme(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void createTheme(String name) {
        boolean nightTheme = selectingNightTheme;
        Theme.ThemeInfo sourceTheme = nightTheme ? Theme.getCurrentNightTheme() : Theme.getActiveTheme();
        if (nightTheme && sourceTheme != null && sourceTheme != Theme.getActiveTheme() && parentLayout != null) {
            INavigationLayout.ThemeAnimationSettings settings = new INavigationLayout.ThemeAnimationSettings(
                    sourceTheme, -1, true, !SharedConfig.animationsEnabled());
            settings.afterAnimationRunnable = () -> createAndEditTheme(name, true);
            parentLayout.animateThemedValues(settings, null);
            return;
        }
        createAndEditTheme(name, nightTheme);
    }

    private void createAndEditTheme(String name, boolean nightTheme) {
        Theme.ThemeInfo dayTheme = Theme.getCurrentTheme();
        Theme.ThemeInfo theme = Theme.createNewTheme(name);
        if (nightTheme) {
            Theme.setCurrentNightTheme(theme);
            if (dayTheme != null) {
                Theme.applyTheme(dayTheme, false);
            }
            Theme.applyTheme(theme, true);
            Theme.saveAutoNightThemeConfig();
        }
        refreshRows();
        openEditor(theme);
    }

    private void openEditor(Theme.ThemeInfo theme) {
        Activity activity = getParentActivity();
        if (activity == null || theme == null || theme.pathToFile == null) {
            return;
        }
        new ThemeEditorView().show(activity, theme);
    }

    private void editTheme(Theme.ThemeInfo theme) {
        if (theme == null) {
            return;
        }
        if (theme == Theme.getActiveTheme() || parentLayout == null) {
            openEditor(theme);
            return;
        }
        INavigationLayout.ThemeAnimationSettings settings = new INavigationLayout.ThemeAnimationSettings(
                theme, -1, theme == Theme.getCurrentNightTheme(), !SharedConfig.animationsEnabled());
        settings.afterAnimationRunnable = () -> openEditor(theme);
        parentLayout.animateThemedValues(settings, null);
    }

    private void exportTheme(Theme.ThemeInfo theme) {
        Activity activity = getParentActivity();
        if (activity == null || theme == null) {
            return;
        }
        String fileName = theme.getName();
        if (!fileName.toLowerCase(Locale.US).endsWith(".attheme")) {
            fileName += ".attheme";
        }
        final String finalFileName = fileName.replaceAll("[^a-zA-Z0-9._ -]", "_");
        themeIoExecutor.execute(() -> {
            File output = new File(activity.getCacheDir(), finalFileName);
            Exception error = null;
            try {
                if (theme.pathToFile != null) {
                    if (!AndroidUtilities.copyFile(new File(theme.pathToFile), output)) {
                        throw new IllegalStateException("theme copy failed");
                    }
                } else if (theme.assetName != null) {
                    try (InputStream input = ApplicationLoader.applicationContext.getAssets().open(theme.assetName);
                         FileOutputStream stream = new FileOutputStream(output)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            stream.write(buffer, 0, read);
                        }
                    }
                } else {
                    throw new IllegalStateException("theme has no local source");
                }
            } catch (Exception e) {
                error = e;
                FileLog.e(e);
            }
            Exception finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalError != null) {
                    showToast(R.string.theme_export_failed);
                    return;
                }
                try {
                    Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", output);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/octet-stream");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(Intent.createChooser(share, getString(R.string.theme_export)));
                } catch (Exception e) {
                    showToast(R.string.theme_export_failed);
                }
            });
        });
    }

    private void confirmDeleteTheme() {
        Activity activity = getParentActivity();
        Theme.ThemeInfo theme = getSelectedTheme();
        if (activity == null || theme == null || theme.pathToFile == null) {
            return;
        }
        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.theme_delete_title)
                .setMessage(R.string.theme_delete_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteTheme(theme))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteTheme(Theme.ThemeInfo theme) {
        Theme.ThemeInfo dayTheme = Theme.getCurrentTheme();
        Theme.ThemeInfo nightTheme = Theme.getCurrentNightTheme();
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        boolean deletingNightTheme = theme == nightTheme;
        boolean restoreActiveNightTheme = theme == dayTheme && activeTheme == nightTheme;
        boolean activeThemeChanged = activeTheme == theme;

        if ((deletingNightTheme && activeThemeChanged || restoreActiveNightTheme) && dayTheme != null) {
            Theme.applyTheme(dayTheme, false);
        }
        boolean currentThemeDeleted = Theme.deleteTheme(theme);
        if (restoreActiveNightTheme && nightTheme != null) {
            Theme.applyTheme(nightTheme, true);
        }
        if (deletingNightTheme) {
            Theme.saveAutoNightThemeConfig();
        }
        if ((currentThemeDeleted || activeThemeChanged) && parentLayout instanceof org.telegram.ui.ActionBar.ActionBarLayout) {
            ((org.telegram.ui.ActionBar.ActionBarLayout) parentLayout).rebuildAllFragmentViews(true);
        } else {
            refreshRows();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated);
    }

    private void refreshRows() {
        if (previewSurface != null) {
            previewSurface.updateColors();
        }
        rebuildThemeLists();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private String formatTime(int minutes) {
        return String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60);
    }

    private String getLocationSunString() {
        int sunriseHour = Theme.autoNightSunriseTime / 60;
        int sunriseMinute = Theme.autoNightSunriseTime - sunriseHour * 60;
        int sunsetHour = Theme.autoNightSunsetTime / 60;
        int sunsetMinute = Theme.autoNightSunsetTime - sunsetHour * 60;
        return getString(R.string.theme_schedule_location_info)
                .replace("%1$s", String.format(Locale.getDefault(), "%02d:%02d", sunsetHour, sunsetMinute))
                .replace("%2$s", String.format(Locale.getDefault(), "%02d:%02d", sunriseHour, sunriseMinute));
    }

    private void updateSunTime(Location lastKnownLocation, boolean forceUpdate) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_LOCATION_PERMISSION);
                return;
            }
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                showToast(R.string.theme_schedule_location_disabled);
                try {
                    activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                } catch (Exception ignore) {
                }
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (lastKnownLocation == null || forceUpdate) {
            startLocationUpdate();
            if (lastKnownLocation == null) {
                return;
            }
        }
        onLocationReceived(lastKnownLocation);
    }

    private void startLocationUpdate() {
        if (updatingLocation) {
            return;
        }
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return;
        }
        updatingLocation = true;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, gpsLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0, networkLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void stopLocationUpdate() {
        updatingLocation = false;
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(gpsLocationListener);
            locationManager.removeUpdates(networkLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void onLocationReceived(Location location) {
        if (location == null) {
            return;
        }
        stopLocationUpdate();
        Theme.autoNightLocationLatitude = location.getLatitude();
        Theme.autoNightLocationLongitude = location.getLongitude();
        int[] time = SunDate.calculateSunriseSunset(Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude);
        Theme.autoNightSunriseTime = time[0];
        Theme.autoNightSunsetTime = time[1];
        Theme.autoNightCityName = null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        Theme.autoNightLastSunCheckDay = calendar.get(Calendar.DAY_OF_MONTH);
        Theme.saveAutoNightThemeConfig();
        if (Theme.autoNightScheduleByLocation && Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
            Theme.checkAutoNightThemeConditions();
        }
        refreshRows();
        themeIoExecutor.execute(() -> {
            String name = null;
            try {
                Geocoder geocoder = new Geocoder(ApplicationLoader.applicationContext, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    name = addresses.get(0).getLocality();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (name == null || name.isEmpty()) {
                name = String.format(Locale.US, "(%.06f, %.06f)", Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude);
            }
            String finalName = name;
            AndroidUtilities.runOnUIThread(() -> {
                Theme.autoNightCityName = finalName;
                Theme.saveAutoNightThemeConfig();
                refreshRows();
            });
        });
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return;
        }
        if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateSunTime(null, true);
        } else {
            showToast(R.string.theme_schedule_location_permission);
        }
    }

    @Override
    public void onFragmentDestroy() {
        stopLocationUpdate();
        themeIoExecutor.shutdownNow();
        super.onFragmentDestroy();
    }

    private void showToast(int resId) {
        Activity activity = getParentActivity();
        if (activity != null) {
            Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            if (previewSurface != null) {
                previewSurface.updateColors();
            }
            if (themesHorizontalListCell != null) {
                themesHorizontalListCell.invalidate();
            }
            if (accentsListView != null) {
                accentsListView.invalidate();
            }
            if (fontSizeCell != null) {
                fontSizeCell.updateColors();
            }
        };
        descriptions.add(description(actionBar, ThemeDescription.FLAG_BACKGROUND, Theme.key_windowBackgroundWhite, delegate));
        descriptions.add(description(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, Theme.key_windowBackgroundWhiteBlackText, delegate));
        descriptions.add(description(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, Theme.key_windowBackgroundWhiteBlackText, delegate));
        descriptions.add(description(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, Theme.key_actionBarActionModeDefaultSelector, delegate));
        descriptions.add(description(listView, ThemeDescription.FLAG_BACKGROUND, Theme.key_windowBackgroundGray, delegate));
        descriptions.add(description(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, Theme.key_windowBackgroundGray, delegate));
        descriptions.add(description(listView, ThemeDescription.FLAG_SELECTOR, Theme.key_listSelector, delegate));

        addCellFieldDescription(descriptions, TextCell.class, "textView", Theme.key_windowBackgroundWhiteBlackText, delegate);
        addCellFieldDescription(descriptions, TextCell.class, "valueTextView", Theme.key_windowBackgroundWhiteValueText, delegate);
        addCellFieldDescription(descriptions, TextCheckCell.class, "titleView", Theme.key_windowBackgroundWhiteBlackText, delegate);
        addCellFieldDescription(descriptions, DialogRadioCell.class, "textView", Theme.key_windowBackgroundWhiteBlackText, delegate);
        addCellFieldDescription(descriptions, DialogRadioCell.class, "valueTextView", Theme.key_windowBackgroundWhiteValueText, delegate);
        addCellFieldDescription(descriptions, HeaderCell.class, "textView", Theme.key_windowBackgroundWhiteBlueHeader, delegate);

        int[] previewKeys = {
                Theme.key_chat_wallpaper,
                Theme.key_chat_inBubble,
                Theme.key_chat_messageTextIn,
                Theme.key_chat_inTimeText,
                Theme.key_divider
        };
        for (int key : previewKeys) {
            descriptions.add(description(previewSurface, 0, key, delegate));
        }
        return descriptions;
    }

    private ThemeDescription description(View view, int flags, int key, ThemeDescription.ThemeDescriptionDelegate delegate) {
        return new ThemeDescription(view, flags, null, null, null, delegate, key, null);
    }

    private void addCellFieldDescription(ArrayList<ThemeDescription> descriptions, Class<?> cellClass, String field, int key, ThemeDescription.ThemeDescriptionDelegate delegate) {
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR,
                new Class[]{cellClass}, new String[]{field}, null, null, delegate, key));
    }

    private class FontSizeCell extends FrameLayout {
        private static final int MIN_FONT_SIZE = 12;
        private static final int MAX_FONT_SIZE = 30;

        private final SeekBarView sizeBar;
        private final TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private boolean changedWhileDragging;

        FontSizeCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            valuePaint.setTextSize(AndroidUtilities.dp(16));
            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(MAX_FONT_SIZE - MIN_FONT_SIZE + 1);
            sizeBar.setProgress((SharedConfig.fontSize - MIN_FONT_SIZE) / (float) (MAX_FONT_SIZE - MIN_FONT_SIZE));
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int size = Math.round(MIN_FONT_SIZE + (MAX_FONT_SIZE - MIN_FONT_SIZE) * progress);
                    if (SharedConfig.setFontSize(size)) {
                        changedWhileDragging = true;
                        if (previewSurface != null) {
                            previewSurface.updateTextSizes();
                        }
                        invalidate();
                    }
                    if (stop && changedWhileDragging) {
                        changedWhileDragging = false;
                        SharedConfig.notifyFontSizeChanged();
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(SharedConfig.fontSize);
                }

                @Override
                public int getStepsCount() {
                    return MAX_FONT_SIZE - MIN_FONT_SIZE;
                }
            });
            sizeBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(sizeBar, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    38,
                    Gravity.CENTER_VERTICAL,
                    5,
                    0,
                    48,
                    0
            ));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            valuePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            String value = String.valueOf(SharedConfig.fontSize);
            canvas.drawText(value, getWidth() - AndroidUtilities.dp(38), AndroidUtilities.dp(32), valuePaint);
        }

        void updateColors() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            sizeBar.invalidate();
            invalidate();
        }
    }

    private static final class FileTooLargeException extends Exception {
    }

    private class ThemeAccentsListView extends RecyclerListView {
        private final ThemeAccentsAdapter adapter = new ThemeAccentsAdapter();
        private Theme.ThemeInfo themeInfo;

        ThemeAccentsListView(Context context) {
            super(context);
            setFocusable(false);
            setItemAnimator(null);
            setLayoutAnimation(null);
            setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), 0);
            setClipToPadding(false);
            LinearLayoutManager layoutManager = new LinearLayoutManager(context);
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            setLayoutManager(layoutManager);
            setAdapter(adapter);
            setOnItemClickListener((view, position) -> {
                if (themeInfo == null || themeInfo.themeAccents == null || position < 0 || position >= themeInfo.themeAccents.size()) {
                    return;
                }
                Theme.ThemeAccent accent = themeInfo.themeAccents.get(position);
                openThemePreview(themeInfo, accent.id);
            });
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        void bind(Theme.ThemeInfo theme) {
            themeInfo = theme;
            adapter.notifyDataSetChanged();
            if (theme != null && theme.themeAccents != null) {
                for (int i = 0; i < theme.themeAccents.size(); i++) {
                    if (theme.themeAccents.get(i).id == theme.currentAccentId) {
                        scrollToPosition(i);
                        break;
                    }
                }
            }
        }

        private class ThemeAccentsAdapter extends SelectionAdapter {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new Holder(new AccentDotView(parent.getContext()));
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                AccentDotView view = (AccentDotView) holder.itemView;
                Theme.ThemeAccent accent = themeInfo.themeAccents.get(position);
                view.setAccent(accent.accentColor, themeInfo.currentAccentId == accent.id);
            }

            @Override
            public int getItemCount() {
                return themeInfo != null && themeInfo.themeAccents != null ? themeInfo.themeAccents.size() : 0;
            }
        }
    }

    private static class AccentDotView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;
        private boolean checked;

        AccentDotView(Context context) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.dp(2));
        }

        void setAccent(int accentColor, boolean selected) {
            color = accentColor;
            checked = selected;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(62), AndroidUtilities.dp(62));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getMeasuredWidth() / 2f;
            float cy = getMeasuredHeight() / 2f;
            float radius = AndroidUtilities.dp(18);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, radius, paint);
            if (checked) {
                stroke.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(3), stroke);
                stroke.setColor(Theme.getColor(Theme.key_featuredStickers_addedIcon));
                canvas.drawCircle(cx, cy, radius + AndroidUtilities.dp(2), stroke);
            }
        }
    }
}

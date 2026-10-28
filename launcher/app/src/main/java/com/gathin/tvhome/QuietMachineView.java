package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Quiet Machine (cleandavystv fork): an offline, native Home / Library / Control surface laid over the Classic
 * launcher, which stays alive underneath for its launch, input and remote logic. AppAdapter remains the authority
 * for configured tiles and launch actions. Nothing shown here is invented: unavailable data says so.
 */
final class QuietMachineView extends LinearLayout {
    private static final int ORANGE = 0xfff36b21, ON_ORANGE = 0xfffaf8f2;
    /** Palette tokens: Dark (the default) or Light, switched from Control and saved. */
    private int PAPER, RAIL, INSET, INK, LINE, TRACK, MUTED, GREEN;
    private static final String[] SCREENS = {"Home", "Library", "Search", "Settings"};
    private static final int[] SCREEN_GLYPHS = {Glyph.HOME, Glyph.LIBRARY, Glyph.SEARCH, Glyph.SETTINGS};
    private static final String[] TABS = {"All", "Movies", "Series", "Documentaries", "Music"};
    private static final String[] MODES = {"Off", "Day", "Evening", "Night"};
    /** Shown on screen with each photograph: the attribution its licence asks for (CREDITS.md). */
    private static final String[] PICTURE_CREDITS = {"Los Angeles  ·  Nserrano, CC BY-SA 3.0",
            "USC, Bovard Auditorium  ·  EEJCC, CC BY-SA 4.0", "Florence  ·  Giorgio Galeotti, CC BY 4.0"};

    private final Activity activity;
    private final AppAdapter adapter;
    private final Runnable inputs, classic;
    private final Config config;
    private final SharedPreferences prefs;
    private final Typeface light, regular, medium, variable;
    private final LinearLayout rail, body;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ArrayList<AppAdapter.Entry> catalog = new ArrayList<>();
    private final ArrayList<AppAdapter.Entry> filtered = new ArrayList<>();
    private final LruCache<String, Drawable> art = new LruCache<>(48);
    private final Map<String, LinearLayout> tabViews = new HashMap<>();
    private String screen = "Home", category = "All", query = "";
    private TextView greeting, credit, catalogMessage, volumeValue, volumeNote, focusValue;
    private Meter volume;
    private View statusDot, inputAnchor, defaultFocus;
    private RecyclerView results;
    private int photoGeneration, generation;
    private boolean loaded, disposed;
    private final BroadcastReceiver ticks = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(i.getAction())) syncVolume();
            else resume();
        }
    };

    QuietMachineView(Activity a, AppAdapter apps, Runnable showInputs, Runnable showClassic) {
        super(a);
        activity = a; adapter = apps; inputs = showInputs; classic = showClassic;
        config = Config.load(a);
        prefs = a.getPreferences(0);
        palette(prefs.getBoolean("quiet_dark", true));
        light = UiFont.get(a, R.string.font_light);
        regular = UiFont.get(a, R.string.font_regular);
        medium = UiFont.get(a, R.string.font_medium);
        variable = UiFont.get(a, R.string.font_body);
        setOrientation(HORIZONTAL);
        setBackgroundColor(PAPER);
        rail = column();
        rail.setBackgroundColor(RAIL);
        rail.setPadding(d(22), d(52), d(8), d(24));
        addView(rail, new LayoutParams(d(166), -1));
        View divider = new View(a);
        divider.setBackgroundColor(LINE);
        addView(divider, new LayoutParams(d(1), -1));
        body = column();
        body.setPadding(d(28), d(30), d(36), d(22));
        addView(body, new LayoutParams(0, -1, 1));
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(ticks, filter, Context.RECEIVER_EXPORTED);
        else a.registerReceiver(ticks, filter);
        applyFocusMode();
        show("Home");
    }

    private void palette(boolean dark) {
        PAPER = dark ? 0xff1b1b19 : 0xfff2f0e9;
        RAIL = dark ? 0xff222220 : 0xffebe8df;
        INSET = dark ? 0xff2f2e2b : 0xffe2dfd5;
        INK = dark ? 0xffeceae2 : 0xff222220;
        LINE = dark ? 0xff3d3c38 : 0xffc9c7be;
        TRACK = dark ? 0xff4b4a45 : 0xffbcb9af;
        MUTED = dark ? 0xff9c9a90 : 0xff6c6a62;
        GREEN = dark ? 0xff6fae7d : 0xff5e9a6c;
    }
    private void toggleAppearance() {
        boolean dark = !prefs.getBoolean("quiet_dark", true);
        prefs.edit().putBoolean("quiet_dark", dark).apply();
        palette(dark);
        setBackgroundColor(PAPER);
        rail.setBackgroundColor(RAIL);
        getChildAt(1).setBackgroundColor(LINE);
        show(screen);
        post(() -> { View t = findViewWithTag("appearance"); if (t != null) t.requestFocus(); });
    }

    // ---- shared tokens and components ----------------------------------------------------------------

    private int d(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(VERTICAL); return v; }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private TextView text(String value, float sp, Typeface face, int color) {
        TextView v = new TextView(activity);
        v.setText(value); v.setTextSize(sp); v.setTypeface(face); v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setFontFeatureSettings("tnum");
        return v;
    }
    /** Small spaced capitals: labels, tabs and the rail. */
    private TextView caps(String value, float sp, Typeface face, int color) {
        TextView v = text(value.toUpperCase(Locale.ROOT), sp, face, color);
        v.setLetterSpacing(.16f);
        v.setSingleLine();
        return v;
    }
    private static GradientDrawable frame(int fill, int stroke, int width, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(radius); g.setStroke(width, stroke);
        return g;
    }
    private Drawable resting() { return frame(PAPER, LINE, d(1), d(2)); }
    /** Focus is never colour alone: a thicker outline and an inset fill (tiles add an underline). */
    private Drawable focusRing() { return frame(INSET, ORANGE, d(2), d(2)); }
    private void focusable(View v, boolean framed, Runnable action, Consumer<Boolean> also) {
        v.setFocusable(true);
        v.setClickable(true);
        Drawable rest = framed ? resting() : null;
        v.setBackground(rest);
        v.setOnClickListener(w -> action.run());
        v.setOnFocusChangeListener((w, focus) -> {
            w.setBackground(focus ? focusRing() : framed ? resting() : null);
            if (also != null) also.accept(focus);
        });
    }
    private void space(LinearLayout parent, int dp) { parent.addView(new View(activity), new LayoutParams(1, d(dp))); }
    private View flex() { View v = new View(activity); v.setLayoutParams(new LayoutParams(0, 0, 1)); return v; }
    private View hairline(boolean vertical, int length) {
        View v = new View(activity);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(vertical ? new LayoutParams(d(1), d(length)) : new LayoutParams(-1, d(1)));
        return v;
    }
    private ImageView glyph(int type, int color, boolean solid, int size) {
        ImageView v = new ImageView(activity);
        v.setImageDrawable(new Glyph(type, color, solid));
        v.setLayoutParams(new LayoutParams(d(size), d(size)));
        return v;
    }
    private static ColorMatrixColorFilter mono() {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0);
        return new ColorMatrixColorFilter(m);
    }
    private TextView quietButton(String label, Runnable action) {
        TextView b = caps(label, 10.5f, medium, INK);
        b.setGravity(Gravity.CENTER);
        b.setPadding(d(16), d(11), d(16), d(11));
        focusable(b, true, action, null);
        return b;
    }

    // ---- MainActivity hooks ---------------------------------------------------------------------------

    void home() { show("Home"); }
    void back() { if (!screen.equals("Home")) home(); else requestDefault(); }
    void focusDefault() { if (!hasFocus()) requestDefault(); }
    private void requestDefault() {
        if (defaultFocus != null && defaultFocus.isAttachedToWindow() && defaultFocus.requestFocus()) return;
        body.requestFocus(View.FOCUS_DOWN);
    }
    void refresh() {
        loaded = false;
        art.evictAll();
        generation++;
        if (screen.equals("Home")) {
            View focused = findFocus();
            Object key = focused == null ? null : focused.getTag();
            show("Home");
            if (key != null) post(() -> { View target = findViewWithTag(key); if (target != null) target.requestFocus(); });
        } else if (screen.equals("Library") || screen.equals("Search")) loadCatalog();
    }
    void resume() {
        updateGreeting();
        syncVolume();
        if (statusDot != null) statusDot.setVisibility(online() ? VISIBLE : INVISIBLE);
    }
    void dispose() {
        disposed = true;
        generation++;
        loader.shutdownNow();
        try { activity.unregisterReceiver(ticks); } catch (IllegalArgumentException ignored) { /* never registered */ }
    }
    View inputAnchor() { return inputAnchor == null ? this : inputAnchor; }

    @Override public View focusSearch(View focused, int direction) {
        View candidate = super.focusSearch(focused, direction);
        // No edge may fall through to the Classic surface underneath.
        for (ViewParent p = candidate == null ? null : candidate.getParent(); p != null; p = p.getParent()) {
            if (p == this) return candidate;
        }
        return focused;
    }

    // ---- rail -----------------------------------------------------------------------------------------

    private void show(String next) {
        photoGeneration++;
        screen = next;
        greeting = credit = catalogMessage = volumeValue = volumeNote = focusValue = null;
        volume = null; statusDot = null; inputAnchor = null; defaultFocus = null; results = null;
        tabViews.clear();
        body.removeAllViews();
        rail.removeAllViews();
        for (int i = 0; i < SCREENS.length; i++) rail.addView(railItem(SCREENS[i], SCREEN_GLYPHS[i], SCREENS[i].equals(next)));
        if (next.equals("Home")) homeContent();
        else if (next.equals("Settings")) controlContent();
        else libraryContent(next.equals("Search"));
        linkRail(next);
        post(this::requestDefault);
    }
    /** Left from Home's first column lands on the current rail item; Right from the rail on the screen's main control. */
    private void linkRail(String current) {
        View active = rail.findViewWithTag("rail:" + current);
        active.setId(View.generateViewId());
        for (String tag : new String[]{"watch", "photograph"}) {
            View v = body.findViewWithTag(tag);
            if (v != null) v.setNextFocusLeftId(active.getId());
        }
        if (defaultFocus == null) return;
        if (defaultFocus.getId() == View.NO_ID) defaultFocus.setId(View.generateViewId());
        for (int i = 0; i < rail.getChildCount(); i++) rail.getChildAt(i).setNextFocusRightId(defaultFocus.getId());
    }
    private View railItem(String name, int type, boolean active) {
        LinearLayout item = row();
        item.setPadding(d(8), 0, d(8), 0);
        View dot = new View(activity);
        dot.setBackground(active ? frame(ORANGE, ORANGE, 0, d(3)) : null);
        LayoutParams dp = new LayoutParams(d(6), d(6));
        dp.setMarginEnd(d(10));
        item.addView(dot, dp);
        ImageView icon = glyph(type, active ? INK : MUTED, active, 15);
        ((LayoutParams) icon.getLayoutParams()).setMarginEnd(d(12));
        item.addView(icon);
        item.addView(caps(name, 10, active ? medium : regular, active ? INK : MUTED));
        item.setTag("rail:" + name);
        item.setContentDescription(name + (active ? ", current" : ""));
        focusable(item, false, () -> show(name), null);
        LayoutParams p = new LayoutParams(-1, d(40));
        p.bottomMargin = d(8);
        item.setLayoutParams(p);
        return item;
    }
    private LinearLayout tab(String name, boolean active, Runnable action) {
        LinearLayout t = column();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(d(10), d(9), d(10), d(5));
        t.addView(caps(name, 10, regular, MUTED), new LayoutParams(-2, -2));
        View bar = new View(activity);
        LayoutParams bp = new LayoutParams(d(26), d(2));
        bp.topMargin = d(7);
        t.addView(bar, bp);
        styleTab(t, active);
        t.setTag("tab:" + name);
        focusable(t, false, action, null);
        return t;
    }
    private void styleTab(LinearLayout t, boolean active) {
        TextView label = (TextView) t.getChildAt(0);
        label.setTypeface(active ? medium : regular);
        label.setTextColor(active ? INK : MUTED);
        t.getChildAt(1).setBackgroundColor(active ? ORANGE : 0);
    }

    // ---- Home -----------------------------------------------------------------------------------------

    private void updateGreeting() {
        if (greeting != null) greeting.setText(QuietMachineModel.greeting(LocalTime.now().getHour()) + ",\n"
                + (config.greetingName.isEmpty() ? "Davy" : config.greetingName));
    }
    private void homeContent() {
        LinearLayout header = row();
        greeting = text("", 24, light, INK);
        greeting.setLineSpacing(d(4), 1f);
        updateGreeting();
        header.addView(greeting, new LayoutParams(0, -2, 1.3f));
        View rule = hairline(true, 76);
        ((LayoutParams) rule.getLayoutParams()).setMarginEnd(d(22));
        header.addView(rule);
        LinearLayout time = column();
        TextClock date = new TextClock(activity);
        date.setFormat24Hour("EEE d MMM");
        date.setFormat12Hour("EEE d MMM");
        date.setAllCaps(true);
        date.setLetterSpacing(.16f);
        date.setTypeface(medium);
        date.setTextSize(10.5f);
        date.setTextColor(INK);
        TextClock clock = new TextClock(activity);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTypeface(variable);
        clock.setFontVariationSettings("'wght' 620");
        clock.setTextSize(50);
        clock.setTextColor(INK);
        clock.setIncludeFontPadding(false);
        clock.setFontFeatureSettings("tnum");
        time.addView(date);
        space(time, 8);
        time.addView(clock);
        header.addView(time, new LayoutParams(0, -2, 1f));
        FrameLayout photograph = new FrameLayout(activity);
        photograph.setTag("photograph");
        photograph.setId(View.generateViewId());
        focusable(photograph, true, () -> {
            prefs.edit().putInt("quiet_picture", Math.floorMod(prefs.getInt("quiet_picture", 0) + 1, 3)).apply();
            loadPhotograph(photograph);
        }, null);
        header.addView(photograph, new LayoutParams(0, d(150), 2.2f));
        LinearLayout status = column();
        status.setGravity(Gravity.END);
        statusDot = new View(activity);
        statusDot.setBackground(frame(GREEN, GREEN, 0, d(4)));
        statusDot.setContentDescription("Network connected");
        statusDot.setVisibility(online() ? VISIBLE : INVISIBLE);
        status.addView(statusDot, new LayoutParams(d(7), d(7)));
        LayoutParams sp = new LayoutParams(d(22), d(150));
        header.addView(status, sp);
        body.addView(header, new LayoutParams(-1, d(160)));
        space(body, 32);

        List<AppAdapter.Entry> apps = new ArrayList<>();
        for (AppAdapter.Entry app : adapter.quietEntries()) if (app.kind != AppAdapter.Entry.EMPTY) apps.add(app);
        boolean fit = apps.size() + 1 <= 5;
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(fit);
        scroll.setClipToPadding(false);
        LinearLayout tiles = row();
        tiles.setGravity(Gravity.TOP);
        View watch = tile(glyphTile(), "Watch", true, inputs, null);
        watch.setTag("watch");
        watch.setContentDescription("Watch: live TV and HDMI inputs");
        inputAnchor = watch;
        defaultFocus = watch;
        tiles.addView(watch, tileParams(fit, false));
        for (int i = 0; i < apps.size(); i++) {
            AppAdapter.Entry app = apps.get(i);
            View t = tile(appArt(app), app.label, false, () -> adapter.quietLaunch(app), app);
            t.setTag(app.component.flattenToString());
            tiles.addView(t, tileParams(fit, i == apps.size() - 1));
        }
        // Up from any tile goes to the panorama, never sideways into the rail; Down comes back to WATCH.
        for (int i = 0; i < tiles.getChildCount(); i++) tiles.getChildAt(i).setNextFocusUpId(photograph.getId());
        watch.setId(View.generateViewId());
        photograph.setNextFocusDownId(watch.getId());
        scroll.addView(tiles);
        body.addView(scroll, new LayoutParams(-1, d(178)));
        body.addView(flex());
        LinearLayout foot = row();
        foot.addView(caps("OK  open     ·     Menu  app info     ·     Back  home", 9, regular, MUTED));
        foot.addView(flex());
        credit = caps("", 9, regular, MUTED);
        foot.addView(credit);
        body.addView(foot);
        loadPhotograph(photograph);
    }
    private LayoutParams tileParams(boolean fit, boolean last) {
        LayoutParams p = fit ? new LayoutParams(0, -1, 1) : new LayoutParams(d(138), -1);
        if (!last) p.setMarginEnd(d(10));
        return p;
    }
    private View glyphTile() { return glyph(Glyph.PLAY, INK, false, 30); }
    private View appArt(AppAdapter.Entry app) {
        ImageView v = new ImageView(activity);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setColorFilter(mono());
        Drawable image = app.banner != null ? app.banner : app.icon;
        v.setImageDrawable(image);
        v.setLayoutParams(app.banner != null ? new LayoutParams(d(100), d(46)) : new LayoutParams(d(42), d(42)));
        return v;
    }
    private View tile(View art, String label, boolean caps, Runnable action, AppAdapter.Entry app) {
        LinearLayout t = column();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(d(10), d(34), d(10), d(12));
        LinearLayout artBox = column();
        artBox.setGravity(Gravity.CENTER);
        artBox.addView(art);
        t.addView(artBox, new LayoutParams(-1, d(52)));
        space(t, 14);
        TextView name = caps ? caps(label, 11.5f, medium, INK) : text(label, 13.5f, regular, INK);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(name, new LayoutParams(-1, -2));
        View bar = new View(activity);
        bar.setBackgroundColor(ORANGE);
        bar.setVisibility(INVISIBLE);
        LayoutParams bp = new LayoutParams(d(58), d(3));
        bp.topMargin = d(12);
        t.addView(bar, bp);
        focusable(t, true, action, focus -> bar.setVisibility(focus ? VISIBLE : INVISIBLE));
        if (app != null && app.kind == AppAdapter.Entry.APP) t.setOnKeyListener((v, key, e) -> appInfoKey(key, e, app.pkg));
        return t;
    }
    private boolean appInfoKey(int key, KeyEvent e, String pkg) {
        if (key != KeyEvent.KEYCODE_MENU) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) {
            Intent info = SystemSettings.appInfo(activity, pkg);
            if (info != null) start(info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else Toast.makeText(activity, "App info is not available on this TV", Toast.LENGTH_SHORT).show();
        }
        return true;
    }
    private void loadPhotograph(FrameLayout frame) {
        final int ticket = ++photoGeneration;
        frame.removeAllViews();
        int selected = Math.floorMod(prefs.getInt("quiet_picture", 0), PhotoDream.PHOTOS.length);
        File dir = Config.dir(activity);
        File chosen = dir == null ? null : new File(new File(dir, "wallpapers"), PhotoDream.PHOTOS[selected] + ".jpg");
        File fallback = dir == null ? null : new File(dir, "wallpaper.jpg");
        boolean pack = chosen != null && chosen.isFile();
        File file = pack ? chosen : fallback != null && fallback.isFile() ? fallback : null;
        if (credit != null) credit.setText(pack ? PICTURE_CREDITS[selected].toUpperCase(Locale.ROOT) : "");
        LinearLayout empty = column();
        empty.setGravity(Gravity.CENTER);
        empty.setBackgroundColor(INSET);
        TextView missing = caps("No photograph installed", 9.5f, medium, MUTED);
        missing.setGravity(Gravity.CENTER);
        empty.addView(missing);
        space(empty, 6);
        TextView hint = text("Los Angeles · USC · Florence pack: see docs/quiet-machine.md", 11, regular, MUTED);
        hint.setGravity(Gravity.CENTER);
        empty.addView(hint);
        FrameLayout.LayoutParams inset = new FrameLayout.LayoutParams(-1, -1);
        inset.setMargins(d(3), d(3), d(3), d(3));
        if (file == null) {
            frame.addView(empty, inset);
            frame.setContentDescription("No photograph installed");
            return;
        }
        frame.setContentDescription((pack ? PICTURE_CREDITS[selected] : "Theme wallpaper") + ". OK shows the next photograph.");
        loader.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            options.inSampleSize = QuietMachineModel.sampleSize(options.outWidth, options.outHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            post(() -> {
                if (disposed || ticket != photoGeneration) return;
                if (bitmap == null) { frame.addView(empty, inset); return; }
                ImageView image = new ImageView(activity);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setColorFilter(mono());
                image.setImageBitmap(bitmap);
                frame.addView(image, inset);
            });
        });
    }

    // ---- Library and Search ---------------------------------------------------------------------------

    private void libraryContent(boolean search) {
        LinearLayout header = row();
        for (String name : TABS) {
            LinearLayout t = tab(name, name.equals(category), () -> selectCategory(name));
            tabViews.put(name, t);
            header.addView(t);
            if (name.equals(category)) defaultFocus = t;
        }
        header.addView(flex());
        header.addView(hairline(true, 16));
        TextView sortLabel = caps("Sort", 9.5f, regular, MUTED);
        sortLabel.setPadding(d(14), 0, d(4), 0);
        header.addView(sortLabel);
        header.addView(hairline(true, 16));
        TextView sort = caps(sortName(), 9.5f, medium, INK);
        sort.setPadding(d(14), d(10), d(10), d(10));
        sort.setTag("sort");
        focusable(sort, false, () -> {
            prefs.edit().putBoolean("quiet_recent", !prefs.getBoolean("quiet_recent", true)).apply();
            sort.setText(sortName().toUpperCase(Locale.ROOT));
            fillCatalog();
        }, null);
        header.addView(sort);
        body.addView(header);
        body.addView(hairline(false, 1));
        space(body, 14);
        if (search) {
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setTextColor(INK);
            input.setHintTextColor(MUTED);
            input.setTypeface(regular);
            input.setTextSize(15);
            input.setHint("Search installed apps by name");
            input.setText(query);
            input.setSelection(query.length());
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setPadding(d(14), 0, d(14), 0);
            input.setBackground(resting());
            input.setOnFocusChangeListener((v, f) -> v.setBackground(f ? focusRing() : resting()));
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int st, int before, int count) { query = s.toString(); fillCatalog(); }
                @Override public void afterTextChanged(Editable e) { }
            });
            body.addView(input, new LayoutParams(-1, d(46)));
            space(body, 14);
            defaultFocus = input;
        }
        catalogMessage = text("", 15, regular, MUTED);
        catalogMessage.setPadding(d(4), d(6), 0, d(14));
        body.addView(catalogMessage);
        results = new RecyclerView(activity);
        results.setLayoutManager(new GridLayoutManager(activity, 3));
        results.setItemAnimator(null);
        results.setClipToPadding(false);
        results.setAdapter(new CatalogAdapter());
        body.addView(results, new LayoutParams(-1, 0, 1));
        fillCatalog();
        if (!loaded) loadCatalog();
    }
    private String sortName() { return prefs.getBoolean("quiet_recent", true) ? "Recent" : "A–Z"; }
    private void selectCategory(String name) {
        category = name;
        for (Map.Entry<String, LinearLayout> t : tabViews.entrySet()) styleTab(t.getValue(), t.getKey().equals(name));
        fillCatalog();
    }
    private void loadCatalog() {
        final int ticket = ++generation;
        loader.execute(() -> {
            Set<String> excluded = new HashSet<>(config.libraryExcludedApps);
            LinkedHashMap<String, AppAdapter.Entry> found = new LinkedHashMap<>();
            PackageManager pm = activity.getPackageManager();
            for (String kind : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(kind), 0)) {
                    if (info.activityInfo == null || !info.activityInfo.exported) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(activity.getPackageName()) || found.containsKey(pkg)
                            || LibraryApps.excludedFromCatalog(excluded, pkg)) continue;
                    try {
                        found.put(pkg, new AppAdapter.Entry(info.loadLabel(pm).toString(),
                                new ComponentName(pkg, info.activityInfo.name), null, null));
                    } catch (RuntimeException ignored) {
                        // Removed during discovery.
                    }
                }
            }
            ArrayList<AppAdapter.Entry> apps = new ArrayList<>(found.values());
            post(() -> {
                if (disposed || ticket != generation) return;
                catalog.clear();
                catalog.addAll(apps);
                loaded = true;
                fillCatalog();
            });
        });
    }
    private void fillCatalog() {
        if (results == null) return;
        filtered.clear();
        if (QuietMachineModel.catalogAvailable(category)) {
            for (AppAdapter.Entry app : catalog) {
                if (QuietMachineModel.matches(app.label, app.pkg, query, category)) filtered.add(app);
            }
            Map<String, Long> opened = new HashMap<>();
            for (AppAdapter.Entry app : filtered) opened.put(app.pkg, LastOpened.get(activity, app.pkg));
            filtered.sort(QuietMachineModel.order(prefs.getBoolean("quiet_recent", true),
                    app -> opened.get(app.pkg), app -> app.label));
        }
        String message = !QuietMachineModel.catalogAvailable(category)
                ? category + ": no catalog is connected to this launcher. All lists the installed apps."
                : filtered.isEmpty() ? (loaded ? "No installed app matches" : "Reading installed apps…")
                : "Music".equals(category) ? "Music apps installed on this TV" : "";
        catalogMessage.setText(message);
        catalogMessage.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        RecyclerView.Adapter<?> a = results.getAdapter();
        if (a != null) a.notifyDataSetChanged();
    }
    private static final class CatalogHolder extends RecyclerView.ViewHolder {
        final ImageView picture;
        final TextView title, source;
        CatalogHolder(View card, ImageView picture, TextView title, TextView source) {
            super(card);
            this.picture = picture; this.title = title; this.source = source;
        }
    }
    private final class CatalogAdapter extends RecyclerView.Adapter<CatalogHolder> {
        CatalogAdapter() { setHasStableIds(true); }
        @Override public long getItemId(int position) { return filtered.get(position).component.flattenToString().hashCode(); }
        @Override public int getItemCount() { return filtered.size(); }
        @Override public CatalogHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout card = row();
            card.setPadding(d(6), d(6), d(6), d(6));
            FrameLayout thumb = new FrameLayout(activity);
            thumb.setBackgroundColor(INSET);
            ImageView picture = new ImageView(activity);
            picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
            picture.setColorFilter(mono());
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -1);
            pp.setMargins(d(8), d(6), d(8), d(6));
            thumb.addView(picture, pp);
            card.addView(thumb, new LayoutParams(d(92), d(52)));
            LinearLayout labels = column();
            labels.setPadding(d(12), 0, d(4), 0);
            TextView title = text("", 13.5f, medium, INK);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title);
            TextView source = text("", 10, regular, MUTED);
            source.setSingleLine();
            source.setEllipsize(TextUtils.TruncateAt.END);
            source.setPadding(0, d(5), 0, 0);
            labels.addView(source);
            card.addView(labels, new LayoutParams(0, -2, 1));
            TextView more = text("⋮", 16, regular, MUTED);
            more.setContentDescription("Menu: app info");
            card.addView(more);
            RecyclerView.LayoutParams size = new RecyclerView.LayoutParams(-1, d(66));
            size.setMargins(0, 0, d(10), d(10));
            card.setLayoutParams(size);
            return new CatalogHolder(card, picture, title, source);
        }
        @Override public void onBindViewHolder(CatalogHolder holder, int position) {
            AppAdapter.Entry app = filtered.get(position);
            holder.title.setText(app.label);
            long when = LastOpened.get(activity, app.pkg);
            holder.source.setText(when > 0 ? "Opened " + LastOpened.describe(activity, when) : "Installed app");
            holder.itemView.setTag(app.component.flattenToString());
            focusable(holder.itemView, true, () -> adapter.quietLaunch(app), null);
            holder.itemView.setBackground(holder.itemView.hasFocus() ? focusRing() : resting());
            holder.itemView.setOnKeyListener((v, key, e) -> {
                // The right edge of the grid stays put instead of jumping up to SORT.
                int at = holder.getBindingAdapterPosition();
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT && (at % 3 == 2 || at == filtered.size() - 1)) return true;
                return appInfoKey(key, e, app.pkg);
            });
            Drawable ready = art.get(app.pkg);
            holder.picture.setImageDrawable(ready);
            if (ready != null) return;
            final int ticket = generation;
            loader.execute(() -> {
                Drawable loaded = render(app.component);
                if (loaded == null) return;
                post(() -> {
                    if (disposed || ticket != generation) return;
                    art.put(app.pkg, loaded);
                    if (app.component.flattenToString().equals(holder.itemView.getTag())) holder.picture.setImageDrawable(loaded);
                });
            });
        }
    }
    /** The app's TV banner where it has one, else its icon, drawn once into a small bitmap. */
    private Drawable render(ComponentName component) {
        PackageManager pm = activity.getPackageManager();
        try {
            Drawable source = pm.getActivityBanner(component);
            if (source == null) source = pm.getApplicationBanner(component.getPackageName());
            boolean banner = source != null;
            if (!banner) source = pm.getActivityIcon(component);
            int w = banner ? 160 : 72, h = banner ? 90 : 72;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            source.setBounds(0, 0, w, h);
            source.draw(new Canvas(bitmap));
            return new BitmapDrawable(getResources(), bitmap);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return null;
        }
    }

    // ---- Control --------------------------------------------------------------------------------------

    private void start(Intent intent) {
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, "Not available on this TV", Toast.LENGTH_LONG).show();
        }
    }
    private void settings(String action) {
        try { activity.startActivity(SystemSettings.intent(activity, action)); }
        catch (ActivityNotFoundException | SecurityException e) {
            try { activity.startActivity(SystemSettings.intent(activity, Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(activity, "System settings are not available on this TV", Toast.LENGTH_LONG).show();
            }
        }
    }
    private boolean online() {
        try {
            ConnectivityManager cm = activity.getSystemService(ConnectivityManager.class);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException e) {
            return false;
        }
    }
    private LinearLayout card(LinearLayout parent, String title, int glyphType, boolean last) {
        LinearLayout card = column();
        card.setPadding(d(16), d(16), d(16), d(14));
        card.setBackground(resting());
        LinearLayout top = row();
        top.setGravity(Gravity.TOP);
        top.addView(caps(title, 9.5f, medium, INK), new LayoutParams(0, -2, 1));
        top.addView(glyph(glyphType, INK, false, 18));
        card.addView(top);
        space(card, 6);
        LayoutParams p = new LayoutParams(0, -1, 1);
        if (!last) p.setMarginEnd(d(10));
        parent.addView(card, p);
        return card;
    }
    private LinearLayout scale(String left, String right) {
        LinearLayout s = row();
        s.addView(text(left, 9.5f, regular, MUTED));
        s.addView(flex());
        s.addView(text(right, 9.5f, regular, MUTED));
        return s;
    }
    private void controlContent() {
        LinearLayout header = row();
        String[] names = {"Control", "Picture", "Sound", "Network", "System"};
        String[] actions = {null, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS,
                Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS};
        for (int i = 0; i < names.length; i++) {
            String action = actions[i];
            LinearLayout t = tab(names[i], action == null, () -> { if (action == null) requestDefault(); else settings(action); });
            if (action != null) t.setContentDescription(names[i] + " settings, opens system settings");
            header.addView(t);
        }
        header.addView(flex());
        header.addView(hairline(true, 16));
        String name = null;
        try { name = Settings.Global.getString(activity.getContentResolver(), "device_name"); }
        catch (RuntimeException ignored) { /* vendor settings provider */ }
        TextView device = text(name == null || name.isEmpty() ? Build.MODEL : name, 10.5f, regular, INK);
        device.setPadding(d(14), 0, d(10), 0);
        header.addView(device);
        statusDot = new View(activity);
        statusDot.setBackground(frame(GREEN, GREEN, 0, d(4)));
        statusDot.setContentDescription("Network connected");
        statusDot.setVisibility(online() ? VISIBLE : INVISIBLE);
        header.addView(statusDot, new LayoutParams(d(7), d(7)));
        body.addView(header);
        body.addView(hairline(false, 1));
        space(body, 18);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        scroll.addView(content);
        body.addView(scroll, new LayoutParams(-1, 0, 1));
        LinearLayout cards = row();
        cards.setGravity(Gravity.TOP);
        content.addView(cards, new LayoutParams(-1, d(236)));

        LinearLayout sound = card(cards, "Sound", Glyph.SOUND, false);
        AudioManager audio = activity.getSystemService(AudioManager.class);
        int max = volumeMax(audio);
        if (max > 0) {
            volumeValue = text("", 30, regular, INK);
            sound.addView(volumeValue);
            sound.addView(flex());
            volume = new Meter(true, max, this::setVolume, () -> settings(Settings.ACTION_SOUND_SETTINGS));
            volume.setTag("volume");
            volume.setContentDescription("Media volume. Left and right adjust, OK opens sound settings.");
            sound.addView(volume, new LayoutParams(-1, d(30)));
            sound.addView(scale("0", Integer.toString(max)));
            volumeNote = text("Media volume  ·  OK for settings", 9.5f, regular, MUTED);
            volumeNote.setPadding(0, d(6), 0, 0);
            sound.addView(volumeNote);
            defaultFocus = volume;
            syncVolume();
        } else {
            sound.addView(text("Fixed", 26, regular, INK));
            sound.addView(flex());
            TextView why = text("This TV sets the volume itself", 11, regular, MUTED);
            sound.addView(why);
            space(sound, 8);
            sound.addView(quietButton("Sound settings ↗", () -> settings(Settings.ACTION_SOUND_SETTINGS)));
        }

        LinearLayout ambient = card(cards, "Ambient light", Glyph.LIGHT, false);
        ambient.addView(text("Not connected", 20, regular, MUTED));
        ambient.addView(flex());
        ambient.addView(new Meter(false, 100, null, null), new LayoutParams(-1, d(30)));
        ambient.addView(scale("—", "—"));
        TextView none = text("No lighting integration", 9.5f, regular, MUTED);
        none.setPadding(0, d(6), 0, 0);
        ambient.addView(none);

        LinearLayout focus = card(cards, "Focus", Glyph.MOON, false);
        String mode = prefs.getString("quiet_focus", "Off");
        focusValue = text(mode, 30, regular, INK);
        focus.addView(focusValue);
        focus.addView(flex());
        LinearLayout segments = row();
        List<TextView> buttons = new ArrayList<>();
        for (String m : MODES) {
            TextView b = text(m, 9.5f, medium, INK);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, 0, 0, 0);
            b.setSingleLine();
            b.setTag("mode:" + m);
            b.setFocusable(true);
            b.setClickable(true);
            buttons.add(b);
            LayoutParams p = new LayoutParams(0, d(32), 1);
            if (!m.equals("Night")) p.setMarginEnd(d(2));
            segments.addView(b, p);
        }
        for (TextView b : buttons) {
            String m = ((String) b.getTag()).substring(5);
            b.setOnClickListener(v -> {
                prefs.edit().putString("quiet_focus", m).apply();
                applyFocusMode();
                focusValue.setText(m);
                styleSegments(buttons);
            });
            b.setOnFocusChangeListener((v, f) -> styleSegments(buttons));
        }
        styleSegments(buttons);
        focus.addView(segments);
        TextView only = text("Dims this launcher only", 9.5f, regular, MUTED);
        only.setPadding(0, d(8), 0, 0);
        focus.addView(only);

        LinearLayout display = card(cards, "Display", Glyph.DISPLAY, true);
        Display screenDisplay = activity.getWindowManager().getDefaultDisplay();
        Display.Mode outputMode = screenDisplay == null ? null : screenDisplay.getMode();
        int w = outputMode == null ? 0 : outputMode.getPhysicalWidth(), h = outputMode == null ? 0 : outputMode.getPhysicalHeight();
        display.addView(text(QuietMachineModel.resolution(w, h), 30, regular, INK));
        space(display, 4);
        if (outputMode != null) {
            display.addView(text(w + " × " + h + "  ·  " + Math.round(outputMode.getRefreshRate()) + " Hz", 10.5f, regular, MUTED));
        }
        display.addView(flex());
        display.addView(quietButton("Settings ↗", () -> settings(Settings.ACTION_DISPLAY_SETTINGS)));
        TextView calibration = text("Picture mode stays in TV settings", 9.5f, regular, MUTED);
        calibration.setPadding(0, d(8), 0, 0);
        display.addView(calibration);

        space(content, 18);
        LinearLayout footer = row();
        TextView prefsButton = quietButton("Launcher preferences", () -> start(new Intent(activity, LauncherSettingsActivity.class)));
        footer.addView(prefsButton);
        TextView hdmi = quietButton("HDMI / inputs", inputs);
        inputAnchor = hdmi;
        LayoutParams gap = new LayoutParams(-2, -2);
        gap.setMarginStart(d(10));
        footer.addView(hdmi, gap);
        footer.addView(quietButton("Classic launcher", classic), new LayoutParams(gap));
        TextView appearance = quietButton(prefs.getBoolean("quiet_dark", true) ? "Light mode" : "Dark mode",
                this::toggleAppearance);
        appearance.setTag("appearance");
        footer.addView(appearance, new LayoutParams(gap));
        content.addView(footer);
        TextView note = text("Picture, Sound, Network and System open the TV's own settings", 9.5f, regular, MUTED);
        note.setPadding(0, d(14), 0, 0);
        content.addView(note);
        if (defaultFocus == null) defaultFocus = buttons.get(0);
    }
    private void styleSegments(List<TextView> buttons) {
        String mode = prefs.getString("quiet_focus", "Off");
        for (TextView b : buttons) {
            boolean selected = b.getTag().equals("mode:" + mode), focused = b.isFocused();
            b.setBackground(selected ? frame(ORANGE, focused ? INK : ORANGE, d(focused ? 2 : 1), d(2))
                    : focused ? focusRing() : resting());
            b.setTextColor(selected ? ON_ORANGE : INK);
        }
    }
    private int volumeMax(AudioManager audio) {
        try {
            return audio == null || audio.isVolumeFixed() ? 0 : audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }
    private void syncVolume() {
        AudioManager audio = activity.getSystemService(AudioManager.class);
        if (audio == null || volume == null || volumeValue == null) return;
        try {
            int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            volume.set(value);
            volumeValue.setText(Integer.toString(value));
        } catch (RuntimeException unavailable) {
            volumeValue.setText("—");
        }
    }
    /**
     * One step toward target, as the remote's volume keys send (Fire OS ignores setStreamVolume on its TV output),
     * then a read-back: a TV or audio device that keeps its own level is reported instead of a level it never took.
     */
    private void setVolume(int target) {
        AudioManager audio = activity.getSystemService(AudioManager.class);
        if (audio == null || volumeNote == null) return;
        int before, after;
        try {
            before = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (target != before) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    target > before ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            after = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (RuntimeException refused) {
            volumeNote.setText("Volume is controlled by the system");
            syncVolume();
            return;
        }
        volumeNote.setText(after != before || target == before ? "Media volume  ·  OK for settings"
                : "Held by the TV or a connected audio device");
        syncVolume();
    }
    private void applyFocusMode() {
        String mode = prefs.getString("quiet_focus", "Off");
        WindowManager.LayoutParams p = activity.getWindow().getAttributes();
        p.screenBrightness = QuietMachineModel.brightness(mode);
        activity.getWindow().setAttributes(p);
        // Visible even where the firmware ignores the window brightness.
        setForeground(new ColorDrawable(QuietMachineModel.dim(mode)));
    }

    // ---- small drawn parts ----------------------------------------------------------------------------

    /** A level bar for the D-pad: left and right step it, OK runs the action. Not live: a hairline, unfocusable. */
    private final class Meter extends View {
        private final boolean live;
        private final int max;
        private final IntConsumer change;
        private final Runnable ok;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int value;

        Meter(boolean live, int max, IntConsumer change, Runnable ok) {
            super(activity);
            this.live = live; this.max = Math.max(1, max); this.change = change; this.ok = ok;
            setFocusable(live);
            if (live) setOnFocusChangeListener((v, f) -> { setBackground(f ? focusRing() : null); invalidate(); });
        }
        void set(int v) { value = v; invalidate(); }
        @Override public boolean onKeyDown(int key, KeyEvent event) {
            if (!live) return super.onKeyDown(key, event);
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int next = QuietMachineModel.step(value, key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, max);
                if (next != value) { value = next; invalidate(); change.accept(next); }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) return true;
            return super.onKeyDown(key, event);
        }
        @Override public boolean onKeyUp(int key, KeyEvent event) {
            if (live && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) { ok.run(); return true; }
            return super.onKeyUp(key, event);
        }
        @Override protected void onDraw(Canvas c) {
            float l = d(8), r = getWidth() - d(8), y = getHeight() / 2f;
            if (!live) {
                paint.setColor(LINE);
                c.drawRect(l, y - d(1) / 2f, r, y + d(1) / 2f, paint);
                return;
            }
            float h = d(6), x = l + (r - l) * value / max;
            paint.setColor(TRACK);
            c.drawRect(x, y - h / 2, r, y + h / 2, paint);
            paint.setColor(ORANGE);
            c.drawRect(l, y - h / 2, x, y + h / 2, paint);
            paint.setColor(INK);
            c.drawRect(x - d(1), y - d(8), x + d(1), y + d(8), paint);
        }
    }

    /** Line icons drawn on a 24-unit grid: no image assets, so nothing added to the APK but code. */
    static final class Glyph extends Drawable {
        static final int HOME = 0, LIBRARY = 1, SEARCH = 2, SETTINGS = 3, PLAY = 4, SOUND = 5, LIGHT = 6, MOON = 7,
                DISPLAY = 8;
        private final int type;
        private final boolean solid;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int type, int color, boolean solid) {
            this.type = type;
            this.solid = solid;
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(1.6f);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save();
            c.translate(b.left + (b.width() - 24 * s) / 2, b.top + (b.height() - 24 * s) / 2);
            c.scale(s, s);
            Path p = new Path();
            switch (type) {
                case HOME:
                    p.moveTo(4, 11); p.lineTo(12, 4); p.lineTo(20, 11); p.lineTo(20, 20); p.lineTo(4, 20); p.close();
                    break;
                case LIBRARY:
                    p.addRect(5, 4, 19, 20, Path.Direction.CW); p.moveTo(9, 4); p.lineTo(9, 20);
                    break;
                case SEARCH:
                    p.addCircle(10.5f, 10.5f, 5.5f, Path.Direction.CW); p.moveTo(14.6f, 14.6f); p.lineTo(20, 20);
                    break;
                case SETTINGS:
                    p.addCircle(12, 12, 2.8f, Path.Direction.CW);
                    p.addCircle(12, 12, 6.2f, Path.Direction.CW);
                    rays(p, 6.2f, 9f);
                    p.setFillType(Path.FillType.EVEN_ODD);
                    break;
                case PLAY:
                    p.moveTo(7, 4.5f); p.lineTo(19, 12); p.lineTo(7, 19.5f); p.close();
                    break;
                case SOUND:
                    p.moveTo(3.5f, 9.5f); p.lineTo(7.5f, 9.5f); p.lineTo(12, 5.5f); p.lineTo(12, 18.5f);
                    p.lineTo(7.5f, 14.5f); p.lineTo(3.5f, 14.5f); p.close();
                    p.addArc(new RectF(11, 8, 19, 16), -55, 110);
                    break;
                case LIGHT:
                    p.addCircle(12, 12, 4, Path.Direction.CW);
                    rays(p, 6.5f, 9f);
                    break;
                case MOON:
                    p.addCircle(12, 12, 7.5f, Path.Direction.CW);
                    Path cut = new Path();
                    cut.addCircle(16.5f, 8.5f, 6.5f, Path.Direction.CW);
                    p.op(cut, Path.Op.DIFFERENCE);
                    break;
                default:
                    p.addRect(3, 5, 21, 16, Path.Direction.CW);
                    p.moveTo(12, 16); p.lineTo(12, 19.5f); p.moveTo(8, 19.5f); p.lineTo(16, 19.5f);
                    break;
            }
            paint.setStyle(solid ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
            c.drawPath(p, paint);
            c.restore();
        }
        private static void rays(Path p, float from, float to) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                p.moveTo(12 + from * cos, 12 + from * sin);
                p.lineTo(12 + to * cos, 12 + to * sin);
            }
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

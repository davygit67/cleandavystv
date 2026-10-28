package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline, native TV surface. Existing adapter remains the authority for configured launch actions. */
final class QuietMachineView extends LinearLayout {
    static final int PAPER = 0xfff2f0e9, INK = 0xff222220, LINE = 0xffc9c7be,
            ORANGE = 0xfff36b21, MUTED = 0xff68675f;
    private final Activity activity;
    private final AppAdapter adapter;
    private final Runnable inputs, classic;
    private final Config config;
    private final LinearLayout body, rail;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ArrayList<AppAdapter.Entry> catalog = new ArrayList<>();
    private String screen = "Home", category = "All", query = "";
    private TextView greeting;
    private SeekBar volumeSlider;
    private TextView volumeLevel;
    private RecyclerView results;
    private TextView catalogMessage;
    private final ArrayList<AppAdapter.Entry> filtered = new ArrayList<>();
    private final android.util.LruCache<String, android.graphics.drawable.Drawable> icons = new android.util.LruCache<>(48);
    private View inputAnchor;
    private int photoGeneration;
    private boolean loaded, disposed;
    private int generation;
    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateGreeting(); syncVolume(); }
    };

    QuietMachineView(Activity a, AppAdapter apps, Runnable showInputs, Runnable showClassic) {
        super(a); activity = a; adapter = apps; inputs = showInputs; classic = showClassic;
        config = Config.load(a);
        setOrientation(HORIZONTAL); setBackgroundColor(PAPER); setPadding(d(28), d(28), d(28), d(28));
        rail = column(); rail.setPadding(0, d(28), d(14), 0);
        addView(rail, new LayoutParams(d(112), -1));
        View divider = new View(a); divider.setBackgroundColor(LINE); addView(divider, new LayoutParams(d(1), -1));
        body = column(); body.setPadding(d(22), d(12), 0, 0); addView(body, new LayoutParams(0, -1, 1));
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        if (android.os.Build.VERSION.SDK_INT >= 33) a.registerReceiver(clock, filter, Context.RECEIVER_EXPORTED);
        else a.registerReceiver(clock, filter);
        applyFocusMode(); show("Home");
    }
    private int d(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(activity); v.setOrientation(HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size) {
        TextView v = new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(INK);
        v.setFontFeatureSettings("tnum"); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private GradientDrawable border(boolean focused) {
        GradientDrawable b = new GradientDrawable(); b.setColor(focused ? 0xffe8e5dc : PAPER);
        b.setCornerRadius(d(2)); b.setStroke(d(focused ? 2 : 1), focused ? ORANGE : LINE); return b;
    }
    private void selectable(View v, Runnable action) {
        v.setFocusable(true); v.setClickable(true); v.setBackground(border(false));
        v.setOnClickListener(w -> action.run());
        v.setOnFocusChangeListener((w, focus) -> w.setBackground(border(focus)));
    }
    private TextView button(String label, Runnable action) {
        TextView v = text(label, 16); v.setPadding(d(12), d(12), d(12), d(12));
        selectable(v, action); return v;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(activity), new LayoutParams(1, d(height))); }
    void home() { show("Home"); }
    void back() { if (!screen.equals("Home")) home(); else focusDefault(); }
    void focusDefault() { if (!hasFocus() && body.getChildCount() > 0) body.requestFocus(View.FOCUS_DOWN); }
    void refresh() {
        loaded = false; icons.evictAll(); generation++;
        if (screen.equals("Home")) {
            Object key = findFocus() == null ? null : findFocus().getTag();
            show("Home"); restoreFocus(key);
        } else if (screen.equals("Library") || screen.equals("Search")) loadCatalog();
    }
    void resume() {
        updateGreeting();
        syncVolume();
    }
    private void syncVolume() {
        AudioManager audio = activity.getSystemService(AudioManager.class);
        if (audio != null && volumeSlider != null && volumeLevel != null) {
            try {
                int value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                volumeSlider.setProgress(value); volumeLevel.setText(Integer.toString(value));
            } catch (RuntimeException unavailable) {
                volumeSlider.setEnabled(false); volumeLevel.setText("System controlled");
            }
        }
    }
    void dispose() { disposed = true; generation++; loader.shutdownNow(); activity.unregisterReceiver(clock); }

    private void show(String next) {
        photoGeneration++;
        screen = next; greeting = null; volumeSlider = null; volumeLevel = null; body.removeAllViews(); rail.removeAllViews(); results = null; inputAnchor = null;
        for (String item : new String[]{"Home", "Library", "Search", "Settings"}) {
            TextView b = button((item.equals(screen) ? "•  " : "   ") + item, () -> show(item));
            b.setTextSize(14); b.setTypeface(null, item.equals(screen) ? Typeface.BOLD : Typeface.NORMAL);
            b.setTag("rail:" + item);
            if (item.equals(screen)) b.setTextColor(ORANGE);
            rail.addView(b, new LayoutParams(-1, d(56))); space(rail, 8);
        }
        if (next.equals("Home")) homeContent();
        else if (next.equals("Settings")) controlContent();
        else libraryContent(next.equals("Search"));
        body.post(this::focusDefault);
    }
    private void restoreFocus(Object key) {
        if (key == null) return;
        View target = findViewWithTag(key);
        if (target != null) target.requestFocus();
    }
    View inputAnchor() { return inputAnchor == null ? this : inputAnchor; }

    @Override public View focusSearch(View focused, int direction) {
        View candidate = super.focusSearch(focused, direction);
        // No edge may fall through to the Classic surface or escape this screen.
        for (android.view.ViewParent p = candidate == null ? null : candidate.getParent(); p != null; p = p.getParent()) {
            if (p == this) return candidate;
        }
        return focused;
    }
    private void updateGreeting() {
        if (greeting != null) greeting.setText(QuietMachineModel.greeting(LocalTime.now().getHour()) + ",\n"
                + (config.greetingName.isEmpty() ? "Davy" : config.greetingName));
    }
    private void homeContent() {
        LinearLayout header = row();
        greeting = text("", 27); updateGreeting(); header.addView(greeting, new LayoutParams(0, d(126), 1));
        LinearLayout time = column(); time.setPadding(d(20), 0, d(12), 0);
        TextClock date = new TextClock(activity); date.setFormat24Hour("EEE d MMM"); date.setFormat12Hour("EEE d MMM"); date.setTextColor(MUTED); date.setTextSize(13);
        TextClock clockView = new TextClock(activity); clockView.setFormat24Hour("HH:mm"); clockView.setFormat12Hour("HH:mm"); clockView.setTextColor(INK); clockView.setTextSize(46); clockView.setTypeface(null, Typeface.BOLD);
        time.addView(date); time.addView(clockView); header.addView(time, new LayoutParams(0, -2, 1));
        FrameLayout photograph = new FrameLayout(activity);
        photograph.setTag("photograph");
        selectable(photograph, () -> {
            int current = activity.getPreferences(0).getInt("quiet_picture", 0);
            activity.getPreferences(0).edit().putInt("quiet_picture", Math.floorMod(current + 1, 3)).apply();
            loadPhotograph(photograph);
        });
        loadPhotograph(photograph);
        header.addView(photograph, new LayoutParams(0, d(126), 2)); body.addView(header); space(body, 30);
        TextView heading = text("02  /  QUIET MACHINE", 12); heading.setLetterSpacing(.2f); body.addView(heading); space(body, 16);
        HorizontalScrollView scroll = new HorizontalScrollView(activity); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tiles = row();
        ColorMatrix mono = new ColorMatrix(); mono.setSaturation(0);
        TextView watch = button("▷\nWATCH", inputs); watch.setGravity(Gravity.CENTER); watch.setTextSize(24);
        watch.setTag("watch"); inputAnchor = watch;
        LayoutParams tileSize = new LayoutParams(d(162), d(130)); tileSize.setMargins(0, 0, d(10), 0); tiles.addView(watch, tileSize);
        for (AppAdapter.Entry app : adapter.quietEntries()) {
            if (app.kind == AppAdapter.Entry.EMPTY) continue;
            LinearLayout tile = column(); tile.setGravity(Gravity.CENTER); tile.setPadding(d(12), d(12), d(12), d(12));
            if (app.icon != null) { ImageView icon = new ImageView(activity); icon.setImageDrawable(app.icon); icon.setColorFilter(new ColorMatrixColorFilter(mono)); tile.addView(icon, new LayoutParams(d(42), d(42))); }
            TextView label = text(app.label, 17); label.setGravity(Gravity.CENTER); label.setMaxLines(2); tile.addView(label, new LayoutParams(-1, 0, 1));
            tile.setTag(app.component.flattenToString());
            selectable(tile, () -> adapter.quietLaunch(app)); tiles.addView(tile, new LayoutParams(tileSize));
        }
        scroll.addView(tiles); body.addView(scroll); space(body, 24);
        body.addView(text("WATCH opens live TV and HDMI inputs   ·   Library contains installed apps", 13));
        watch.requestFocus();
    }
    private void loadPhotograph(FrameLayout frame) {
        final int ticket = ++photoGeneration;
        frame.removeAllViews();
        String[] pictures = {"la", "usc", "florence"};
        int selected = Math.floorMod(activity.getPreferences(0).getInt("quiet_picture", 0), pictures.length);
        String name = pictures[selected];
        File chosen = new File(new File(Config.dir(activity), "wallpapers"), name + ".jpg");
        File file = chosen.isFile() ? chosen : new File(Config.dir(activity), "wallpaper.jpg");
        TextView empty = text("PHOTOGRAPHS\nLos Angeles  /  USC  /  Florence\nNo photograph installed", 14);
        empty.setTextColor(MUTED); empty.setPadding(d(18), d(12), d(18), d(12));
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        frame.setContentDescription("No photograph installed. Press OK to choose the next photograph.");
        if (!file.isFile()) return;
        loader.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getPath(), options);
            options.inSampleSize = QuietMachineModel.sampleSize(options.outWidth, options.outHeight);
            options.inJustDecodeBounds = false; options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            post(() -> {
                if (disposed || ticket != photoGeneration) { if (bitmap != null) bitmap.recycle(); return; }
                if (bitmap == null) return;
                ImageView image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ColorMatrix mono = new ColorMatrix(); mono.setSaturation(0);
                image.setColorFilter(new ColorMatrixColorFilter(mono)); image.setImageBitmap(bitmap);
                frame.removeAllViews();
                FrameLayout.LayoutParams inset = new FrameLayout.LayoutParams(-1, -1);
                inset.setMargins(d(3), d(3), d(3), d(3)); frame.addView(image, inset);
                frame.setContentDescription((chosen.isFile() ? name : "Existing wallpaper") + ". Press OK for next photograph.");
            });
        });
    }

    private void libraryContent(boolean search) {
        LinearLayout tabs = row();
        for (String name : new String[]{"All", "Movies", "Series", "Documentaries", "Music"}) {
            TextView tab = button(name.equals(category) ? name + "  ━" : name, () -> { category = name; show(screen); restoreFocus("tab:" + name); });
            tab.setTag("tab:" + name); tab.setTextSize(13); tabs.addView(tab, new LayoutParams(0, d(48), 1));
        }
        body.addView(tabs); space(body, 12);
        if (search) {
            EditText input = new EditText(activity); input.setSingleLine(true); input.setTextColor(INK); input.setHintTextColor(MUTED);
            input.setHint("Search installed apps"); input.setText(query); body.addView(input, new LayoutParams(-1, d(48)));
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
                public void onTextChanged(CharSequence s, int st, int before, int count) { query = s.toString(); fillCatalog(); }
                public void afterTextChanged(Editable e) { }
            });
        }
        body.addView(text("INSTALLED APPS  /  No streaming catalog connected", 13)); space(body, 14);
        catalogMessage = text("", 18); body.addView(catalogMessage);
        results = new RecyclerView(activity);
        results.setLayoutManager(new GridLayoutManager(activity, 3));
        results.setItemAnimator(null); results.setAdapter(new CatalogAdapter());
        body.addView(results, new LayoutParams(-1, 0, 1));
        fillCatalog(); if (!loaded) loadCatalog();
    }
    private void loadCatalog() {
        final int ticket = ++generation;
        loader.execute(() -> {
            Set<String> excluded = new HashSet<>(config.libraryExcludedApps);
            LinkedHashMap<String, AppAdapter.Entry> found = new LinkedHashMap<>(); PackageManager pm = activity.getPackageManager();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    if (info.activityInfo == null || !info.activityInfo.exported) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(activity.getPackageName()) || found.containsKey(pkg) || LibraryApps.excludedFromCatalog(excluded, pkg)) continue;
                    try { found.put(pkg, new AppAdapter.Entry(info.loadLabel(pm).toString(), new ComponentName(pkg, info.activityInfo.name), null, null)); }
                    catch (RuntimeException ignored) { /* App was removed during discovery. */ }
                }
            }
            ArrayList<AppAdapter.Entry> sorted = new ArrayList<>(found.values()); sorted.sort(Comparator.comparing(e -> e.label.toLowerCase(Locale.ROOT)));
            post(() -> { if (disposed || ticket != generation) return; catalog.clear(); catalog.addAll(sorted); loaded = true; fillCatalog(); });
        });
    }
    private void fillCatalog() {
        if (results == null) return;
        filtered.clear();
        for (AppAdapter.Entry app : catalog) {
            if (QuietMachineModel.matches(app.label, app.pkg, query, category)) filtered.add(app);
        }
        String message = !category.equals("All") && !category.equals("Music")
                ? category + " catalog unavailable. Choose All to browse installed apps."
                : filtered.isEmpty() ? (loaded ? "No matching installed apps" : "Loading installed apps…") : "";
        catalogMessage.setText(message); catalogMessage.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        results.getAdapter().notifyDataSetChanged();
    }
    private final class CatalogHolder extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView title;
        CatalogHolder(LinearLayout card, ImageView image, TextView label) { super(card); art = image; title = label; }
    }
    private final class CatalogAdapter extends RecyclerView.Adapter<CatalogHolder> {
        CatalogAdapter() { setHasStableIds(true); }
        @Override public long getItemId(int position) { return filtered.get(position).component.flattenToString().hashCode(); }
        @Override public int getItemCount() { return filtered.size(); }
        @Override public CatalogHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout card = row(); card.setPadding(d(8), d(8), d(8), d(8));
            ImageView art = new ImageView(activity);
            ColorMatrix gray = new ColorMatrix(); gray.setSaturation(0); art.setColorFilter(new ColorMatrixColorFilter(gray));
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE); card.addView(art, new LayoutParams(d(50), d(50)));
            LinearLayout labels = column(); labels.setPadding(d(10), 0, 0, 0); card.addView(labels, new LayoutParams(0, -2, 1));
            TextView title = text("", 17); title.setSingleLine(); title.setEllipsize(TextUtils.TruncateAt.END); labels.addView(title);
            TextView source = text("Installed app", 12); source.setTextColor(MUTED); labels.addView(source);
            RecyclerView.LayoutParams size = new RecyclerView.LayoutParams(-1, d(80));
            size.setMargins(0, 0, d(8), d(8)); card.setLayoutParams(size);
            return new CatalogHolder(card, art, title);
        }
        @Override public void onBindViewHolder(CatalogHolder holder, int position) {
            AppAdapter.Entry app = filtered.get(position);
            holder.title.setText(app.label); holder.itemView.setTag(app.component.flattenToString());
            selectable(holder.itemView, () -> adapter.quietLaunch(app));
            holder.itemView.setBackground(border(holder.itemView.hasFocus()));
            holder.art.setImageDrawable(icons.get(app.pkg));
            if (icons.get(app.pkg) != null) return;
            final int ticket = generation;
            loader.execute(() -> {
                android.graphics.drawable.Drawable icon;
                try {
                    android.graphics.drawable.Drawable source = activity.getPackageManager().getActivityIcon(app.component);
                    Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                    source.setBounds(0, 0, 64, 64); source.draw(new Canvas(bitmap));
                    icon = new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
                } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return; }
                final android.graphics.drawable.Drawable ready = icon;
                post(() -> {
                    if (disposed || ticket != generation) return;
                    icons.put(app.pkg, ready);
                    if (app.component.flattenToString().equals(holder.itemView.getTag())) holder.art.setImageDrawable(ready);
                });
            });
        }
    }
    private void settings(String action) {
        try { activity.startActivity(SystemSettings.intent(activity, action)); }
        catch (ActivityNotFoundException | SecurityException e) {
            try { activity.startActivity(SystemSettings.intent(activity, Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(activity, "System settings unavailable on this device", Toast.LENGTH_LONG).show(); }
        }
    }
    private LinearLayout panel(LinearLayout parent, String title) {
        LinearLayout card = column(); card.setPadding(d(14), d(16), d(14), d(16)); card.setBackground(border(false));
        TextView label = text(title.toUpperCase(Locale.ROOT), 12); label.setLetterSpacing(.12f); card.addView(label); space(card, 14);
        card.setMinimumHeight(d(260));
        LayoutParams p = new LayoutParams(0, -2, 1); p.setMargins(0, 0, d(8), 0); parent.addView(card, p); return card;
    }
    private void controlContent() {
        LinearLayout tabs = row();
        String[] names = {"Control", "Picture", "Sound", "Network", "System"};
        String[] actions = {"", Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS};
        for (int i = 0; i < names.length; i++) { final String action = actions[i]; TextView b = button(names[i], () -> { if (action.isEmpty()) show("Settings"); else settings(action); }); b.setTextSize(13); tabs.addView(b, new LayoutParams(0, d(48), 1)); }
        body.addView(tabs); space(body, 16);
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        LinearLayout content = column(); scroll.addView(content);
        body.addView(scroll, new LayoutParams(-1, 0, 1));
        LinearLayout panels = row(); panels.setGravity(Gravity.TOP); content.addView(panels);
        LinearLayout sound = panel(panels, "Sound"); AudioManager audio = activity.getSystemService(AudioManager.class);
        if (!volumeAvailable(audio)) { sound.addView(text("System controlled", 19)); sound.addView(button("Sound settings ↗", () -> settings(Settings.ACTION_SOUND_SETTINGS))); }
        else {
            TextView level = text(Integer.toString(audio.getStreamVolume(AudioManager.STREAM_MUSIC)), 30); sound.addView(level);
            SeekBar slider = new SeekBar(activity); volumeSlider = slider; volumeLevel = level; slider.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)); slider.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            slider.setKeyProgressIncrement(1);
            slider.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER) return false;
                if (event.getAction() == KeyEvent.ACTION_UP) settings(Settings.ACTION_SOUND_SETTINGS);
                return true;
            });
            slider.setProgressTintList(ColorStateList.valueOf(ORANGE)); slider.setThumbTintList(ColorStateList.valueOf(INK)); slider.setContentDescription("Media volume; left and right adjust");
            slider.setBackground(border(false)); slider.setOnFocusChangeListener((v, f) -> v.setBackground(border(f)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar b, int value, boolean user) {
                    if (!user) return;
                    try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); }
                    catch (RuntimeException e) {
                        b.setEnabled(false);
                        Toast.makeText(activity, "Volume is system controlled. Use Sound settings or the TV remote.", Toast.LENGTH_LONG).show();
                    }
                    syncVolume();
                }
                public void onStartTrackingTouch(SeekBar b) { }
                public void onStopTrackingTouch(SeekBar b) { }
            }); sound.addView(slider, new LayoutParams(-1, d(48))); sound.addView(text("0 — " + slider.getMax() + "  /  Media volume", 12));
        }
        LinearLayout ambient = panel(panels, "Ambient light"); ambient.addView(text("Not connected", 22)); space(ambient, 16); ambient.addView(text("Requires a supported\nlighting integration", 14));
        LinearLayout focus = panel(panels, "Focus"); String mode = activity.getPreferences(0).getString("quiet_focus", "Off");
        focus.addView(text(mode, 26));
        for (String m : new String[]{"Off", "Day", "Evening", "Night"}) {
            TextView b = button((m.equals(mode) ? "• " : "") + m, () -> { activity.getPreferences(0).edit().putString("quiet_focus", m).apply(); applyFocusMode(); show("Settings"); restoreFocus("mode:" + m); });
            b.setTag("mode:" + m); b.setTextSize(13); b.setPadding(d(8), 0, 0, 0); focus.addView(b, new LayoutParams(-1, d(34)));
        }
        LinearLayout display = panel(panels, "Display"); display.addView(text("System managed", 21)); space(display, 12); display.addView(button("Picture settings ↗", () -> settings(Settings.ACTION_DISPLAY_SETTINGS))); display.addView(text("TV calibration stays\nin system settings", 13));
        space(content, 12); content.addView(text("Focus adjusts this launcher window only. TV picture and connected lights are unchanged.", 13)); space(content, 12);
        LinearLayout footer = row(); footer.addView(button("Launcher preferences", () -> activity.startActivity(new Intent(activity, LauncherSettingsActivity.class))));
        TextView hdmi = button("HDMI / inputs", inputs); inputAnchor = hdmi;
        footer.addView(hdmi); footer.addView(button("Classic launcher", classic)); content.addView(footer);
    }
    private boolean volumeAvailable(AudioManager audio) {
        try { return audio != null && !audio.isVolumeFixed() && audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0; }
        catch (RuntimeException unavailable) { return false; }
    }
    private void applyFocusMode() {
        String mode = activity.getPreferences(0).getString("quiet_focus", "Off");
        WindowManager.LayoutParams p = activity.getWindow().getAttributes();
        p.screenBrightness = QuietMachineModel.brightness(mode); activity.getWindow().setAttributes(p);
        // Guaranteed launcher-only visual response even when TV firmware ignores window brightness.
        int dim = "Night".equals(mode) ? 0x22000000 : "Evening".equals(mode) ? 0x11000000 : 0;
        setForeground(new android.graphics.drawable.ColorDrawable(dim));
    }
}

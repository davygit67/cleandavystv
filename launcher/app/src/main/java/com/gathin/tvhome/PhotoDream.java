package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fork compatibility (cleandavystv): the photo screensaver of the launcher this fork replaces, kept under the same
 * component name so the TV's existing screensaver_components entry stays valid after an in-place update. The old
 * build carried twelve bundled photos; this one shows the Quiet Machine photo pack (wallpapers/la.jpg, usc.jpg,
 * florence.jpg, installed through ThemeProvider) in grayscale, each slowly zooming, crossfading every SLIDE_MS,
 * with a small 24-hour clock. Without the pack it uses the theme's wallpaper.jpg, and without that only the clock.
 */
public class PhotoDream extends DreamService {

    static final String[] PHOTOS = {"la", "usc", "florence"};
    private static final long SLIDE_MS = 20_000;
    private static final long FADE_MS = 2_500;
    private static final float ZOOM = 1.08f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final List<File> photos = new ArrayList<>();
    private ImageView front, back;
    private ObjectAnimator zoom;
    private TextClock clock;
    private int next;
    private boolean dreaming;

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            showNext();
            if (photos.size() > 1) handler.postDelayed(this, SLIDE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        front = photoView();
        back = photoView();
        root.addView(back);
        root.addView(front);
        clock = clock();
        root.addView(clock);
        setContentView(root);

        File dir = Config.dir(this);
        if (dir != null) {
            for (String name : PHOTOS) {
                File f = new File(new File(dir, "wallpapers"), name + ".jpg");
                if (f.isFile()) photos.add(f);
            }
            File wallpaper = new File(dir, "wallpaper.jpg");
            if (photos.isEmpty() && wallpaper.isFile()) photos.add(wallpaper);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        if (!photos.isEmpty()) handler.post(advance);
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        handler.removeCallbacks(advance);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        handler.removeCallbacks(advance);
        if (zoom != null) zoom.cancel();
        decoder.shutdownNow();
        super.onDetachedFromWindow();
    }

    /** Decodes the next photo off the main thread, then fades it in over the current one with its slow zoom. */
    private void showNext() {
        File file = photos.get(next);
        next = (next + 1) % photos.size();
        decoder.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int sample = 1;
            while (options.outWidth / sample > 1920 || options.outHeight / sample > 1920) sample *= 2;
            options.inSampleSize = sample;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            if (bitmap != null) handler.post(() -> crossfade(bitmap));
        });
    }

    private void crossfade(Bitmap bitmap) {
        if (!dreaming) return;
        ImageView incoming = back, outgoing = front;
        incoming.animate().cancel();
        incoming.setImageBitmap(bitmap);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        if (zoom != null) zoom.cancel();
        zoom = ObjectAnimator.ofPropertyValuesHolder(incoming, PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, ZOOM),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, ZOOM));
        zoom.setInterpolator(new LinearInterpolator());
        zoom.setDuration(SLIDE_MS + FADE_MS);
        zoom.start();
        incoming.animate().alpha(1f).setDuration(FADE_MS).start();
        outgoing.animate().alpha(0f).setStartDelay(FADE_MS).setDuration(1)
                .withEndAction(() -> outgoing.setImageDrawable(null)).start();
        front = incoming;
        back = outgoing;
        clock.bringToFront();
    }

    private ImageView photoView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ColorMatrix mono = new ColorMatrix();
        mono.setSaturation(0);
        v.setColorFilter(new ColorMatrixColorFilter(mono));
        v.setAlpha(0f);
        v.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private TextClock clock() {
        TextClock c = new TextClock(this);
        c.setFormat12Hour("HH:mm");
        c.setFormat24Hour("HH:mm");
        c.setTextColor(0xe6f2f0e9);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        float dp = getResources().getDisplayMetrics().density;
        // A dark plate, so the clock stays legible over a bright sky.
        android.graphics.drawable.GradientDrawable plate = new android.graphics.drawable.GradientDrawable();
        plate.setColor(0x8c1b1b19);
        plate.setCornerRadius(2 * dp);
        c.setBackground(plate);
        c.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, (int) (48 * dp), (int) (36 * dp));
        c.setLayoutParams(lp);
        return c;
    }
}

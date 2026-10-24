package com.ailearn.drive;

import android.content.Context;
import android.graphics.*;
import android.os.Environment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;

/** Loads + caches PNGs. Lookup order for <name>.png:
 *   1. assets/sprites/<name>.png   (recommended: packaged in the APK, no permissions, any file name)
 *   2. assets/<name>.png
 *   3. res/drawable*\/<name>.png   (file name must be lowercase a-z 0-9 _ only)
 *   4. <app files dir>/sprites/<name>.png
 *   5. /sdcard/Documents/AiDriveSprites/<name>.png  (needs storage permission granted in system settings)
 *  Missing sprites fall back to generated art so the game always runs.
 *  All methods are synchronized: the game thread draws while the UI thread can reload. */
public class SpriteManager {
    public static final String[] CAR_NAMES = {"car_rally", "car_sports", "car_taxi", "car_truck"};
    public static final String[] ASSET_NAMES = {"car_rally", "car_sports", "car_taxi", "car_truck", "menu_bg"};
    static final int[] COLORS = {0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFFEC407A};
    static final int MAX_SIDE = 1280;       // larger images are down-sampled while decoding (prevents out-of-memory)
    static final int CAR_DRAW_WIDTH = 44;   // cars are drawn this wide (world units); height keeps the sprite's aspect ratio
    private final Context ctx;
    private final HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private final HashSet<String> missing = new HashSet<String>();

    public SpriteManager(Context c) { ctx = c; }

    public synchronized void clear() { cache.clear(); missing.clear(); }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally { in.close(); }
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int s = 1;
        while (o.outWidth / s > MAX_SIDE || o.outHeight / s > MAX_SIDE) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        o.inScaled = false; // never let the phone's screen density resize the sprite
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private Bitmap fromAssets(String path) {
        try { return decode(readAll(ctx.getAssets().open(path))); } catch (Throwable t) { return null; }
    }

    private static Bitmap fromFile(File f) {
        try { if (f.exists()) return decode(readAll(new FileInputStream(f))); } catch (Throwable t) { }
        return null;
    }

    /** Returns bitmap or null if no such asset exists. */
    public synchronized Bitmap load(String name) {
        Bitmap b = cache.get(name);
        if (b != null) return b;
        if (missing.contains(name)) return null;
        b = fromAssets("sprites/" + name + ".png");
        if (b == null) b = fromAssets(name + ".png");
        if (b == null) {
            try {
                int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
                if (id != 0) b = decode(readAll(ctx.getResources().openRawResource(id)));
            } catch (Throwable t) { b = null; }
        }
        if (b == null) b = fromFile(new File(ctx.getFilesDir(), "sprites/" + name + ".png"));
        if (b == null) {
            try { b = fromFile(new File(Environment.getExternalStorageDirectory(), "Documents/AiDriveSprites/" + name + ".png")); }
            catch (Throwable t) { b = null; }
        }
        if (b == null) { missing.add(name); return null; }
        cache.put(name, b);
        return b;
    }

    public synchronized boolean exists(String name) { return load(name) != null; }
    public synchronized Bitmap ui(String name) { return load(name); }

    /** Car sprite for population index (sprite must face RIGHT / +X). */
    public synchronized Bitmap car(int pop) {
        String key = "car#" + pop;
        Bitmap b = cache.get(key);
        if (b != null) return b;
        Bitmap src = load(CAR_NAMES[pop % CAR_NAMES.length]);
        if (src != null) {
            try { b = Bitmap.createScaledBitmap(src, CAR_DRAW_WIDTH, Math.max(8, CAR_DRAW_WIDTH * src.getHeight() / src.getWidth()), true); }
            catch (Throwable t) { b = null; }
        }
        if (b == null) b = makeCar(COLORS[pop % COLORS.length]);
        cache.put(key, b);
        return b;
    }

    private Bitmap makeCar(int color) {
        Bitmap b = Bitmap.createBitmap(40, 22, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawRoundRect(new RectF(1, 1, 39, 21), 6, 6, p);
        p.setColor(0xCC101820);
        c.drawRect(22, 4, 30, 18, p);
        c.drawRect(6, 5, 12, 17, p);
        p.setColor(0xFFFFEB3B);
        c.drawRect(36, 3, 39, 7, p);
        c.drawRect(36, 15, 39, 19, p);
        return b;
    }
}

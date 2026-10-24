package com.ailearn.drive;

import android.content.Context;
import android.graphics.*;
import java.util.ArrayList;

/** Canvas button/label helpers. Buttons use sprite "btn_<id>" if present, else a drawn rounded rect. */
public class UIManager {
    public static class Btn {
        public String id, label;
        public RectF r = new RectF();
        public boolean on;
        public Btn(String id, String label) { this.id = id; this.label = label; }
    }

    public final float d;
    public final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SpriteManager sprites;

    public UIManager(Context c, SpriteManager s) { d = c.getResources().getDisplayMetrics().density; sprites = s; }

    public void drawBtn(Canvas c, Btn b) {
        Bitmap bm = sprites.ui("btn_" + b.id);
        if (bm != null) c.drawBitmap(bm, null, b.r, null);
        else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(b.on ? 0xFF2E7D32 : 0xFF263A5A);
            c.drawRoundRect(b.r, 10 * d, 10 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(b.on ? 0xFF81C784 : 0xFF7AA2F7);
            c.drawRoundRect(b.r, 10 * d, 10 * d, p);
        }
        float size = Math.min(16 * d, b.r.height() * 0.45f);
        p.setTextSize(size);
        while (size > 8 && p.measureText(b.label) > b.r.width() - 8 * d) { size -= 1; p.setTextSize(size); }
        text(c, b.label, b.r.centerX(), b.r.centerY(), size, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    /** y = vertical center of the text. */
    public void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(a);
        c.drawText(s, x, y - (p.ascent() + p.descent()) / 2f, p);
    }

    public void panel(Canvas c, RectF r, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(r, 8 * d, 8 * d, p);
    }

    public Btn hit(ArrayList<Btn> l, float x, float y) {
        for (Btn b : l) if (b.r.contains(x, y)) return b;
        return null;
    }

    public void background(Canvas c, int w, int h) {
        Bitmap bg = sprites.ui("menu_bg");
        if (bg != null) c.drawBitmap(bg, null, new RectF(0, 0, w, h), null);
        else {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFF0E1420, 0xFF1D2B45, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
    }
}

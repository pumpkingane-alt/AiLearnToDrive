package com.ailearn.drive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Node graph of a NeuralNetwork. Usable as a View, or embedded in any canvas via render(). */
public class NeuralVisualizerView extends View {
    public NeuralNetwork net;
    private final Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG), np = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    static final String[] OUT = {"Steer Left", "Steer Right", "Throttle", "Brake"};

    public NeuralVisualizerView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF10141C);
    }

    public void setNetwork(NeuralNetwork n) { net = n; postInvalidate(); }

    @Override protected void onDraw(Canvas c) { render(c, 0, 0, getWidth(), getHeight()); }

    static int blend(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int actColor(float a) {
        a = Math.max(-1f, Math.min(1f, a));
        return a >= 0 ? blend(0xFF3A3F4B, 0xFFFFD54F, a) : blend(0xFF3A3F4B, 0xFF42A5F5, -a);
    }

    String label(int col, int j, int cols, int[] sizes) {
        if (col == 0) {
            int rays = sizes[0] - 2;
            if (rays < 1) return "In " + (j + 1);
            return j < rays ? "Ray " + (j + 1) : (j == rays ? "Speed" : "Angle");
        }
        if (col == cols - 1) return j < OUT.length ? OUT[j] : "Out " + (j + 1);
        return null;
    }

    public void render(Canvas c, float x, float y, float w, float h) {
        NeuralNetwork n = net;
        if (n == null || n.sizes.length < 2) return;
        int cols = n.sizes.length;
        boolean names = GameSettings.showNodeNames;
        float ml = names ? 72 * d : 16 * d, mr = names ? 72 * d : 16 * d, pad = 14 * d;
        float gx0 = x + ml, gx1 = x + w - mr;
        int maxN = 1;
        for (int s : n.sizes) maxN = Math.max(maxN, s);
        float r = Math.max(3 * d, Math.min(13 * d, (h - 2 * pad) / (maxN * 2.4f)));
        float[][] px = new float[cols][], py = new float[cols][];
        for (int i = 0; i < cols; i++) {
            px[i] = new float[n.sizes[i]];
            py[i] = new float[n.sizes[i]];
            for (int j = 0; j < n.sizes[i]; j++) {
                px[i][j] = gx0 + (gx1 - gx0) * i / (cols - 1);
                py[i][j] = y + pad + (h - 2 * pad) * (j + 0.5f) / n.sizes[i];
            }
        }
        // connections
        int conns = 0;
        for (int l = 0; l < cols - 1; l++) conns += n.sizes[l] * n.sizes[l + 1];
        lp.setStyle(Paint.Style.STROKE);
        for (int l = 0; l < cols - 1; l++)
            for (int o = 0; o < n.sizes[l + 1]; o++)
                for (int i = 0; i < n.sizes[l]; i++) {
                    float v = n.w[l][o][i], m = Math.abs(v);
                    int alpha = 50 + (int) Math.min(200, m * 140);
                    lp.setColor(v >= 0 ? Color.argb(alpha, 76, 175, 80) : Color.argb(alpha, 229, 57, 53));
                    lp.setStrokeWidth(Math.max(0.5f, Math.min(4f * d, m * 2.5f * d)));
                    c.drawLine(px[l][i], py[l][i], px[l + 1][o], py[l + 1][o], lp);
                }
        if (GameSettings.showWeightValues && conns <= 400) {
            tp.setTextSize(8 * d);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(0xFFFFFFFF);
            for (int l = 0; l < cols - 1; l++)
                for (int o = 0; o < n.sizes[l + 1]; o++)
                    for (int i = 0; i < n.sizes[l]; i++) {
                        float t = 0.3f + 0.4f * ((i * 7 + o * 3) % 5) / 4f;
                        float tx = px[l][i] + (px[l + 1][o] - px[l][i]) * t;
                        float ty = py[l][i] + (py[l + 1][o] - py[l][i]) * t - 2 * d;
                        c.drawText(String.valueOf(Math.round(n.w[l][o][i] * 100) / 100f), tx, ty, tp);
                    }
        }
        // nodes (shaded by live activation)
        tp.setTextSize(10 * d);
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < n.sizes[i]; j++) {
                float a = (n.act != null && n.act[i] != null && j < n.act[i].length) ? n.act[i][j] : 0f;
                np.setStyle(Paint.Style.FILL);
                np.setColor(actColor(a));
                c.drawCircle(px[i][j], py[i][j], r, np);
                np.setStyle(Paint.Style.STROKE);
                np.setStrokeWidth(1.2f * d);
                np.setColor(0xFFDDDDDD);
                c.drawCircle(px[i][j], py[i][j], r, np);
                if (names) {
                    String s = label(i, j, cols, n.sizes);
                    if (s != null) {
                        tp.setColor(0xFFDDDDDD);
                        boolean left = i == 0;
                        tp.setTextAlign(left ? Paint.Align.RIGHT : Paint.Align.LEFT);
                        c.drawText(s, px[i][j] + (left ? -r - 4 * d : r + 4 * d), py[i][j] - (tp.ascent() + tp.descent()) / 2f, tp);
                    }
                }
            }
    }
}

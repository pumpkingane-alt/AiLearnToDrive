package com.ailearn.drive;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Closed loop track defined by control nodes; smoothed (Catmull-Rom) into a dense centerline with inner/outer walls. */
public class Track {
    public static final int SUB = 6;
    public ArrayList<float[]> nodes = new ArrayList<float[]>();
    public float width = 120f;
    public String name = "custom";
    // built data (per centerline point)
    public float[] cx = new float[0], cy = new float[0], tx, ty, ox, oy, ix, iy, cum;
    public float[] segs = new float[0], segR = new float[0]; // segs: x1,y1,x2,y2 ; 2 per point (outer, inner)
    public int m, segCount;
    public float total, minX = -100, minY = -100, maxX = 100, maxY = 100, startX, startY, startAngle;
    private final Path path = new Path();

    public static Track createDefault() {
        Track t = new Track();
        t.name = "default";
        t.width = 130f;
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            double r = 1 + 0.25 * Math.sin(3 * a) + 0.1 * Math.cos(2 * a);
            t.nodes.add(new float[]{(float) (Math.cos(a) * 750 * r), (float) (Math.sin(a) * 450 * r)});
        }
        t.build();
        return t;
    }

    public void build() {
        int n = nodes.size();
        if (n < 3) { m = 0; segCount = 0; return; }
        m = n * SUB;
        cx = new float[m]; cy = new float[m]; tx = new float[m]; ty = new float[m];
        ox = new float[m]; oy = new float[m]; ix = new float[m]; iy = new float[m]; cum = new float[m];
        for (int i = 0; i < n; i++) {
            float[] p0 = nodes.get((i + n - 1) % n), p1 = nodes.get(i), p2 = nodes.get((i + 1) % n), p3 = nodes.get((i + 2) % n);
            for (int s = 0; s < SUB; s++) {
                float t = s / (float) SUB, t2 = t * t, t3 = t2 * t;
                int k = i * SUB + s;
                cx[k] = 0.5f * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                cy[k] = 0.5f * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
            }
        }
        float hw = width / 2f;
        minX = minY = 1e9f; maxX = maxY = -1e9f;
        for (int k = 0; k < m; k++) {
            int pv = (k + m - 1) % m, nx = (k + 1) % m;
            float dx = cx[nx] - cx[pv], dy = cy[nx] - cy[pv];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4f) len = 1f;
            tx[k] = dx / len; ty[k] = dy / len;
            ox[k] = cx[k] - ty[k] * hw; oy[k] = cy[k] + tx[k] * hw;
            ix[k] = cx[k] + ty[k] * hw; iy[k] = cy[k] - tx[k] * hw;
            minX = Math.min(minX, Math.min(ox[k], ix[k])); maxX = Math.max(maxX, Math.max(ox[k], ix[k]));
            minY = Math.min(minY, Math.min(oy[k], iy[k])); maxY = Math.max(maxY, Math.max(oy[k], iy[k]));
        }
        segCount = m * 2;
        segs = new float[segCount * 4];
        segR = new float[segCount];
        for (int k = 0; k < m; k++) {
            int j = (k + 1) % m;
            setSeg(2 * k, ox[k], oy[k], ox[j], oy[j]);
            setSeg(2 * k + 1, ix[k], iy[k], ix[j], iy[j]);
        }
        cum[0] = 0;
        for (int k = 1; k < m; k++) cum[k] = cum[k - 1] + dist(cx[k - 1], cy[k - 1], cx[k], cy[k]);
        total = cum[m - 1] + dist(cx[m - 1], cy[m - 1], cx[0], cy[0]);
        startX = cx[0]; startY = cy[0];
        startAngle = (float) Math.atan2(cy[1] - cy[0], cx[1] - cx[0]);
    }

    private void setSeg(int s, float x1, float y1, float x2, float y2) {
        segs[4 * s] = x1; segs[4 * s + 1] = y1; segs[4 * s + 2] = x2; segs[4 * s + 3] = y2;
        segR[s] = dist(x1, y1, x2, y2) / 2f;
    }

    static float dist(float a, float b, float c, float d) { float x = c - a, y = d - b; return (float) Math.sqrt(x * x + y * y); }

    /** Path distance covered after passing 'passed' centerline points (supports multiple laps). */
    public float pathDist(int passed) { return (passed / m) * total + cum[passed % m]; }

    private boolean segVisible(Camera2D cam, int k) {
        int j = (k + 1) % m;
        float a = Math.min(Math.min(ox[k], ox[j]), Math.min(ix[k], ix[j]));
        float b = Math.max(Math.max(ox[k], ox[j]), Math.max(ix[k], ix[j]));
        float c = Math.min(Math.min(oy[k], oy[j]), Math.min(iy[k], iy[j]));
        float d = Math.max(Math.max(oy[k], oy[j]), Math.max(iy[k], iy[j]));
        return cam.visible(a, c, b, d);
    }

    /** Draws only the visible parts (viewport culling). Call inside camera transform. */
    public void draw(Canvas c, Camera2D cam, Paint p) {
        if (m < 3) return;
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0xFF3A3A40);
        for (int k = 0; k < m; k++) {
            if (!segVisible(cam, k)) continue;
            int j = (k + 1) % m;
            path.reset();
            path.moveTo(ox[k], oy[k]); path.lineTo(ox[j], oy[j]);
            path.lineTo(ix[j], iy[j]); path.lineTo(ix[k], iy[k]);
            path.close();
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        for (int k = 0; k < m; k++) {
            if (!segVisible(cam, k)) continue;
            int j = (k + 1) % m;
            p.setColor(0xFFE53935);
            c.drawLine(ox[k], oy[k], ox[j], oy[j], p);
            p.setColor(0xFFFFFFFF);
            c.drawLine(ix[k], iy[k], ix[j], iy[j], p);
        }
        p.setStrokeWidth(10f);
        p.setColor(0xFFFFFFFF);
        c.drawLine(ox[0], oy[0], ix[0], iy[0], p);
        p.setStrokeWidth(5f);
        p.setColor(0xFF000000);
        c.drawLine((ox[0] + cx[0]) / 2, (oy[0] + cy[0]) / 2, cx[0], cy[0], p);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\n \"name\": \"" + name.replace("\"", "") + "\",\n \"width\": " + width + ",\n \"nodes\": [");
        for (int i = 0; i < nodes.size(); i++) {
            float[] p = nodes.get(i);
            sb.append(i > 0 ? "," : "").append("\n  [").append(p[0]).append(", ").append(p[1]).append("]");
        }
        return sb.append("\n ]\n}").toString();
    }

    public static Track fromJson(String s) throws Exception {
        JSONObject o = new JSONObject(s);
        Track t = new Track();
        t.name = o.optString("name", "custom");
        t.width = (float) o.optDouble("width", 120);
        JSONArray a = o.getJSONArray("nodes");
        for (int i = 0; i < a.length(); i++) {
            JSONArray p = a.getJSONArray(i);
            t.nodes.add(new float[]{(float) p.getDouble(0), (float) p.getDouble(1)});
        }
        t.build();
        return t;
    }
}

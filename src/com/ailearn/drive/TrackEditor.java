package com.ailearn.drive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/** Point-and-node track drawing: tap=add node, drag node=move, drag empty=pan, pinch=zoom, Del mode: tap node=delete. */
public class TrackEditor {
    public Track track;
    public final Camera2D cam = new Camera2D();
    public boolean deleteMode;
    private int dragNode = -1;
    private float downX, downY, lastX, lastY;
    private boolean moved, scaling;
    private final ScaleGestureDetector sgd;
    private final float d;

    public TrackEditor(Context ctx, Track t) {
        d = ctx.getResources().getDisplayMetrics().density;
        track = t;
        cam.mode = Camera2D.FREE;
        if (t.m >= 3) { cam.snapTo((t.minX + t.maxX) / 2f, (t.minY + t.maxY) / 2f); cam.zoom = cam.fitZoom(t) * 0.9f; }
        else cam.zoom = 0.6f;
        sgd = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector g) { cam.zoomBy(g.getScaleFactor()); return true; }
        });
    }

    private int hitNode(float sx, float sy) {
        float wx = cam.toWorldX(sx), wy = cam.toWorldY(sy), r = 26 * d / cam.zoom;
        for (int i = track.nodes.size() - 1; i >= 0; i--) {
            float[] p = track.nodes.get(i);
            if (Math.abs(p[0] - wx) < r && Math.abs(p[1] - wy) < r) return i;
        }
        return -1;
    }

    public boolean onTouch(MotionEvent e) {
        sgd.onTouchEvent(e);
        if (e.getPointerCount() > 1 || sgd.isInProgress()) { scaling = true; dragNode = -1; return true; }
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scaling = false; moved = false; downX = lastX = x; downY = lastY = y;
                dragNode = hitNode(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (scaling) { lastX = x; lastY = y; break; }
                if (!moved && Math.abs(x - downX) + Math.abs(y - downY) > 14 * d) moved = true;
                if (moved) {
                    if (dragNode >= 0 && !deleteMode) {
                        float[] p = track.nodes.get(dragNode);
                        p[0] = cam.toWorldX(x); p[1] = cam.toWorldY(y);
                        track.build();
                    } else cam.pan(x - lastX, y - lastY);
                }
                lastX = x; lastY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (!scaling && !moved) {
                    if (deleteMode) { if (dragNode >= 0) track.nodes.remove(dragNode); }
                    else if (dragNode < 0) track.nodes.add(new float[]{cam.toWorldX(x), cam.toWorldY(y)});
                    track.build();
                }
                scaling = false; dragNode = -1;
                break;
            case MotionEvent.ACTION_CANCEL:
                scaling = false; dragNode = -1;
                break;
        }
        return true;
    }

    public void draw(Canvas c, Paint p) {
        c.drawColor(0xFF1B2A1B);
        c.save();
        cam.apply(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f / cam.zoom);
        p.setColor(0x22FFFFFF);
        float g = 200f;
        for (float gx = (float) Math.floor(cam.left() / g) * g; gx <= cam.right(); gx += g) c.drawLine(gx, cam.top(), gx, cam.bottom(), p);
        for (float gy = (float) Math.floor(cam.top() / g) * g; gy <= cam.bottom(); gy += g) c.drawLine(cam.left(), gy, cam.right(), gy, p);
        track.draw(c, cam, p);
        int n = track.nodes.size();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f / cam.zoom);
        p.setColor(0xAAFFEB3B);
        for (int i = 0; i < n - 1; i++) { float[] a = track.nodes.get(i), b = track.nodes.get(i + 1); c.drawLine(a[0], a[1], b[0], b[1], p); }
        if (n >= 3) { float[] a = track.nodes.get(n - 1), b = track.nodes.get(0); c.drawLine(a[0], a[1], b[0], b[1], p); }
        float r = 12 * d / cam.zoom;
        for (int i = 0; i < n; i++) {
            float[] q = track.nodes.get(i);
            p.setStyle(Paint.Style.FILL);
            p.setColor(i == 0 ? 0xFF4CAF50 : (i == dragNode ? 0xFFFF9800 : 0xFF2196F3));
            c.drawCircle(q[0], q[1], r, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * 1.1f);
            c.drawText(String.valueOf(i + 1), q[0], q[1] - (p.ascent() + p.descent()) / 2f, p);
        }
        c.restore();
    }
}

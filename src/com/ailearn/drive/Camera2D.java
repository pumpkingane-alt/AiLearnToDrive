package com.ailearn.drive;

import android.graphics.Canvas;

/** Viewport: Follow Best Car / Free Pan / Overview. world->screen = translate(w/2,h/2) * scale(zoom) * translate(-cam). */
public class Camera2D {
    public static final int FOLLOW = 0, FREE = 1, OVERVIEW = 2;
    public int mode = FOLLOW;
    public float x, y, zoom = 1f;
    public int vw = 800, vh = 480;

    public void setViewport(int w, int h) { vw = w; vh = h; }

    public void update(float tx, float ty, Track t) {
        if (mode == FOLLOW) {
            x += (tx - x) * 0.12f;
            y += (ty - y) * 0.12f;
            zoom += (GameSettings.cameraZoom - zoom) * 0.1f;
        } else if (mode == OVERVIEW && t != null && t.m > 2) {
            x += ((t.minX + t.maxX) / 2f - x) * 0.15f;
            y += ((t.minY + t.maxY) / 2f - y) * 0.15f;
            zoom += (fitZoom(t) - zoom) * 0.15f;
        }
    }

    public float fitZoom(Track t) {
        float w = Math.max(1f, t.maxX - t.minX), h = Math.max(1f, t.maxY - t.minY);
        return Math.min(vw / w, vh / h) * 0.85f;
    }

    public void snapTo(float px, float py) { x = px; y = py; }

    public void apply(Canvas c) {
        c.translate(vw / 2f, vh / 2f);
        c.scale(zoom, zoom);
        c.translate(-x, -y);
    }

    public float left() { return x - vw / 2f / zoom; }
    public float right() { return x + vw / 2f / zoom; }
    public float top() { return y - vh / 2f / zoom; }
    public float bottom() { return y + vh / 2f / zoom; }

    /** Viewport culling test for a world-space bounding box. */
    public boolean visible(float minX, float minY, float maxX, float maxY) {
        return maxX >= left() && minX <= right() && maxY >= top() && minY <= bottom();
    }

    public float toWorldX(float sx) { return (sx - vw / 2f) / zoom + x; }
    public float toWorldY(float sy) { return (sy - vh / 2f) / zoom + y; }
    public void pan(float dxScreen, float dyScreen) { x -= dxScreen / zoom; y -= dyScreen / zoom; }
    public void zoomBy(float f) { zoom = Math.max(0.05f, Math.min(4f, zoom * f)); }
}

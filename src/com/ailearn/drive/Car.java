package com.ailearn.drive;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Agent: arcade physics + raycast vision + brain. Heading angle in radians (screen coords, +angle = turn right). */
public class Car {
    public static final float MAX_SPEED = 6f, RADIUS = 9f;
    public float x, y, angle, speed, steer, fitness, speedSum, penalty;
    public boolean alive = true;
    public NeuralNetwork brain;
    public int pop, nextIdx, passed, idle, ticks;
    public float[] rays = new float[0], rayA = new float[0], inputs = new float[0];
    private int[] near = new int[0];
    private int nearCount;

    public Car(NeuralNetwork b, int pop) { brain = b; this.pop = pop; }

    public void reset(Track t) {
        x = t.startX; y = t.startY; angle = t.startAngle;
        speed = 0; steer = 0; alive = true;
        nextIdx = 1 % t.m; passed = 0; idle = 0; ticks = 0;
        fitness = 0; speedSum = 0; penalty = 0;
        int n = GameSettings.rayCount;
        if (rays.length != n) { rays = new float[n]; rayA = new float[n]; }
        if (inputs.length != n + 2) inputs = new float[n + 2];
        if (near.length < t.segCount) near = new int[t.segCount];
        for (int i = 0; i < n; i++) rays[i] = 1f;
    }

    public void update(Track t) {
        if (!alive) return;
        // 1. gather nearby wall segments (cheap distance prefilter)
        float reach = GameSettings.rayLength + 20f;
        nearCount = 0;
        float[] sg = t.segs;
        for (int s = 0; s < t.segCount; s++) {
            float r = reach + t.segR[s];
            float dx = (sg[4 * s] + sg[4 * s + 2]) * 0.5f - x;
            if (dx > r || dx < -r) continue;
            float dy = (sg[4 * s + 1] + sg[4 * s + 3]) * 0.5f - y;
            if (dx * dx + dy * dy <= r * r) near[nearCount++] = s;
        }
        // 2. raycasts
        int n = rays.length;
        float len = GameSettings.rayLength, spread = (float) Math.toRadians(GameSettings.raySpread);
        for (int i = 0; i < n; i++) {
            float a = angle + (n == 1 ? 0f : (i / (float) (n - 1) - 0.5f) * spread);
            rayA[i] = a;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a), best = len;
            for (int k = 0; k < nearCount; k++) {
                int s = near[k] * 4;
                best = rayHit(x, y, dx, dy, sg[s], sg[s + 1], sg[s + 2], sg[s + 3], best);
            }
            rays[i] = best / len;
            inputs[i] = rays[i];
        }
        inputs[n] = speed / MAX_SPEED;
        inputs[n + 1] = steer;
        // 3. brain: [steerLeft, steerRight, throttle, brake]
        float[] out = brain.forward(inputs);
        float oL = out.length > 0 ? out[0] : 0, oR = out.length > 1 ? out[1] : 0;
        float th = out.length > 2 ? (out[2] + 1f) * 0.5f : 0.5f, br = out.length > 3 ? Math.max(0f, out[3]) : 0f;
        float target = Math.max(-1f, Math.min(1f, oR - oL));
        steer += (target - steer) * 0.3f;
        // 4. arcade physics
        speed += th * 0.16f - br * 0.25f - speed * 0.02f;
        speed = Math.max(0f, Math.min(MAX_SPEED, speed));
        angle += steer * 0.06f * Math.min(1f, speed / 2f);
        x += (float) Math.cos(angle) * speed;
        y += (float) Math.sin(angle) * speed;
        ticks++;
        speedSum += speed;
        // 5. wall hit
        float r2 = RADIUS * RADIUS;
        for (int k = 0; k < nearCount; k++) {
            int s = near[k] * 4;
            if (distSq(x, y, sg[s], sg[s + 1], sg[s + 2], sg[s + 3]) < r2) { alive = false; penalty = 25f; break; }
        }
        // 6. progress (plane crossing at the next centerline point)
        for (int g = 0; g < 4; g++) {
            float dx = x - t.cx[nextIdx], dy = y - t.cy[nextIdx];
            if (dx * t.tx[nextIdx] + dy * t.ty[nextIdx] > 0 && dx * dx + dy * dy < t.width * t.width * 1.4f) {
                passed++; nextIdx = (nextIdx + 1) % t.m; idle = 0;
            } else break;
        }
        if (++idle > 180) alive = false;
        fitness = t.pathDist(passed) + 8f * speedSum / ticks - penalty;
    }

    static float rayHit(float ox, float oy, float dx, float dy, float x1, float y1, float x2, float y2, float maxD) {
        float sx = x2 - x1, sy = y2 - y1, den = dx * sy - dy * sx;
        if (Math.abs(den) < 1e-6f) return maxD;
        float qx = x1 - ox, qy = y1 - oy;
        float t = (qx * sy - qy * sx) / den, u = (qx * dy - qy * dx) / den;
        return (t >= 0 && t < maxD && u >= 0 && u <= 1) ? t : maxD;
    }

    static float distSq(float px, float py, float x1, float y1, float x2, float y2) {
        float sx = x2 - x1, sy = y2 - y1, l2 = sx * sx + sy * sy;
        float t = l2 > 0 ? ((px - x1) * sx + (py - y1) * sy) / l2 : 0;
        t = Math.max(0, Math.min(1, t));
        float dx = px - (x1 + t * sx), dy = py - (y1 + t * sy);
        return dx * dx + dy * dy;
    }

    public void draw(Canvas c, Bitmap sprite, Paint sp, Paint lp, boolean leader) {
        if (alive && GameSettings.showSensors) {
            lp.setStyle(Paint.Style.STROKE);
            lp.setStrokeWidth(leader ? 2.5f : 1.2f);
            float len = GameSettings.rayLength;
            for (int i = 0; i < rays.length; i++) {
                int g = (int) (255 * rays[i]);
                lp.setColor((leader ? 0xFF000000 : 0x77000000) | ((255 - g) << 16) | (g << 8));
                c.drawLine(x, y, x + (float) Math.cos(rayA[i]) * rays[i] * len, y + (float) Math.sin(rayA[i]) * rays[i] * len, lp);
            }
        }
        c.save();
        c.translate(x, y);
        c.rotate((float) Math.toDegrees(angle));
        sp.setAlpha(alive ? 255 : 70);
        c.drawBitmap(sprite, -sprite.getWidth() / 2f, -sprite.getHeight() / 2f, sp);
        c.restore();
        if (leader && alive) {
            lp.setStyle(Paint.Style.STROKE); lp.setStrokeWidth(2f); lp.setColor(0xFFFFEB3B);
            c.drawCircle(x, y, 24f, lp);
        }
    }
}

package com.ailearn.drive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

/** Main SurfaceView: menu, simulation (HUD, minimap, camera modes, pause), and track-editor screens. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    public static final String VERSION = "1.2";
    static final int MENU = 0, SIM = 1, EDIT = 2;
    static final int[] STEPS = {1, 2, 4, 0}; // 0 = MAX (time-budgeted)

    final Activity act;
    final SurfaceHolder holder;
    Thread thread;
    volatile boolean running;
    final Object lock = new Object();
    int state = MENU, speedIdx = 0, W = 800, H = 480;
    boolean paused, showNet = true;
    final float d;
    final SpriteManager sprites;
    final UIManager ui;
    final Camera2D cam = new Camera2D();
    EvolutionManager evo;
    TrackEditor editor;
    final NeuralVisualizerView viz;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), carPaint = new Paint(Paint.FILTER_BITMAP_FLAG), linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path mmPath = new Path();
    final RectF tmp = new RectF();
    final ArrayList<UIManager.Btn> menuB = new ArrayList<UIManager.Btn>(), hudB = new ArrayList<UIManager.Btn>(),
        pauseB = new ArrayList<UIManager.Btn>(), editB = new ArrayList<UIManager.Btn>();
    final ScaleGestureDetector sgd;
    float lx, ly, downX, downY;
    boolean dragging, scaling;
    UIManager.Btn downBtn;

    public interface StrCb { void got(String s); }

    public GameView(Activity a) {
        super(a);
        act = a;
        d = a.getResources().getDisplayMetrics().density;
        holder = getHolder();
        holder.addCallback(this);
        sprites = new SpriteManager(a);
        ui = new UIManager(a, sprites);
        viz = new NeuralVisualizerView(a);
        sgd = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector g) { synchronized (lock) { zoomGesture(g.getScaleFactor()); } return true; }
        });
        setFocusable(true);
    }

    // ---------------- lifecycle ----------------
    public void surfaceCreated(SurfaceHolder h) {
        CrashLog.mark(act, "surface_created");
        running = true; thread = new Thread(this); thread.start();
    }

    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        synchronized (lock) { W = w; H = hh; cam.setViewport(w, hh); layoutUi(); }
    }

    public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException e) { }
    }

    public void onHostPause() {
        synchronized (lock) {
            if (evo != null && GameSettings.autoSaveProgress) SaveManager.saveCheckpoint(act, evo);
            GameSettings.save();
        }
    }

    public void onHostResume() { synchronized (lock) { layoutUi(); } }

    /** returns true if handled */
    public boolean onBack() {
        synchronized (lock) {
            if (state == SIM) { if (!paused) paused = true; else leaveSim(); return true; }
            if (state == EDIT) { state = MENU; layoutUi(); return true; }
            return false;
        }
    }

    void leaveSim() {
        if (evo != null) SaveManager.saveCheckpoint(act, evo);
        state = MENU;
        paused = false;
        layoutUi();
    }

    volatile String lastError;
    private boolean firstFrameDone;

    @Override public void run() {
        while (running) {
            long t0 = System.currentTimeMillis();
            synchronized (lock) { try { update(); } catch (Throwable e) { lastError = "update(): " + CrashLog.stack(e); } }
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    try { synchronized (lock) { draw(c); } } catch (Throwable e) { lastError = "draw(): " + CrashLog.stack(e); }
                    drawError(c);
                    if (!firstFrameDone) { firstFrameDone = true; CrashLog.mark(act, CrashLog.OK); }
                }
            } catch (Throwable e) {
                lastError = "canvas: " + CrashLog.stack(e);
            } finally { if (c != null) try { holder.unlockCanvasAndPost(c); } catch (Throwable e) { } }
            long dt = System.currentTimeMillis() - t0;
            if (dt < 16) try { Thread.sleep(16 - dt); } catch (InterruptedException e) { }
        }
    }

    /** In-app error finder: shows the last exception from the game loop on screen (tap to dismiss). */
    void drawError(Canvas c) {
        String err = lastError;
        if (err == null) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD000000);
        c.drawRect(0, 0, W, H, paint);
        paint.setColor(0xFFFF5252);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(11 * d);
        float y = 18 * d;
        c.drawText("GAME ERROR (tap to dismiss) - send me these lines:", 8 * d, y, paint);
        paint.setColor(0xFFFFFFFF);
        String[] lines = err.split("\n");
        for (int i = 0; i < lines.length && i < 22; i++) {
            y += 14 * d;
            String ln = lines[i];
            if (ln.length() > 110) ln = ln.substring(0, 110);
            c.drawText(ln, 8 * d, y, paint);
        }
    }

    // ---------------- layout ----------------
    void grid(ArrayList<UIManager.Btn> l, int per, float y0, float bh, float gap) {
        for (int i = 0; i < l.size(); i++) {
            int row = i / per, col = i % per, inRow = Math.min(per, l.size() - row * per);
            float bw = Math.min(84 * d, (W - gap * (inRow + 1)) / inRow);
            float x = (W - (inRow * bw + (inRow - 1) * gap)) / 2f + col * (bw + gap);
            float y = y0 + row * (bh + gap);
            l.get(i).r.set(x, y, x + bw, y + bh);
        }
    }

    void column(ArrayList<UIManager.Btn> l, float y0, float bh) {
        float bw = Math.min(W * 0.8f, 440 * d);
        for (int i = 0; i < l.size(); i++) {
            float y = y0 + i * bh * 1.25f;
            l.get(i).r.set((W - bw) / 2f, y, (W + bw) / 2f, y + bh);
        }
    }

    void layoutUi() {
        menuB.clear();
        if (SaveManager.hasCheckpoint(act)) menuB.add(new UIManager.Btn("resume", "Resume Long-Term Training (Gen " + SaveManager.checkpointGen(act) + ")"));
        menuB.add(new UIManager.Btn("start", "Start Evolution Simulation"));
        menuB.add(new UIManager.Btn("creator", "Custom Neural Creator"));
        menuB.add(new UIManager.Btn("editor", "Track Editor"));
        menuB.add(new UIManager.Btn("load", "Load Trained Brain"));
        menuB.add(new UIManager.Btn("settings", "Settings"));
        column(menuB, 78 * d, Math.min(52 * d, (H - 100 * d) / (menuB.size() * 1.25f)));

        hudB.clear();
        String[][] hs = {{"pause", "II"}, {"s0", "1x"}, {"s1", "2x"}, {"s2", "4x"}, {"s3", "MAX"}, {"follow", "Follow"}, {"free", "Free"}, {"map", "Map"}, {"net", "Net"}};
        for (String[] h : hs) hudB.add(new UIManager.Btn(h[0], h[1]));
        int per = W / d < 560 ? 5 : 9, rows = (hudB.size() + per - 1) / per;
        float bh = 38 * d, gap = 5 * d;
        grid(hudB, per, H - rows * (bh + gap), bh, gap);

        pauseB.clear();
        pauseB.add(new UIManager.Btn("resumesim", "Resume"));
        pauseB.add(new UIManager.Btn("tune", "Tune Evolution (mutation / breeding)"));
        pauseB.add(new UIManager.Btn("psettings", "Settings"));
        pauseB.add(new UIManager.Btn("export", "Export / Save Best Brain"));
        pauseB.add(new UIManager.Btn("exit", "Save & Exit to Menu"));
        float pbh = Math.min(46 * d, (H - 60 * d) / (pauseB.size() * 1.3f));
        column(pauseB, (H - pauseB.size() * pbh * 1.25f) / 2f + 10 * d, pbh);

        editB.clear();
        String[][] es = {{"ebk", "Back"}, {"eundo", "Undo"}, {"eclr", "Clear"}, {"ew-", "Width-"}, {"ew+", "Width+"}, {"edel", "Del: Off"}, {"esave", "Save"}, {"eload", "Load"}};
        for (String[] e : es) editB.add(new UIManager.Btn(e[0], e[1]));
        grid(editB, W / d < 560 ? 4 : 8, 6 * d, 38 * d, 5 * d);
    }

    // ---------------- update ----------------
    void update() {
        if (state != SIM || evo == null) return;
        cam.setViewport(W, H);
        if (!paused) {
            if (speedIdx < 3) { for (int i = 0; i < STEPS[speedIdx]; i++) evo.step(); }
            else {
                long end = System.nanoTime() + 12000000L;
                int n = 0;
                while (System.nanoTime() < end && n < 400) { evo.step(); n++; }
            }
        }
        Car l = evo.getLeader();
        cam.update(l != null ? l.x : evo.track.startX, l != null ? l.y : evo.track.startY, evo.track);
    }

    // ---------------- draw ----------------
    void draw(Canvas c) {
        if (state == MENU) drawMenu(c);
        else if (state == SIM && evo != null) drawSim(c);
        else if (state == EDIT && editor != null) drawEditor(c);
    }

    void drawMenu(Canvas c) {
        ui.background(c, W, H);
        ui.text(c, "AI LEARN TO DRIVE", W / 2f, 34 * d, 28 * d, 0xFFFFFFFF, Paint.Align.CENTER);
        ui.text(c, "Neuroevolution Driving Simulator  v" + VERSION, W / 2f, 58 * d, 13 * d, 0xFF90A4AE, Paint.Align.CENTER);
        for (UIManager.Btn b : menuB) ui.drawBtn(c, b);
    }

    void drawSim(Canvas c) {
        c.drawColor(0xFF2E5E2E);
        Track t = evo.track;
        c.save();
        cam.apply(c);
        t.draw(c, cam, paint);
        Car leader = evo.getLeader();
        float m = GameSettings.rayLength;
        for (EvolutionManager.Population pop : evo.pops) {
            Bitmap bm = sprites.car(pop.id);
            for (Car car : pop.cars) {
                if (!cam.visible(car.x - m, car.y - m, car.x + m, car.y + m)) continue;
                car.draw(c, bm, carPaint, linePaint, car == leader);
            }
        }
        c.restore();
        drawHud(c, leader);
        if (paused) {
            c.drawColor(0xAA000000);
            ui.text(c, "PAUSED", W / 2f, 28 * d, 24 * d, 0xFFFFFFFF, Paint.Align.CENTER);
            for (UIManager.Btn b : pauseB) ui.drawBtn(c, b);
        }
    }

    void drawHud(Canvas c, Car leader) {
        float pw = Math.min(W * 0.62f, 340 * d);
        tmp.set(6 * d, 6 * d, 6 * d + pw, 54 * d);
        ui.panel(c, tmp, 0x99000000);
        ui.text(c, "Gen " + evo.generation + "   Pops " + evo.pops.size() + "   Alive " + evo.aliveCount() + "/" + evo.totalCars(),
            14 * d, 20 * d, 13 * d, 0xFFFFFFFF, Paint.Align.LEFT);
        ui.text(c, "Best " + (int) evo.bestFitnessNow() + "   Record " + (int) evo.globalBest + "   Time " + (evo.tick / 60) + "/" + GameSettings.genTimeLimit + "s",
            14 * d, 40 * d, 13 * d, 0xFFFFEB3B, Paint.Align.LEFT);
        if (showNet && leader != null) {
            float nw = Math.min(W * 0.45f, 330 * d), nh = Math.min(H * 0.36f, 180 * d);
            tmp.set(6 * d, 60 * d, 6 * d + nw, 60 * d + nh);
            ui.panel(c, tmp, 0xAA10141C);
            viz.net = leader.brain;
            viz.render(c, tmp.left, tmp.top, nw, nh);
        }
        drawMinimap(c, leader);
        for (UIManager.Btn b : hudB) {
            b.on = (b.id.equals("s0") && speedIdx == 0) || (b.id.equals("s1") && speedIdx == 1) || (b.id.equals("s2") && speedIdx == 2) || (b.id.equals("s3") && speedIdx == 3)
                || (b.id.equals("follow") && cam.mode == Camera2D.FOLLOW) || (b.id.equals("free") && cam.mode == Camera2D.FREE)
                || (b.id.equals("map") && cam.mode == Camera2D.OVERVIEW) || (b.id.equals("net") && showNet);
            if (b.id.equals("pause")) b.label = paused ? ">" : "II";
            ui.drawBtn(c, b);
        }
    }

    void drawMinimap(Canvas c, Car leader) {
        Track t = evo.track;
        float sz = Math.min(110 * d, H * 0.3f);
        tmp.set(W - sz - 6 * d, 6 * d, W - 6 * d, 6 * d + sz);
        ui.panel(c, tmp, 0x99000000);
        if (t.m < 3) return;
        float tw = Math.max(1, t.maxX - t.minX), th = Math.max(1, t.maxY - t.minY);
        float s = Math.min((sz - 12 * d) / tw, (sz - 12 * d) / th);
        float ox = tmp.centerX() - (t.minX + t.maxX) / 2f * s, oy = tmp.centerY() - (t.minY + t.maxY) / 2f * s;
        mmPath.reset();
        for (int k = 0; k < t.m; k += 2) {
            float px = ox + t.cx[k] * s, py = oy + t.cy[k] * s;
            if (k == 0) mmPath.moveTo(px, py); else mmPath.lineTo(px, py);
        }
        mmPath.close();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, t.width * s));
        paint.setColor(0xFF666670);
        c.drawPath(mmPath, paint);
        paint.setStyle(Paint.Style.FILL);
        for (EvolutionManager.Population pop : evo.pops)
            for (Car car : pop.cars) {
                if (!car.alive) continue;
                paint.setColor(car == leader ? 0xFFFFEB3B : 0xFF4FC3F7);
                c.drawCircle(ox + car.x * s, oy + car.y * s, car == leader ? 3.5f * d : 2f * d, paint);
            }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(ox + cam.left() * s, oy + cam.top() * s, ox + cam.right() * s, oy + cam.bottom() * s, paint);
    }

    void drawEditor(Canvas c) {
        editor.cam.setViewport(W, H);
        editor.draw(c, paint);
        for (UIManager.Btn b : editB) { if (b.id.equals("edel")) { b.on = editor.deleteMode; b.label = editor.deleteMode ? "Del: On" : "Del: Off"; } ui.drawBtn(c, b); }
        ui.text(c, "Nodes: " + editor.track.nodes.size() + "   Width: " + (int) editor.track.width
            + "   (tap = add node, drag node = move, drag empty = pan, pinch = zoom; min 3 nodes)", 10 * d, H - 14 * d, 11 * d, 0xFFFFFFFF, Paint.Align.LEFT);
    }

    // ---------------- touch ----------------
    void zoomGesture(float f) {
        if (state != SIM) return;
        if (cam.mode == Camera2D.FOLLOW) GameSettings.cameraZoom = Math.max(0.2f, Math.min(4f, GameSettings.cameraZoom * f));
        else if (cam.mode == Camera2D.FREE) cam.zoomBy(f);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        synchronized (lock) {
            int a = e.getActionMasked();
            float x = e.getX(), y = e.getY();
            if (lastError != null) { if (a == MotionEvent.ACTION_DOWN) lastError = null; return true; }
            if (state == EDIT) return editTouch(e, a, x, y);
            if (state == SIM && !paused) sgd.onTouchEvent(e);
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                    downX = lx = x; downY = ly = y; dragging = false; scaling = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (state != SIM || paused) break;
                    if (e.getPointerCount() > 1 || sgd.isInProgress()) { scaling = true; break; }
                    if (scaling) break;
                    if (!dragging && Math.abs(x - downX) + Math.abs(y - downY) > 14 * d) dragging = true;
                    if (dragging) { cam.mode = Camera2D.FREE; cam.pan(x - lx, y - ly); }
                    lx = x; ly = y;
                    break;
                case MotionEvent.ACTION_UP:
                    if (!dragging && !scaling) tap(x, y);
                    break;
            }
            return true;
        }
    }

    void tap(float x, float y) {
        if (state == MENU) {
            UIManager.Btn b = ui.hit(menuB, x, y);
            if (b == null) return;
            if (b.id.equals("start")) startSim(false, null);
            else if (b.id.equals("resume")) startSim(true, null);
            else if (b.id.equals("creator")) act.startActivity(new Intent(act, NeuralEditorActivity.class));
            else if (b.id.equals("editor")) openEditor();
            else if (b.id.equals("load")) showLoadBrain();
            else if (b.id.equals("settings")) SettingsDialog.show(act, sprites, null);
        } else if (state == SIM) {
            if (paused) {
                UIManager.Btn b = ui.hit(pauseB, x, y);
                if (b == null) return;
                if (b.id.equals("resumesim")) paused = false;
                else if (b.id.equals("tune")) SettingsDialog.showTuning(act);
                else if (b.id.equals("psettings")) SettingsDialog.show(act, sprites, null);
                else if (b.id.equals("export")) showExport();
                else if (b.id.equals("exit")) leaveSim();
                return;
            }
            UIManager.Btn b = ui.hit(hudB, x, y);
            if (b == null) return;
            if (b.id.equals("pause")) paused = !paused;
            else if (b.id.equals("s0")) speedIdx = 0;
            else if (b.id.equals("s1")) speedIdx = 1;
            else if (b.id.equals("s2")) speedIdx = 2;
            else if (b.id.equals("s3")) speedIdx = 3;
            else if (b.id.equals("follow")) cam.mode = Camera2D.FOLLOW;
            else if (b.id.equals("free")) cam.mode = Camera2D.FREE;
            else if (b.id.equals("map")) cam.mode = Camera2D.OVERVIEW;
            else if (b.id.equals("net")) showNet = !showNet;
        }
    }

    boolean editTouch(MotionEvent e, int a, float x, float y) {
        if (a == MotionEvent.ACTION_DOWN) { downBtn = ui.hit(editB, x, y); if (downBtn != null) return true; }
        if (downBtn != null) {
            if (a == MotionEvent.ACTION_UP) { if (ui.hit(editB, x, y) == downBtn) editAction(downBtn); downBtn = null; }
            else if (a == MotionEvent.ACTION_CANCEL) downBtn = null;
            return true;
        }
        return editor.onTouch(e);
    }

    // ---------------- actions ----------------
    Track loadActiveTrack() {
        if (GameSettings.currentTrack != null && GameSettings.currentTrack.length() > 0) {
            Track t = SaveManager.loadTrack(act, GameSettings.currentTrack);
            if (t != null) return t;
        }
        return Track.createDefault();
    }

    public void startSim(boolean resume, NeuralNetwork seed) {
        synchronized (lock) {
            Track t = loadActiveTrack();
            EvolutionManager em = new EvolutionManager(act, t);
            boolean ok = false;
            if (resume) ok = em.resume(SaveManager.loadCheckpoint(act));
            if (resume && !ok) toast("Checkpoint doesn't match current ray count - starting fresh");
            if (!ok) {
                NeuralNetwork saved = (seed == null && GameSettings.useCustomBrain) ? SaveManager.loadCustomBrain(act) : null;
                EvolutionManager.SeedResolution r = EvolutionManager.resolveSeed(seed, GameSettings.useCustomBrain, saved, GameSettings.inputCount(), GameSettings.rayCount);
                if (r.message != null) toast(r.message);
                if (r.rayCount != GameSettings.rayCount) GameSettings.rayCount = r.rayCount;
                em.startFresh(r.seed);
            }
            evo = em;
            cam.mode = Camera2D.FOLLOW;
            cam.zoom = GameSettings.cameraZoom;
            cam.snapTo(t.startX, t.startY);
            paused = false; speedIdx = 0; state = SIM;
        }
    }

    void openEditor() {
        editor = new TrackEditor(act, loadActiveTrack());
        state = EDIT;
    }

    void toast(final String s) {
        act.runOnUiThread(new Runnable() { public void run() { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); } });
    }

    void prompt(String title, String def, final StrCb cb) {
        final EditText et = new EditText(act);
        et.setText(def);
        new AlertDialog.Builder(act).setTitle(title).setView(et)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int w) { cb.got(et.getText().toString().trim()); } })
            .setNegativeButton("Cancel", null).show();
    }

    void editAction(UIManager.Btn b) {
        Track t = editor.track;
        String id = b.id;
        if (id.equals("ebk")) { state = MENU; layoutUi(); }
        else if (id.equals("eundo")) { if (!t.nodes.isEmpty()) t.nodes.remove(t.nodes.size() - 1); t.build(); }
        else if (id.equals("eclr")) { t.nodes.clear(); t.build(); }
        else if (id.equals("ew-")) { t.width = Math.max(40f, t.width - 10f); t.build(); }
        else if (id.equals("ew+")) { t.width = Math.min(300f, t.width + 10f); t.build(); }
        else if (id.equals("edel")) editor.deleteMode = !editor.deleteMode;
        else if (id.equals("esave")) {
            if (t.nodes.size() < 3) { toast("Need at least 3 nodes"); return; }
            prompt("Save track as", GameSettings.currentTrack.length() > 0 ? GameSettings.currentTrack : "my_track", new StrCb() {
                public void got(String s) {
                    if (s.length() == 0) return;
                    String name = s.replaceAll("[^A-Za-z0-9_\\-]", "_");
                    synchronized (lock) {
                        editor.track.name = name;
                        try { SaveManager.saveTrack(act, name, editor.track.toJson()); GameSettings.currentTrack = name; GameSettings.save(); toast("Saved & selected: " + name); }
                        catch (Exception ex) { toast("Save failed: " + ex.getMessage()); }
                    }
                } });
        } else if (id.equals("eload")) {
            final String[] names = SaveManager.listTracks(act);
            final String[] items = new String[names.length + 1];
            items[0] = "[Built-in default track]";
            System.arraycopy(names, 0, items, 1, names.length);
            new AlertDialog.Builder(act).setTitle("Load track").setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int i) {
                    synchronized (lock) {
                        if (i == 0) { editor = new TrackEditor(act, Track.createDefault()); GameSettings.currentTrack = ""; }
                        else { Track tr = SaveManager.loadTrack(act, names[i - 1]); if (tr == null) { toast("Could not load track"); return; } editor = new TrackEditor(act, tr); GameSettings.currentTrack = names[i - 1]; }
                        GameSettings.save();
                    }
                } }).show();
        }
    }

    void showLoadBrain() {
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<File> files = new ArrayList<File>();
        labels.add("Best autosave"); files.add(SaveManager.bestFile(act));
        labels.add("Custom brain from creator"); files.add(SaveManager.customBrainFile(act));
        for (File f : SaveManager.listBrains(act)) { labels.add(f.getName()); files.add(f); }
        labels.add("Paste from clipboard"); files.add(null);
        new AlertDialog.Builder(act).setTitle("Load Trained Brain").setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dlg, int i) {
                try {
                    String json = files.get(i) == null ? SaveManager.paste(act) : SaveManager.readFile(files.get(i));
                    NeuralNetwork n = NeuralNetwork.fromJson(json);
                    if (n.sizes[n.sizes.length - 1] != 4) { toast("Brain needs 4 outputs (has " + n.sizes[n.sizes.length - 1] + ")"); return; }
                    int rays = n.sizes[0] - 2;
                    if (rays < 1 || rays > 13) { toast("Brain input count unsupported (needs 1-13 rays + 2)"); return; }
                    GameSettings.rayCount = rays;
                    startSim(false, n);
                } catch (Exception ex) { toast("Could not load: " + ex.getMessage()); }
            } }).show();
    }

    void showExport() {
        if (evo == null) return;
        Car l = evo.getLeader();
        final NeuralNetwork best = evo.globalBestBrain != null ? evo.globalBestBrain : (l != null ? l.brain : null);
        if (best == null) { toast("Nothing to export yet"); return; }
        final EditText et = new EditText(act);
        et.setText("brain_gen" + evo.generation);
        new AlertDialog.Builder(act).setTitle("Export best brain").setView(et)
            .setPositiveButton("Save to Documents", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int w) {
                    String p = SaveManager.exportBrain(act, et.getText().toString(), best.toJson());
                    toast(p == null ? "Export failed" : "Saved to " + p);
                } })
            .setNeutralButton("Copy to Clipboard", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int w) { SaveManager.copyToClipboard(act, best.toJson()); toast("Copied to clipboard"); } })
            .setNegativeButton("Cancel", null).show();
    }
}

package com.ailearn.drive;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.*;

/** Settings / tuning / sprite-manager dialogs + reusable slider helper. */
public class SettingsDialog {
    public interface OnChange { void changed(int v); }

    public static SeekBar addSlider(Context ctx, LinearLayout parent, final String label, final int min, final int max, int value, final OnChange cb) {
        final TextView tv = new TextView(ctx);
        tv.setText(label + ": " + value);
        tv.setPadding(0, 14, 0, 0);
        parent.addView(tv);
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { tv.setText(label + ": " + (p + min)); if (user) cb.changed(p + min); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        parent.addView(sb);
        return sb;
    }

    static CheckBox addCheck(Context ctx, LinearLayout parent, String label, boolean val, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(ctx);
        cb.setText(label);
        cb.setChecked(val);
        cb.setOnCheckedChangeListener(l);
        parent.addView(cb);
        return cb;
    }

    static LinearLayout box(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(32, 16, 32, 16);
        return l;
    }

    /** sprites may be null (e.g. from the creator activity). */
    public static void show(final Context ctx, final SpriteManager sprites, final Runnable onClose) {
        ScrollView sv = new ScrollView(ctx);
        LinearLayout l = box(ctx);
        sv.addView(l);
        addCheck(ctx, l, "Show vehicle sensors (rays)", GameSettings.showSensors, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { GameSettings.showSensors = v; } });
        addCheck(ctx, l, "Show node names (visualizer)", GameSettings.showNodeNames, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { GameSettings.showNodeNames = v; } });
        addCheck(ctx, l, "Show weight values (visualizer)", GameSettings.showWeightValues, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { GameSettings.showWeightValues = v; } });
        addCheck(ctx, l, "Auto-save progress every generation", GameSettings.autoSaveProgress, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { GameSettings.autoSaveProgress = v; } });
        addSlider(ctx, l, "Camera zoom (x100)", 20, 300, Math.round(GameSettings.cameraZoom * 100), new OnChange() {
            public void changed(int v) { GameSettings.cameraZoom = v / 100f; } });
        if (sprites != null) {
            Button sb = new Button(ctx);
            sb.setText("Sprite Manager");
            sb.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) { showSprites(ctx, sprites); } });
            l.addView(sb);
        }
        AlertDialog dlg = new AlertDialog.Builder(ctx).setTitle("Settings").setView(sv).setPositiveButton("Done", null).create();
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface d) { GameSettings.save(); if (onClose != null) onClose.run(); } });
        dlg.show();
    }

    /** On-the-fly evolution tuning (pause menu). */
    public static void showTuning(Context ctx) {
        ScrollView sv = new ScrollView(ctx);
        LinearLayout l = box(ctx);
        sv.addView(l);
        addSlider(ctx, l, "Mutation rate %", 0, 100, Math.round(GameSettings.mutationRate * 100), new OnChange() {
            public void changed(int v) { GameSettings.mutationRate = v / 100f; } });
        addSlider(ctx, l, "Mutation intensity %", 1, 100, Math.round(GameSettings.mutationIntensity * 100), new OnChange() {
            public void changed(int v) { GameSettings.mutationIntensity = v / 100f; } });
        addSlider(ctx, l, "Crossover (breeding) rate %", 0, 100, Math.round(GameSettings.crossoverRate * 100), new OnChange() {
            public void changed(int v) { GameSettings.crossoverRate = v / 100f; } });
        addSlider(ctx, l, "Checkpoint every N generations", 1, 100, GameSettings.checkpointEvery, new OnChange() {
            public void changed(int v) { GameSettings.checkpointEvery = v; } });
        AlertDialog dlg = new AlertDialog.Builder(ctx).setTitle("Evolution Tuning").setView(sv).setPositiveButton("Done", null).create();
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface d) { GameSettings.save(); } });
        dlg.show();
    }

    static void showSprites(final Context ctx, final SpriteManager sp) {
        StringBuilder sb = new StringBuilder();
        for (String n : SpriteManager.ASSET_NAMES) sb.append(sp.exists(n) ? "[OK]  " : "[--]  ").append(n).append(".png\n");
        sb.append("\n[--] = not found, a generated fallback is used.\n")
          .append("Car sprites: transparent PNG, car facing RIGHT, about 2:1 wide (e.g. 128x64).\n")
          .append("Population 1/2/3/4 use car_rally / car_sports / car_taxi / car_truck.\n")
          .append("menu_bg.png and btn_<id>.png (e.g. btn_start.png) are optional.\n\n")
          .append("Put PNGs in the project's assets/sprites/ folder and rebuild, or copy them to:\n")
          .append(ctx.getFilesDir()).append("/sprites/\nor Documents/AiDriveSprites/ (needs storage permission).");
        new AlertDialog.Builder(ctx).setTitle("Sprite Manager").setMessage(sb.toString())
            .setPositiveButton("Reload", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { sp.clear(); Toast.makeText(ctx, "Sprites reloaded", Toast.LENGTH_SHORT).show(); } })
            .setNegativeButton("Close", null).show();
    }
}

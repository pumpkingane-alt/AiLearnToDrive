package com.ailearn.drive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/** Dual-tab neural network creator: Basic (sliders) and Advanced (raw JSON + live validation + floating error log). */
public class NeuralEditorActivity extends Activity {
    private NeuralVisualizerView viz;
    private EditText code;
    private TextView status, floatLog;
    private ScrollView basicPanel;
    private LinearLayout advPanel;
    private Button tabBasic, tabAdv;
    private NeuralNetwork net;
    private boolean suppress;
    private final ArrayList<String> log = new ArrayList<String>();
    private final Handler handler = new Handler();
    private float phase;
    private final Runnable liveTick = new Runnable() {
        public void run() {
            if (net != null) {
                float[] in = new float[net.sizes[0]];
                for (int i = 0; i < in.length; i++) in[i] = 0.5f + 0.5f * (float) Math.sin(phase + i * 0.9f);
                phase += 0.15f;
                net.forward(in);
                viz.setNetwork(net);
            }
            handler.postDelayed(this, 150);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        GameSettings.load(this);
        net = initialNet();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF10141C);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout tabs = new LinearLayout(this);
        tabBasic = btn("Basic Mode", new View.OnClickListener() { public void onClick(View v) { showTab(0); } });
        tabAdv = btn("Advanced Mode", new View.OnClickListener() { public void onClick(View v) { showTab(1); } });
        tabs.addView(tabBasic, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(tabAdv, new LinearLayout.LayoutParams(0, -2, 1));
        main.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        viz = new NeuralVisualizerView(this);
        main.addView(viz, new LinearLayout.LayoutParams(-1, 0, 0.42f));
        FrameLayout content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(-1, 0, 0.58f));
        buildBasic();
        buildAdvanced();
        content.addView(basicPanel, new FrameLayout.LayoutParams(-1, -1));
        content.addView(advPanel, new FrameLayout.LayoutParams(-1, -1));

        floatLog = new TextView(this);
        floatLog.setBackgroundColor(0xEE2B0A0A);
        floatLog.setTextColor(0xFFFF5252);
        floatLog.setTypeface(Typeface.MONOSPACE);
        floatLog.setTextSize(12);
        floatLog.setPadding(20, 14, 20, 14);
        floatLog.setVisibility(View.GONE);
        floatLog.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { floatLog.setVisibility(View.GONE); } });
        FrameLayout.LayoutParams lpf = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lpf.setMargins(12, 0, 12, 12);
        root.addView(floatLog, lpf);

        setContentView(root);
        showTab(0);
        setNet(net, true);
    }

    private NeuralNetwork initialNet() {
        if (GameSettings.useCustomBrain) {
            NeuralNetwork n = SaveManager.loadCustomBrain(this);
            if (n != null) return n;
        }
        return randomNet();
    }

    private NeuralNetwork randomNet() {
        return new NeuralNetwork(NeuralNetwork.topology(GameSettings.inputCount(), GameSettings.hiddenLayers, GameSettings.neuronsPerLayer, 4));
    }

    @Override protected void onResume() { super.onResume(); handler.post(liveTick); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(liveTick); GameSettings.save(); }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void showTab(int t) {
        basicPanel.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        advPanel.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        tabBasic.setAlpha(t == 0 ? 1f : 0.5f);
        tabAdv.setAlpha(t == 1 ? 1f : 0.5f);
    }

    // ---------------- Tab 1: Basic ----------------
    private void buildBasic() {
        basicPanel = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(24, 8, 24, 24);
        basicPanel.addView(l);
        SettingsDialog.addSlider(this, l, "Sensor ray count", 1, 13, GameSettings.rayCount, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.rayCount = v; rebuild(); } });
        SettingsDialog.addSlider(this, l, "Ray length", 50, 400, (int) GameSettings.rayLength, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.rayLength = v; } });
        SettingsDialog.addSlider(this, l, "Ray spread arc (degrees)", 30, 360, (int) GameSettings.raySpread, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.raySpread = v; } });
        SettingsDialog.addSlider(this, l, "Hidden layers", 1, 10, GameSettings.hiddenLayers, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.hiddenLayers = v; rebuild(); } });
        SettingsDialog.addSlider(this, l, "Neurons per layer", 1, 16, GameSettings.neuronsPerLayer, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.neuronsPerLayer = v; rebuild(); } });
        SettingsDialog.addSlider(this, l, "Population count", 1, 10, GameSettings.populationCount, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.populationCount = v; } });
        SettingsDialog.addSlider(this, l, "Max cars (population size, 100 max)", 1, 100, GameSettings.maxCars, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.maxCars = v; } });
        SettingsDialog.addSlider(this, l, "Mutation rate %", 0, 100, Math.round(GameSettings.mutationRate * 100), new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.mutationRate = v / 100f; } });
        SettingsDialog.addSlider(this, l, "Mutation intensity %", 1, 100, Math.round(GameSettings.mutationIntensity * 100), new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.mutationIntensity = v / 100f; } });
        SettingsDialog.addSlider(this, l, "Breeding / crossover rate %", 0, 100, Math.round(GameSettings.crossoverRate * 100), new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.crossoverRate = v / 100f; } });
        SettingsDialog.addSlider(this, l, "Generation time limit (s)", 5, 180, GameSettings.genTimeLimit, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.genTimeLimit = v; } });
        SettingsDialog.addSlider(this, l, "Checkpoint every N generations", 1, 100, GameSettings.checkpointEvery, new SettingsDialog.OnChange() {
            public void changed(int v) { GameSettings.checkpointEvery = v; } });
        LinearLayout row = new LinearLayout(this);
        row.addView(btn("Randomize", new View.OnClickListener() { public void onClick(View v) { setNet(randomNet(), true); } }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(btn("Use This Brain", new View.OnClickListener() { public void onClick(View v) { useBrain(); } }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(btn("Clear Custom", new View.OnClickListener() { public void onClick(View v) {
            GameSettings.useCustomBrain = false; GameSettings.save(); toast("Simulations will start from random brains"); } }), new LinearLayout.LayoutParams(0, -2, 1));
        l.addView(row);
        TextView hint = new TextView(this);
        hint.setText("Changing rays / layers / neurons generates a new random network (overwrites JSON edits).");
        hint.setTextSize(12);
        l.addView(hint);
    }

    private void rebuild() { setNet(randomNet(), true); }

    private void useBrain() {
        String err = NeuralNetwork.dimError(net, net.sizes[0]);
        if (net.sizes[net.sizes.length - 1] != 4) { toast(err); return; }
        int rays = net.sizes[0] - 2;
        if (rays < 1 || rays > 13) { toast("Layer 1 must take 3 to 15 inputs (1-13 rays + speed + angle)"); return; }
        try {
            GameSettings.rayCount = rays;
            GameSettings.useCustomBrain = true;
            SaveManager.saveCustomBrain(this, net.toJson());
            GameSettings.save();
            toast("Saved. New simulations start from this brain (" + rays + " rays).");
        } catch (Exception e) { toast("Save failed: " + e.getMessage()); }
    }

    // ---------------- Tab 2: Advanced ----------------
    private void buildAdvanced() {
        advPanel = new LinearLayout(this);
        advPanel.setOrientation(LinearLayout.VERTICAL);
        advPanel.setPadding(12, 4, 12, 4);
        status = new TextView(this);
        status.setTextSize(12);
        status.setTypeface(Typeface.MONOSPACE);
        advPanel.addView(status);
        code = new EditText(this);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(11);
        code.setGravity(Gravity.TOP | Gravity.LEFT);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { if (!suppress) validate(s.toString()); }
        });
        advPanel.addView(code, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.addView(btn("Save File", new View.OnClickListener() { public void onClick(View v) { saveFile(); } }), new LinearLayout.LayoutParams(0, -2, 1));
        r1.addView(btn("Copy", new View.OnClickListener() { public void onClick(View v) {
            SaveManager.copyToClipboard(NeuralEditorActivity.this, code.getText().toString()); toast("Copied to clipboard"); } }), new LinearLayout.LayoutParams(0, -2, 1));
        r1.addView(btn("Paste", new View.OnClickListener() { public void onClick(View v) { code.setText(SaveManager.paste(NeuralEditorActivity.this)); } }), new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(btn("Import File", new View.OnClickListener() { public void onClick(View v) { importFile(); } }), new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(btn("Load Best", new View.OnClickListener() { public void onClick(View v) {
            NeuralNetwork n = SaveManager.loadAutoBest(NeuralEditorActivity.this);
            if (n == null) toast("No autosaved brain yet"); else setNet(n, true); } }), new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(btn("Use Brain", new View.OnClickListener() { public void onClick(View v) { useBrain(); } }), new LinearLayout.LayoutParams(0, -2, 1));
        advPanel.addView(r1);
        advPanel.addView(r2);
    }

    private void setNet(NeuralNetwork n, boolean updateText) {
        net = n;
        viz.setNetwork(n);
        if (updateText) {
            suppress = true;
            code.setText(n.toJson());
            suppress = false;
            showError(NeuralNetwork.dimError(n, GameSettings.inputCount()), n);
        }
    }

    /** Live syntax + dimension validation; a valid structure instantly updates the visualizer. */
    private void validate(String s) {
        try {
            NeuralNetwork n = NeuralNetwork.fromJson(s);
            net = n;
            viz.setNetwork(n);
            showError(NeuralNetwork.dimError(n, GameSettings.inputCount()), n);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage(), null);
        }
    }

    private void showError(String msg, NeuralNetwork ok) {
        if (msg == null) {
            status.setTextColor(0xFF66BB6A);
            status.setText("Valid network " + Arrays.toString(ok.sizes));
            floatLog.setVisibility(View.GONE);
            return;
        }
        status.setTextColor(0xFFFF5252);
        status.setText("ERROR: " + msg);
        String line = new SimpleDateFormat("HH:mm:ss").format(new Date()) + "  " + msg;
        if (log.isEmpty() || !log.get(log.size() - 1).endsWith(msg)) log.add(line);
        while (log.size() > 4) log.remove(0);
        StringBuilder sb = new StringBuilder("DEBUG LOG (tap to hide)\n");
        for (String x : log) sb.append(x).append('\n');
        floatLog.setText(sb.toString().trim());
        floatLog.setVisibility(View.VISIBLE);
    }

    private void saveFile() {
        final EditText et = new EditText(this);
        et.setText("my_brain");
        new AlertDialog.Builder(this).setTitle("Save brain as").setView(et)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String p = SaveManager.exportBrain(NeuralEditorActivity.this, et.getText().toString(), code.getText().toString());
                    toast(p == null ? "Save failed" : "Saved to " + p);
                } })
            .setNegativeButton("Cancel", null).show();
    }

    private void importFile() {
        final ArrayList<File> files = SaveManager.listBrains(this);
        if (files.isEmpty()) { toast("No files in Documents/AiDriveBrains (or app folder)"); return; }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Import brain").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int i) {
                try { code.setText(SaveManager.readFile(files.get(i))); } catch (Exception e) { toast("Read failed: " + e.getMessage()); }
            } }).show();
    }
}

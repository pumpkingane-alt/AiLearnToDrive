package com.ailearn.drive;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/** Long-term storage: autosave best brain, population checkpoints, exports, imports, tracks, clipboard. */
public class SaveManager {
    public static class Checkpoint {
        public int generation;
        public float best;
        public ArrayList<ArrayList<NeuralNetwork>> brains = new ArrayList<ArrayList<NeuralNetwork>>();
    }

    public static void writeFile(File f, String s) throws IOException {
        FileOutputStream o = new FileOutputStream(f);
        try { o.write(s.getBytes("UTF-8")); } finally { o.close(); }
    }

    /** Write to temp then rename so a crash/power loss never corrupts a long training run. */
    public static void writeAtomic(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        writeFile(tmp, s);
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) writeFile(f, s);
    }

    public static String readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally { in.close(); }
    }

    // ---- auto-save best brain ----
    public static File bestFile(Context c) { return new File(c.getFilesDir(), "best_brain_autosave.json"); }

    public static void autoSaveBest(Context c, NeuralNetwork n, int gen, float fit) {
        try {
            writeAtomic(bestFile(c), n.toJson());
            c.getSharedPreferences("ai_drive_meta", 0).edit().putInt("best_gen", gen).putFloat("best_fit", fit).commit();
        } catch (Exception e) { }
    }

    public static NeuralNetwork loadAutoBest(Context c) {
        try { return NeuralNetwork.fromJson(readFile(bestFile(c))); } catch (Exception e) { return null; }
    }

    // ---- population checkpoint ----
    public static File checkpointFile(Context c) { return new File(c.getFilesDir(), "checkpoint.json"); }
    public static boolean hasCheckpoint(Context c) { return checkpointFile(c).exists(); }
    public static int checkpointGen(Context c) { return c.getSharedPreferences("ai_drive_meta", 0).getInt("cp_gen", 0); }

    /** Permanently remove the stored population checkpoint. Only call after explicit user consent,
     *  since this discards a (possibly long-running) training lineage that cannot be recovered.
     *  Returns true iff the checkpoint file was removed; the cp_gen pref is cleared only on success,
     *  so a failed delete leaves the checkpoint resumable for the caller to report. */
    public static boolean deleteCheckpoint(Context c) {
        boolean ok = false;
        try { ok = checkpointFile(c).delete(); } catch (Exception e) { }
        if (ok) c.getSharedPreferences("ai_drive_meta", 0).edit().remove("cp_gen").commit();
        return ok;
    }

    public static void saveCheckpoint(Context c, EvolutionManager em) {
        try {
            StringBuilder sb = new StringBuilder("{\"generation\":" + em.generation + ",\"best\":" + em.globalBest + ",\"pops\":[");
            for (int p = 0; p < em.pops.size(); p++) {
                if (p > 0) sb.append(',');
                sb.append('[');
                ArrayList<Car> cs = em.pops.get(p).cars;
                for (int i = 0; i < cs.size(); i++) { if (i > 0) sb.append(','); sb.append(cs.get(i).brain.toJson()); }
                sb.append(']');
            }
            sb.append("]}");
            writeAtomic(checkpointFile(c), sb.toString());
            c.getSharedPreferences("ai_drive_meta", 0).edit().putInt("cp_gen", em.generation).commit();
        } catch (Exception e) { }
    }

    public static Checkpoint loadCheckpoint(Context c) {
        try {
            JSONObject o = new JSONObject(readFile(checkpointFile(c)));
            Checkpoint cp = new Checkpoint();
            cp.generation = o.getInt("generation");
            cp.best = (float) o.optDouble("best", 0);
            JSONArray pops = o.getJSONArray("pops");
            for (int p = 0; p < pops.length(); p++) {
                JSONArray a = pops.getJSONArray(p);
                ArrayList<NeuralNetwork> list = new ArrayList<NeuralNetwork>();
                for (int i = 0; i < a.length(); i++) list.add(NeuralNetwork.fromJson(a.getJSONObject(i).toString()));
                cp.brains.add(list);
            }
            return cp;
        } catch (Exception e) { return null; }
    }

    // ---- custom brain from creator ----
    public static File customBrainFile(Context c) { return new File(c.getFilesDir(), "custom_brain.json"); }
    public static void saveCustomBrain(Context c, String json) throws IOException { writeAtomic(customBrainFile(c), json); }
    public static NeuralNetwork loadCustomBrain(Context c) {
        try { return NeuralNetwork.fromJson(readFile(customBrainFile(c))); } catch (Exception e) { return null; }
    }

    // ---- manual export / import (Documents/AiDriveBrains) ----
    public static File brainDir() { return new File(Environment.getExternalStorageDirectory(), "Documents/AiDriveBrains"); }
    public static File fallbackBrainDir(Context c) { return new File(c.getExternalFilesDir(null), "AiDriveBrains"); }

    /** Returns saved path or null on failure. */
    public static String exportBrain(Context c, String name, String json) {
        name = name.replaceAll("[^A-Za-z0-9_\\-]", "_");
        File[] dirs = {brainDir(), fallbackBrainDir(c)};
        for (File d : dirs) {
            try {
                d.mkdirs();
                File f = new File(d, name + ".json");
                writeFile(f, json);
                return f.getAbsolutePath();
            } catch (Exception e) { }
        }
        return null;
    }

    public static ArrayList<File> listBrains(Context c) {
        ArrayList<File> out = new ArrayList<File>();
        File[] dirs = {brainDir(), fallbackBrainDir(c)};
        for (File d : dirs) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            Arrays.sort(fs);
            for (File f : fs) if (f.getName().endsWith(".json")) out.add(f);
        }
        return out;
    }

    // ---- clipboard ----
    public static void copyToClipboard(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("brain", text));
    }

    public static String paste(Context c) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
        return cs == null ? "" : cs.toString();
    }

    // ---- tracks ----
    public static File trackDir(Context c) { File d = new File(c.getFilesDir(), "tracks"); d.mkdirs(); return d; }

    public static void saveTrack(Context c, String name, String json) throws IOException {
        writeAtomic(new File(trackDir(c), name.replaceAll("[^A-Za-z0-9_\\-]", "_") + ".json"), json);
    }

    public static Track loadTrack(Context c, String name) {
        try {
            Track t = Track.fromJson(readFile(new File(trackDir(c), name + ".json")));
            return t.nodes.size() < 3 ? null : t;
        } catch (Exception e) { return null; }
    }

    public static String[] listTracks(Context c) {
        File[] fs = trackDir(c).listFiles();
        ArrayList<String> l = new ArrayList<String>();
        if (fs != null) { Arrays.sort(fs); for (File f : fs) if (f.getName().endsWith(".json")) l.add(f.getName().substring(0, f.getName().length() - 5)); }
        return l.toArray(new String[0]);
    }
}

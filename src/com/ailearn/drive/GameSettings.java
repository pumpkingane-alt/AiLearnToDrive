package com.ailearn.drive;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent options (SharedPreferences) + evolution/sensor parameters. */
public class GameSettings {
    public static boolean showSensors = true, showNodeNames = true, showWeightValues = false, autoSaveProgress = true;
    public static float cameraZoom = 1.0f;
    public static int populationCount = 1, maxCars = 40, rayCount = 5, hiddenLayers = 2, neuronsPerLayer = 8;
    public static int checkpointEvery = 10, genTimeLimit = 40;
    public static float rayLength = 180f, raySpread = 120f;
    public static float mutationRate = 0.10f, mutationIntensity = 0.30f, crossoverRate = 0.70f;
    public static boolean useCustomBrain = false;
    public static String currentTrack = "";
    private static boolean loaded = false;
    private static Context app;

    /** inputs = rays + speed + steering angle */
    public static int inputCount() { return rayCount + 2; }

    public static void load(Context c) {
        if (loaded) return;
        loaded = true;
        app = c.getApplicationContext();
        SharedPreferences s = app.getSharedPreferences("ai_drive_settings", 0);
        showSensors = s.getBoolean("showSensors", showSensors);
        showNodeNames = s.getBoolean("showNodeNames", showNodeNames);
        showWeightValues = s.getBoolean("showWeightValues", showWeightValues);
        autoSaveProgress = s.getBoolean("autoSaveProgress", autoSaveProgress);
        cameraZoom = clampFloat(s, "cameraZoom", cameraZoom, 0.2f, 4f);
        populationCount = Math.max(1, Math.min(10, s.getInt("populationCount", populationCount)));
        maxCars = Math.max(1, Math.min(100, s.getInt("maxCars", maxCars)));
        rayCount = Math.max(1, Math.min(13, s.getInt("rayCount", rayCount)));
        hiddenLayers = Math.max(1, Math.min(10, s.getInt("hiddenLayers", hiddenLayers)));
        neuronsPerLayer = Math.max(1, Math.min(16, s.getInt("neuronsPerLayer", neuronsPerLayer)));
        checkpointEvery = Math.max(1, Math.min(100, s.getInt("checkpointEvery", checkpointEvery)));
        genTimeLimit = Math.max(5, Math.min(180, s.getInt("genTimeLimit", genTimeLimit)));
        rayLength = clampFloat(s, "rayLength", rayLength, 50f, 400f);
        raySpread = clampFloat(s, "raySpread", raySpread, 30f, 360f);
        mutationRate = clampFloat(s, "mutationRate", mutationRate, 0f, 1f);
        mutationIntensity = clampFloat(s, "mutationIntensity", mutationIntensity, 0.01f, 1f);
        crossoverRate = clampFloat(s, "crossoverRate", crossoverRate, 0f, 1f);
        useCustomBrain = s.getBoolean("useCustomBrain", useCustomBrain);
        currentTrack = s.getString("currentTrack", currentTrack);
    }

    /** Loads a float preference, replacing NaN with {@code def} before clamping to [{@code min}, {@code max}]. */
    private static float clampFloat(SharedPreferences s, String key, float def, float min, float max) {
        float v = s.getFloat(key, def);
        if (Float.isNaN(v)) v = def;
        return Math.max(min, Math.min(max, v));
    }

    public static void save() {
        if (app == null) return;
        app.getSharedPreferences("ai_drive_settings", 0).edit()
            .putBoolean("showSensors", showSensors).putBoolean("showNodeNames", showNodeNames)
            .putBoolean("showWeightValues", showWeightValues).putBoolean("autoSaveProgress", autoSaveProgress)
            .putFloat("cameraZoom", cameraZoom).putInt("populationCount", populationCount)
            .putInt("maxCars", maxCars).putInt("rayCount", rayCount).putInt("hiddenLayers", hiddenLayers)
            .putInt("neuronsPerLayer", neuronsPerLayer).putInt("checkpointEvery", checkpointEvery)
            .putInt("genTimeLimit", genTimeLimit).putFloat("rayLength", rayLength).putFloat("raySpread", raySpread)
            .putFloat("mutationRate", mutationRate).putFloat("mutationIntensity", mutationIntensity)
            .putFloat("crossoverRate", crossoverRate).putBoolean("useCustomBrain", useCustomBrain)
            .putString("currentTrack", currentTrack).commit();
    }
}

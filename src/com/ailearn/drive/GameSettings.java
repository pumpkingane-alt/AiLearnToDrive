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
        cameraZoom = Math.max(0.2f, Math.min(4f, s.getFloat("cameraZoom", cameraZoom)));
        populationCount = Math.max(1, Math.min(10, s.getInt("populationCount", populationCount)));
        maxCars = Math.max(1, Math.min(100, s.getInt("maxCars", maxCars)));
        rayCount = Math.max(1, Math.min(13, s.getInt("rayCount", rayCount)));
        hiddenLayers = Math.max(1, Math.min(10, s.getInt("hiddenLayers", hiddenLayers)));
        neuronsPerLayer = Math.max(1, Math.min(16, s.getInt("neuronsPerLayer", neuronsPerLayer)));
        checkpointEvery = Math.max(1, Math.min(100, s.getInt("checkpointEvery", checkpointEvery)));
        genTimeLimit = Math.max(5, Math.min(180, s.getInt("genTimeLimit", genTimeLimit)));
        rayLength = Math.max(50f, Math.min(400f, s.getFloat("rayLength", rayLength)));
        raySpread = Math.max(30f, Math.min(360f, s.getFloat("raySpread", raySpread)));
        mutationRate = Math.max(0f, Math.min(1f, s.getFloat("mutationRate", mutationRate)));
        mutationIntensity =
            Math.max(0.01f, Math.min(1f, s.getFloat("mutationIntensity", mutationIntensity)));
        crossoverRate = Math.max(0f, Math.min(1f, s.getFloat("crossoverRate", crossoverRate)));
        useCustomBrain = s.getBoolean("useCustomBrain", useCustomBrain);
        currentTrack = s.getString("currentTrack", currentTrack);
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

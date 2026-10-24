package com.ailearn.drive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {
    private GameView gv;

    private static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (CrashLog.read(this) != null) { // unresolved crash: show it instead of crash-looping
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
            return;
        }
        try {
            start(b);
        } catch (Throwable e) {
            CrashLog.write(this, "MainActivity.onCreate", e);
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
        }
    }

    private void start(Bundle b) {
        CrashLog.mark(this, "main_create");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        GameSettings.load(this);
        gv = new GameView(this);
        setContentView(gv);
        CrashLog.mark(this, "view_ready");
        if (b == null && SaveManager.hasCheckpoint(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Previous training found")
                .setMessage("Resume long-term training or start a fresh population?")
                .setPositiveButton("Resume (Gen " + SaveManager.checkpointGen(this) + ")", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { gv.startSim(true, null); } })
                .setNegativeButton("Start Fresh", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { gv.startFreshWithConsent(null); } })
                .setNeutralButton("Menu", null)
                .show();
        }
    }

    @Override protected void onPause() { super.onPause(); if (gv != null) gv.onHostPause(); }
    @Override protected void onResume() { super.onResume(); if (gv != null) gv.onHostResume(); }

    @Override public void onBackPressed() { if (gv == null || !gv.onBack()) super.onBackPressed(); }
}

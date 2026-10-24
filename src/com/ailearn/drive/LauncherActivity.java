package com.ailearn.drive;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

/** Tiny launcher: starts the game, or shows what went wrong last time (Java crash text, or the stage a silent crash died at). */
public class LauncherActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String crash = CrashLog.read(this);
        String stage = CrashLog.lastStage(this);
        boolean silentDeath = crash == null && stage != null && !CrashLog.OK.equals(stage);
        if (crash == null && !silentDeath) {
            CrashLog.mark(this, "launcher");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        final String err = crash != null ? crash
            : "No Java error was recorded, but the last launch stopped at stage: " + stage
              + "\n\nThat means the process was killed without an exception (native crash, out-of-memory kill, or the system closed it)."
              + "\nStages in order: launcher > main_create > view_ready > surface_created > ok (first frame drawn)."
              + "\nApp version " + GameView.VERSION + ", Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + "), "
              + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(0xFF000000);
        l.setPadding(16, 16, 16, 16);
        TextView title = new TextView(this);
        title.setText("The game crashed last time (v" + GameView.VERSION + "). Copy the text below and send it:");
        title.setTextColor(0xFFFFFFFF);
        l.addView(title);
        Button copy = new Button(this);
        copy.setText("Copy error");
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("crash", err));
                Toast.makeText(LauncherActivity.this, "Copied", Toast.LENGTH_SHORT).show();
            } });
        l.addView(copy);
        Button go = new Button(this);
        go.setText("Clear error and launch game");
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                CrashLog.clear(LauncherActivity.this);
                CrashLog.mark(LauncherActivity.this, "launcher");
                startActivity(new Intent(LauncherActivity.this, MainActivity.class));
                finish();
            } });
        l.addView(go);
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(err);
        tv.setTextColor(0xFFFF5252);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        l.addView(sv);
        setContentView(l);
    }
}

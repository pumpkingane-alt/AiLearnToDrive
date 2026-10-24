package com.ailearn.drive;

import android.content.Context;
import java.io.*;

/** Dependency-free crash log (uses only java.io) so it works even if everything else is broken.
 *  Also keeps a "stage" breadcrumb so a native crash / killed process (no Java exception) still leaves a clue. */
public class CrashLog {
    public static final String OK = "ok";

    public static File file(Context c) { return new File(c.getFilesDir(), "last_crash.txt"); }
    public static File stageFile(Context c) { return new File(c.getFilesDir(), "last_stage.txt"); }

    public static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static void write(Context c, String where, Throwable e) {
        try {
            FileOutputStream o = new FileOutputStream(file(c));
            try { o.write(("Where: " + where + "\n" + stack(e)).getBytes("UTF-8")); } finally { o.close(); }
        } catch (Throwable x) { }
    }

    /** null if no crash recorded */
    public static String read(Context c) { return readText(file(c)); }

    public static void clear(Context c) { file(c).delete(); stageFile(c).delete(); }

    /** Breadcrumb: where the last launch got to. "ok" = first frame was drawn. */
    public static void mark(Context c, String stage) {
        try {
            FileOutputStream o = new FileOutputStream(stageFile(c));
            try { o.write(stage.getBytes("UTF-8")); } finally { o.close(); }
        } catch (Throwable x) { }
    }

    /** null = no previous launch recorded. */
    public static String lastStage(Context c) { return readText(stageFile(c)); }

    private static String readText(File f) {
        if (!f.exists()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toString("UTF-8");
            } finally { in.close(); }
        } catch (Throwable e) { return "(file unreadable)"; }
    }
}

package com.ailearn.drive;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Random;

/** Feedforward MLP, tanh activation. w[layer][out][in], b[layer][out]. */
public class NeuralNetwork {
    public int[] sizes;
    public float[][][] w;
    public float[][] b;
    public float[][] act; // act[0] = inputs, act[l] = output of layer l-1 (live values for visualizer)
    private static final Random rng = new Random();

    private NeuralNetwork() {}

    public NeuralNetwork(int[] sizes) {
        this.sizes = sizes.clone();
        alloc();
        for (int l = 0; l < w.length; l++)
            for (int o = 0; o < w[l].length; o++) {
                b[l][o] = (rng.nextFloat() * 2f - 1f) * 0.2f;
                for (int i = 0; i < w[l][o].length; i++) w[l][o][i] = rng.nextFloat() * 2f - 1f;
            }
    }

    public static int[] topology(int in, int hidden, int neurons, int out) {
        int[] s = new int[hidden + 2];
        s[0] = in;
        for (int i = 1; i <= hidden; i++) s[i] = neurons;
        s[hidden + 1] = out;
        return s;
    }

    private void alloc() {
        int L = sizes.length - 1;
        w = new float[L][][];
        b = new float[L][];
        act = new float[L + 1][];
        for (int l = 0; l <= L; l++) act[l] = new float[sizes[l]];
        for (int l = 0; l < L; l++) {
            w[l] = new float[sizes[l + 1]][sizes[l]];
            b[l] = new float[sizes[l + 1]];
        }
    }

    public float[] forward(float[] in) {
        System.arraycopy(in, 0, act[0], 0, Math.min(in.length, sizes[0]));
        for (int l = 0; l < w.length; l++)
            for (int o = 0; o < w[l].length; o++) {
                float s = b[l][o];
                float[] row = w[l][o];
                float[] a = act[l];
                for (int i = 0; i < row.length; i++) s += row[i] * a[i];
                act[l + 1][o] = (float) Math.tanh(s);
            }
        return act[act.length - 1];
    }

    public NeuralNetwork copy() {
        NeuralNetwork n = new NeuralNetwork();
        n.sizes = sizes.clone();
        n.alloc();
        for (int l = 0; l < w.length; l++) {
            n.b[l] = b[l].clone();
            for (int o = 0; o < w[l].length; o++) n.w[l][o] = w[l][o].clone();
        }
        return n;
    }

    public boolean sameShape(NeuralNetwork p) { return Arrays.equals(sizes, p.sizes); }

    /** Uniform or single-point crossover (random choice). */
    public NeuralNetwork crossover(NeuralNetwork p) {
        if (!sameShape(p)) return copy();
        NeuralNetwork c = copy();
        boolean uniform = rng.nextBoolean();
        int total = 0;
        for (int l = 0; l < w.length; l++) total += w[l].length * w[l][0].length;
        int cut = rng.nextInt(Math.max(1, total)), idx = 0;
        for (int l = 0; l < w.length; l++)
            for (int o = 0; o < w[l].length; o++) {
                if (rng.nextBoolean()) c.b[l][o] = p.b[l][o];
                for (int i = 0; i < w[l][o].length; i++, idx++) {
                    boolean take = uniform ? rng.nextBoolean() : idx >= cut;
                    if (take) c.w[l][o][i] = p.w[l][o][i];
                }
            }
        return c;
    }

    /** Gaussian mutation: each gene mutates with probability 'rate' by N(0, intensity). */
    public void mutate(float rate, float intensity) {
        for (int l = 0; l < w.length; l++)
            for (int o = 0; o < w[l].length; o++) {
                if (rng.nextFloat() < rate) b[l][o] += (float) rng.nextGaussian() * intensity;
                for (int i = 0; i < w[l][o].length; i++)
                    if (rng.nextFloat() < rate) w[l][o][i] += (float) rng.nextGaussian() * intensity;
            }
    }

    // ---------------- JSON ----------------
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\n \"layers\": [");
        for (int i = 0; i < sizes.length; i++) sb.append(i > 0 ? ", " : "").append(sizes[i]);
        sb.append("],\n \"weights\": [\n");
        int L = w.length;
        for (int l = 0; l < L; l++) {
            sb.append("  [\n");
            for (int o = 0; o < w[l].length; o++) {
                sb.append("   [");
                for (int i = 0; i < w[l][o].length; i++) sb.append(i > 0 ? ", " : "").append(w[l][o][i]);
                sb.append(o < w[l].length - 1 ? "],\n" : "]\n");
            }
            sb.append(l < L - 1 ? "  ],\n" : "  ]\n");
        }
        sb.append(" ],\n \"biases\": [\n");
        for (int l = 0; l < L; l++) {
            sb.append("  [");
            for (int o = 0; o < b[l].length; o++) sb.append(o > 0 ? ", " : "").append(b[l][o]);
            sb.append(l < L - 1 ? "],\n" : "]\n");
        }
        sb.append(" ]\n}");
        return sb.toString();
    }

    /** Throws IllegalArgumentException with an actionable message on any problem. */
    public static NeuralNetwork fromJson(String s) {
        if (s == null || s.trim().length() == 0) throw new IllegalArgumentException("Empty input - paste or write a network JSON");
        JSONObject o;
        try { o = new JSONObject(s); } catch (JSONException e) { throw new IllegalArgumentException(syntaxMsg(s, e)); }
        JSONArray W = o.optJSONArray("weights");
        if (W == null) throw new IllegalArgumentException("Missing \"weights\" array (3D: layers > neurons > weights)");
        int L = W.length();
        if (L < 1) throw new IllegalArgumentException("\"weights\" has no layers");
        float[][][] w = new float[L][][];
        int[] sz = new int[L + 1];
        for (int l = 0; l < L; l++) {
            JSONArray m = W.optJSONArray(l);
            if (m == null) throw new IllegalArgumentException("Layer " + (l + 1) + ": must be an array of neuron rows");
            int rows = m.length();
            if (rows < 1) throw new IllegalArgumentException("Layer " + (l + 1) + " has no neurons");
            w[l] = new float[rows][];
            int cols = 0;
            for (int r = 0; r < rows; r++) {
                JSONArray row = m.optJSONArray(r);
                if (row == null) throw new IllegalArgumentException("Layer " + (l + 1) + ", neuron " + (r + 1) + ": must be an array of weights");
                if (r == 0) cols = row.length();
                else if (row.length() != cols)
                    throw new IllegalArgumentException("Layer " + (l + 1) + ", neuron " + (r + 1) + " has " + row.length() + " weights but neuron 1 has " + cols);
                if (cols < 1) throw new IllegalArgumentException("Layer " + (l + 1) + ", neuron " + (r + 1) + " has no weights");
                w[l][r] = new float[cols];
                for (int c = 0; c < cols; c++) w[l][r][c] = num(row.opt(c), "Layer " + (l + 1) + ", neuron " + (r + 1) + ", weight " + (c + 1));
            }
            if (l == 0) sz[0] = cols;
            else if (cols != sz[l])
                throw new IllegalArgumentException("Layer " + l + " outputs (" + sz[l] + ") do not match Layer " + (l + 1) + " inputs (" + cols + ")");
            sz[l + 1] = rows;
        }
        float[][] bb = new float[L][];
        JSONArray B = o.optJSONArray("biases");
        if (B != null && B.length() != L)
            throw new IllegalArgumentException("\"biases\" has " + B.length() + " layers but \"weights\" has " + L);
        for (int l = 0; l < L; l++) {
            bb[l] = new float[sz[l + 1]];
            if (B == null) continue;
            JSONArray a = B.optJSONArray(l);
            if (a == null) throw new IllegalArgumentException("Biases layer " + (l + 1) + " must be an array");
            if (a.length() != sz[l + 1])
                throw new IllegalArgumentException("Layer " + (l + 1) + " has " + sz[l + 1] + " neurons but " + a.length() + " biases");
            for (int i = 0; i < a.length(); i++) bb[l][i] = num(a.opt(i), "Bias layer " + (l + 1) + ", neuron " + (i + 1));
        }
        JSONArray ls = o.optJSONArray("layers");
        if (ls != null) {
            boolean ok = ls.length() == sz.length;
            for (int i = 0; ok && i < sz.length; i++) ok = ls.optInt(i, -1) == sz[i];
            if (!ok) throw new IllegalArgumentException("\"layers\" " + ls.toString() + " does not match the weight matrices " + Arrays.toString(sz));
        }
        NeuralNetwork n = new NeuralNetwork();
        n.sizes = sz;
        n.alloc();
        n.w = w;
        n.b = bb;
        return n;
    }

    private static float num(Object v, String where) {
        if (!(v instanceof Number)) throw new IllegalArgumentException(where + " is not a number");
        double d = ((Number) v).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) > 1000)
            throw new IllegalArgumentException(where + " is out of range (" + d + "); use values between -1000 and 1000");
        return (float) d;
    }

    private static String syntaxMsg(String s, JSONException e) {
        String m = String.valueOf(e.getMessage());
        int k = m.indexOf("at character ");
        if (k >= 0) {
            int p = k + 13, q = p;
            while (q < m.length() && Character.isDigit(m.charAt(q))) q++;
            try {
                int pos = Math.min(Integer.parseInt(m.substring(p, q)), s.length());
                int line = 1;
                for (int i = 0; i < pos; i++) if (s.charAt(i) == '\n') line++;
                return "Invalid JSON syntax at line " + line + ": " + m.substring(0, k).trim();
            } catch (Exception ex) { }
        }
        return "Invalid JSON syntax: " + (m.length() > 80 ? m.substring(0, 80) : m);
    }

    /** Checks the net fits the car (inputs = rays+2, outputs = 4). Returns null if OK. */
    public static String dimError(NeuralNetwork n, int expIn) {
        if (n.sizes[0] != expIn)
            return "Layer 1 takes " + n.sizes[0] + " inputs but sensors provide " + expIn + " (" + (expIn - 2) + " rays + speed + angle). Change Ray count or Layer 1.";
        int out = n.sizes[n.sizes.length - 1];
        if (out != 4) return "Last layer has " + out + " outputs but 4 are required (Steer Left, Steer Right, Throttle, Brake).";
        return null;
    }
}

package com.ailearn.drive;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

/** Genetic algorithm with multiple independent populations (species), up to 100 cars total. */
public class EvolutionManager {
    public static class Population {
        public int id;
        public ArrayList<Car> cars = new ArrayList<Car>();
        public float bestFit;
    }

    public ArrayList<Population> pops = new ArrayList<Population>();
    public Track track;
    public Context ctx;
    public int generation = 1, tick = 0, tickLimit = 2400;
    public float globalBest = 0f, lastGenBest = 0f;
    public NeuralNetwork globalBestBrain;
    private final Random rng = new Random();
    private static final Comparator<Car> BY_FITNESS = new Comparator<Car>() {
        public int compare(Car a, Car b) { return Float.compare(b.fitness, a.fitness); }
    };

    public EvolutionManager(Context c, Track t) { ctx = c; track = t; }

    /** Fresh run. seed (optional) = the brain to seed each population from (the rest are mutated
     *  variants); null starts from random networks. Callers resolve any saved custom brain via
     *  {@link #resolveSeed} so the outcome can be reported to the user. */
    public void startFresh(NeuralNetwork seed) {
        int total = Math.max(1, Math.min(100, GameSettings.maxCars));
        int P = Math.max(1, Math.min(GameSettings.populationCount, total));
        int per = Math.max(1, total / P);
        pops.clear();
        generation = 1; globalBest = 0; globalBestBrain = null;
        for (int p = 0; p < P; p++) {
            Population pop = new Population();
            pop.id = p;
            for (int k = 0; k < per; k++) {
                NeuralNetwork n;
                if (seed != null) { n = seed.copy(); if (k > 0) n.mutate(0.3f, 0.3f); }
                else n = new NeuralNetwork(NeuralNetwork.topology(GameSettings.inputCount(), GameSettings.hiddenLayers, GameSettings.neuronsPerLayer, 4));
                pop.cars.add(new Car(n, p));
            }
            pops.add(pop);
        }
        resetGeneration();
    }

    /** Outcome of resolving the seed brain for a fresh run (pure, no UI/Android dependencies). */
    public static class SeedResolution {
        public final NeuralNetwork seed;
        public final String message;
        public final int rayCount;
        public SeedResolution(NeuralNetwork seed, String message, int rayCount) {
            this.seed = seed; this.message = message; this.rayCount = rayCount;
        }
    }

    /** Resolves the brain to seed a fresh run from. An explicit provided brain wins (e.g. "Load Trained
     *  Brain"); otherwise the saved custom brain is validated against the current sensor config and,
     *  on a repairable ray-count mismatch, rayCount is adjusted to match it (like "Load Trained Brain")
     *  so it still seeds the run. Returns a user-facing message whenever a requested custom brain could
     *  not be used, so the failure is never silent. Pure: loads nothing and toasts nothing itself. */
    public static SeedResolution resolveSeed(NeuralNetwork provided, boolean useCustomBrain,
            NeuralNetwork saved, int currentInputs, int currentRays) {
        if (provided != null) return new SeedResolution(provided, null, currentRays);
        if (!useCustomBrain) return new SeedResolution(null, null, currentRays);
        if (saved == null) return new SeedResolution(null, "Custom brain not found or corrupt - starting from random brains", currentRays);
        String err = NeuralNetwork.dimError(saved, currentInputs);
        if (err == null) return new SeedResolution(saved, null, currentRays);
        int out = saved.sizes[saved.sizes.length - 1];
        int rays = saved.sizes[0] - 2;
        if (out == 4 && rays >= 1 && rays <= 13)
            return new SeedResolution(saved, "Custom brain needs " + rays + " rays - adjusted sensor ray count (was " + currentRays + "); starting from your brain.", rays);
        return new SeedResolution(null, "Custom brain doesn't match current settings - starting from random brains", currentRays);
    }

    public boolean resume(SaveManager.Checkpoint cp) {
        if (cp == null || cp.brains.isEmpty() || cp.brains.get(0).isEmpty()) return false;
        for (ArrayList<NeuralNetwork> list : cp.brains) {
            if (list.isEmpty()) return false;
            for (NeuralNetwork n : list)
                if (NeuralNetwork.dimError(n, GameSettings.inputCount()) != null) return false;
        }
        pops.clear();
        for (int p = 0; p < cp.brains.size(); p++) {
            Population pop = new Population();
            pop.id = p;
            for (NeuralNetwork n : cp.brains.get(p)) pop.cars.add(new Car(n, p));
            pops.add(pop);
        }
        generation = cp.generation;
        globalBest = cp.best;
        globalBestBrain = SaveManager.loadAutoBest(ctx);
        resetGeneration();
        return true;
    }

    private void resetGeneration() {
        tick = 0;
        tickLimit = GameSettings.genTimeLimit * 60;
        for (Population p : pops) for (Car c : p.cars) c.reset(track);
    }

    /** One physics tick (60 per second of sim time). */
    public void step() {
        boolean any = false;
        for (Population p : pops) for (Car c : p.cars) { c.update(track); if (c.alive) any = true; }
        tick++;
        if (!any || tick >= tickLimit) endGeneration();
    }

    private Car pick(ArrayList<Car> cars) {
        Car best = null;
        for (int i = 0; i < 3; i++) {
            Car c = cars.get(rng.nextInt(cars.size()));
            if (best == null || c.fitness > best.fitness) best = c;
        }
        return best;
    }

    private void endGeneration() {
        lastGenBest = 0;
        for (Population pop : pops) {
            ArrayList<Car> cars = pop.cars;
            Collections.sort(cars, BY_FITNESS);
            pop.bestFit = cars.get(0).fitness;
            lastGenBest = Math.max(lastGenBest, pop.bestFit);
            if (pop.bestFit > globalBest || globalBestBrain == null) { globalBest = Math.max(globalBest, pop.bestFit); globalBestBrain = cars.get(0).brain.copy(); }
            int N = cars.size(), elites = Math.max(1, N / 10);
            ArrayList<NeuralNetwork> next = new ArrayList<NeuralNetwork>();
            for (int e = 0; e < elites && e < N; e++) next.add(cars.get(e).brain.copy()); // elitism
            while (next.size() < N) {
                Car a = pick(cars), b = pick(cars); // tournament selection
                NeuralNetwork child = rng.nextFloat() < GameSettings.crossoverRate ? a.brain.crossover(b.brain) : a.brain.copy();
                child.mutate(GameSettings.mutationRate, GameSettings.mutationIntensity);
                next.add(child);
            }
            for (int i = 0; i < N; i++) cars.get(i).brain = next.get(i);
        }
        generation++;
        if (GameSettings.autoSaveProgress) {
            if (globalBestBrain != null) SaveManager.autoSaveBest(ctx, globalBestBrain, generation - 1, globalBest);
            if (generation % Math.max(1, GameSettings.checkpointEvery) == 0) SaveManager.saveCheckpoint(ctx, this);
        }
        resetGeneration();
    }

    public int aliveCount() { int n = 0; for (Population p : pops) for (Car c : p.cars) if (c.alive) n++; return n; }
    public int totalCars() { int n = 0; for (Population p : pops) n += p.cars.size(); return n; }

    public float bestFitnessNow() {
        float b = 0;
        for (Population p : pops) for (Car c : p.cars) b = Math.max(b, c.fitness);
        return b;
    }

    /** Best alive car (or best overall if none alive). */
    public Car getLeader() {
        Car best = null, bestAny = null;
        for (Population p : pops) for (Car c : p.cars) {
            if (bestAny == null || c.fitness > bestAny.fitness) bestAny = c;
            if (c.alive && (best == null || c.fitness > best.fitness)) best = c;
        }
        return best != null ? best : bestAny;
    }
}

package com.sametkapan.akillitarimnlp.models;

import java.util.List;

public class Recipe {
    public String name;
    public int totalDays;
    public List<Phase> phases;

    public static class Phase {
        public int startDay;
        public int endDay;
        public String phaseName;
        public double targetMoistureMin;
        public double targetMoistureMax;
        public double targetVpdMin;
        public double targetVpdMax;
        public int pulseDurationSec;
        public String irrigationTimeOfDay; // Format: "HH:mm" e.g., "12:30"
        public int irrigationFrequencyDays; // e.g., 1 for every day, 3 for every 3 days
    }
}
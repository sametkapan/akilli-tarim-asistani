package com.sametkapan.akillitarimnlp.models;

import java.util.ArrayList;
import java.util.List;

public class RecipeManager {
    public static List<Recipe> getRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        // Microgreen Radish Recipe
        Recipe radish = new Recipe();
        radish.name = "Microgreen Turp (20 Gün)";
        radish.totalDays = 20;
        radish.phases = new ArrayList<>();

        Recipe.Phase germ = new Recipe.Phase();
        germ.startDay = 1;
        germ.endDay = 4;
        germ.phaseName = "Çimlenme";
        germ.targetMoistureMin = 70.0;
        germ.targetMoistureMax = 80.0;
        germ.targetVpdMin = 0.5;
        germ.targetVpdMax = 0.7;
        germ.pulseDurationSec = 3;
        germ.irrigationTimeOfDay = "12:30";
        germ.irrigationFrequencyDays = 1;
        radish.phases.add(germ);

        Recipe.Phase growth = new Recipe.Phase();
        growth.startDay = 5;
        growth.endDay = 20;
        growth.phaseName = "Gelişim";
        growth.targetMoistureMin = 50.0;
        growth.targetMoistureMax = 65.0;
        growth.targetVpdMin = 0.9;
        growth.targetVpdMax = 1.1;
        growth.pulseDurationSec = 3;
        growth.irrigationTimeOfDay = "12:30";
        growth.irrigationFrequencyDays = 1;
        radish.phases.add(growth);

        recipes.add(radish);

        // General House Plant
        Recipe housePlant = new Recipe();
        housePlant.name = "Genel Ev Bitkisi";
        housePlant.totalDays = 365;
        housePlant.phases = new ArrayList<>();

        Recipe.Phase standard = new Recipe.Phase();
        standard.startDay = 1;
        standard.endDay = 365;
        standard.phaseName = "Standart";
        standard.targetMoistureMin = 40.0;
        standard.targetMoistureMax = 60.0;
        standard.targetVpdMin = 1.1;
        standard.targetVpdMax = 1.3;
        standard.pulseDurationSec = 5;
        standard.irrigationTimeOfDay = "09:00";
        standard.irrigationFrequencyDays = 3;
        housePlant.phases.add(standard);

        recipes.add(housePlant);

        return recipes;
    }
}
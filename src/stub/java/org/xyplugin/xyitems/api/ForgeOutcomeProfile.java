package org.xyplugin.xyitems.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForgeOutcomeProfile {
    private final String itemId;
    private final List<Outcome> outcomes;
    private final double totalWeight;

    public ForgeOutcomeProfile(String itemId, List<Outcome> outcomes) {
        this.itemId = itemId;
        this.outcomes = Collections.unmodifiableList(new ArrayList<Outcome>(outcomes));
        double total = 0D;
        for (Outcome outcome : outcomes) total += outcome.getWeight();
        this.totalWeight = total;
    }

    public String getItemId() { return itemId; }
    public List<Outcome> getOutcomes() { return outcomes; }
    public double getTotalWeight() { return totalWeight; }

    public static final class Outcome {
        public enum Type { FAILURE, QUALITY }

        private final Type type;
        private final String id;
        private final String name;
        private final String color;
        private final double weight;
        private final double probability;

        public Outcome(Type type, String id, String name, String color, double weight, double probability) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.color = color;
            this.weight = weight;
            this.probability = probability;
        }

        public boolean isFailure() { return type == Type.FAILURE; }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getColor() { return color; }
        public double getWeight() { return weight; }
        public double getProbability() { return probability; }
    }
}

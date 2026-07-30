package org.xyplugin.xyitems.api;

import java.util.List;

public final class ForgeOutcomeProfile {
    public String getItemId() { return ""; }
    public List<Outcome> getOutcomes() { throw new UnsupportedOperationException("compile-only stub"); }
    public double getTotalWeight() { return 0D; }

    public static final class Outcome {
        public boolean isFailure() { return false; }
        public String getId() { return ""; }
        public String getName() { return ""; }
        public String getColor() { return ""; }
        public double getWeight() { return 0D; }
        public double getProbability() { return 0D; }
    }
}

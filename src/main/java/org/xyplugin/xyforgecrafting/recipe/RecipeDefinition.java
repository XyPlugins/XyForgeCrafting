package org.xyplugin.xyforgecrafting.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeDefinition {
    private final String id;
    private final String name;
    private final Blueprint blueprint;
    private final Result result;
    private final Map<String, Long> requirements;
    private final Economy economy;
    private final List<String> successCommands;
    private final Failure failure;

    public RecipeDefinition(String id, String name, Blueprint blueprint, Result result,
                            Map<String, Long> requirements, Economy economy, List<String> successCommands,
                            Failure failure) {
        this.id = id;
        this.name = name;
        this.blueprint = blueprint;
        this.result = result;
        this.requirements = Collections.unmodifiableMap(new LinkedHashMap<String, Long>(requirements));
        this.economy = economy;
        this.successCommands = Collections.unmodifiableList(new ArrayList<String>(successCommands));
        this.failure = failure;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Blueprint getBlueprint() { return blueprint; }
    public Result getResult() { return result; }
    public Map<String, Long> getRequirements() { return requirements; }
    public Economy getEconomy() { return economy; }
    public List<String> getSuccessCommands() { return successCommands; }
    public Failure getFailure() { return failure; }

    public static final class Blueprint {
        private final String template;
        private final String displayName;
        private final List<String> lore;

        public Blueprint(String template, String displayName, List<String> lore) {
            this.template = template;
            this.displayName = displayName;
            this.lore = Collections.unmodifiableList(new ArrayList<String>(lore));
        }

        public String getTemplate() { return template; }
        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
    }

    public static final class Result {
        private final String item;
        private final int amount;

        public Result(String item, int amount) {
            this.item = item;
            this.amount = amount;
        }

        public String getItem() { return item; }
        public String getXyItemsId() { return item.substring(item.indexOf(':') + 1); }
        public int getAmount() { return amount; }
    }

    public static final class Economy {
        private final String type;
        private final double amount;

        public Economy(String type, double amount) {
            this.type = type;
            this.amount = amount;
        }

        public String getType() { return type; }
        public double getAmount() { return amount; }
    }

    public static final class Failure {
        public enum BlueprintPolicy { DESTROY, RETURN }

        private final BlueprintPolicy blueprintPolicy;
        private final int materialsRefundPercent;
        private final int moneyRefundPercent;
        private final String message;

        public Failure(BlueprintPolicy blueprintPolicy, int materialsRefundPercent, int moneyRefundPercent,
                       String message) {
            this.blueprintPolicy = blueprintPolicy;
            this.materialsRefundPercent = materialsRefundPercent;
            this.moneyRefundPercent = moneyRefundPercent;
            this.message = message;
        }

        public BlueprintPolicy getBlueprintPolicy() { return blueprintPolicy; }
        public int getMaterialsRefundPercent() { return materialsRefundPercent; }
        public int getMoneyRefundPercent() { return moneyRefundPercent; }
        public String getMessage() { return message; }
    }
}

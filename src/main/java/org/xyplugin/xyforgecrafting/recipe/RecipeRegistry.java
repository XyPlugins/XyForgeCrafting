package org.xyplugin.xyforgecrafting.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RecipeRegistry {
    private final Map<String, RecipeDefinition> recipes;

    private RecipeRegistry(Map<String, RecipeDefinition> recipes) {
        this.recipes = Collections.unmodifiableMap(new LinkedHashMap<String, RecipeDefinition>(recipes));
    }

    public static RecipeRegistry empty() {
        return new RecipeRegistry(Collections.<String, RecipeDefinition>emptyMap());
    }

    public static LoadResult load(File directory, Logger logger) {
        if (!directory.exists() && !directory.mkdirs()) return LoadResult.failure("无法创建配方目录: " + directory);
        List<File> files = new ArrayList<File>();
        collect(directory, files);
        Collections.sort(files, (left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        Map<String, RecipeDefinition> recipes = new LinkedHashMap<String, RecipeDefinition>();
        List<String> errors = new ArrayList<String>();
        for (File file : files) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                ConfigurationSection section = yaml.getConfigurationSection("recipe");
                if (section == null) throw new IllegalArgumentException("缺少根节点 recipe:");
                if (!section.getBoolean("enabled", true)) continue;
                RecipeDefinition recipe = parse(section);
                if (recipes.containsKey(recipe.getId())) throw new IllegalArgumentException("配方ID重复: " + recipe.getId());
                recipes.put(recipe.getId(), recipe);
            } catch (Exception failure) {
                errors.add(file.getPath() + ": " + failure.getMessage());
            }
        }
        for (String error : errors) logger.warning("[XyForgeCrafting] " + error);
        return errors.isEmpty() ? LoadResult.success(new RecipeRegistry(recipes)) : LoadResult.failure(errors);
    }

    private static RecipeDefinition parse(ConfigurationSection section) {
        String id = normalize(section.getString("id", ""));
        if (id.isEmpty()) throw new IllegalArgumentException("recipe.id不能为空。");
        String name = section.getString("name", "&f" + id);

        ConfigurationSection blueprint = requiredSection(section, "blueprint");
        String template = itemId(blueprint.getString("material", blueprint.getString("template", "")),
                "blueprint.material", "minecraft");
        String displayName = blueprint.getString("name", blueprint.getString("display-name", "&f" + id + "锻造图"));
        List<String> blueprintLore = new ArrayList<String>(blueprint.getStringList("lore"));

        ConfigurationSection result = section.getConfigurationSection("result");
        String resultItem;
        int resultAmount;
        if (result == null && section.isString("result")) {
            resultItem = itemId(section.getString("result", ""), "result", "xyitems");
            resultAmount = 1;
        } else {
            if (result == null) throw new IllegalArgumentException("缺少 result 节点。");
            resultItem = itemId(result.getString("item", ""), "result.item", null);
            resultAmount = result.getInt("amount", 1);
        }
        if (!resultItem.toLowerCase(Locale.ROOT).startsWith("xyitems:")) {
            throw new IllegalArgumentException("result.item当前只支持xyitems:完整ID。");
        }
        if (resultAmount <= 0 || resultAmount > 64) throw new IllegalArgumentException("result.amount必须在1到64之间。");

        Map<String, Long> requirements = new LinkedHashMap<String, Long>();
        ConfigurationSection requirementSection = section.getConfigurationSection("requirements");
        if (requirementSection != null) {
            for (String key : requirementSection.getKeys(false)) {
                String fullId = itemId(key, "requirements物品ID", "minecraft");
                long amount = requirementSection.getLong(key, 0L);
                if (amount <= 0L) throw new IllegalArgumentException("材料 " + key + " 的数量必须大于0。");
                requirements.put(fullId, amount);
            }
        }

        ConfigurationSection economySection = section.getConfigurationSection("economy");
        String economyType = economySection == null ? "VAULT" : economySection.getString("type", "VAULT").trim().toUpperCase(Locale.ROOT);
        double economyAmount = section.contains("money") ? section.getDouble("money", 0D)
                : economySection == null ? 0D : economySection.getDouble("amount", 0D);
        if (!"VAULT".equals(economyType)) throw new IllegalArgumentException("economy.type当前只支持VAULT。");
        if (Double.isNaN(economyAmount) || Double.isInfinite(economyAmount) || economyAmount < 0D) {
            throw new IllegalArgumentException("money不能小于0。");
        }

        List<String> commands = section.isList("success-commands")
                ? section.getStringList("success-commands")
                : section.getStringList("outcomes.success.commands");
        ConfigurationSection compactFailure = section.getConfigurationSection("failure");
        String policyText = compactFailure == null
                ? section.getString("outcomes.failure.blueprint", "DESTROY")
                : compactFailure.getString("blueprint", section.getString("outcomes.failure.blueprint", "DESTROY"));
        policyText = policyText.trim().toUpperCase(Locale.ROOT);
        RecipeDefinition.Failure.BlueprintPolicy policy;
        try {
            policy = RecipeDefinition.Failure.BlueprintPolicy.valueOf(policyText);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("failure.blueprint只能是DESTROY或RETURN。");
        }
        int materialRefund = compactFailure != null && compactFailure.contains("refund-materials")
                ? percent(compactFailure.getInt("refund-materials", 0), "refund-materials")
                : percent(section.getInt("outcomes.failure.refund.materials-percent", 0), "materials-percent");
        int moneyRefund = compactFailure != null && compactFailure.contains("refund-money")
                ? percent(compactFailure.getInt("refund-money", 0), "refund-money")
                : percent(section.getInt("outcomes.failure.refund.money-percent", 0), "money-percent");
        String failureMessage = compactFailure == null
                ? section.getString("outcomes.failure.message", "&c锻造失败。")
                : compactFailure.getString("message", section.getString("outcomes.failure.message", "&c锻造失败。"));
        return new RecipeDefinition(id, name,
                new RecipeDefinition.Blueprint(template, displayName, blueprintLore),
                new RecipeDefinition.Result(resultItem, resultAmount), requirements,
                new RecipeDefinition.Economy(economyType, economyAmount), commands,
                new RecipeDefinition.Failure(policy, materialRefund, moneyRefund, failureMessage));
    }

    private static int percent(int value, String field) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(field + "必须在0到100之间。");
        return value;
    }

    private static ConfigurationSection requiredSection(ConfigurationSection owner, String path) {
        ConfigurationSection section = owner.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("缺少 " + path + " 节点。");
        return section;
    }

    private static String itemId(String raw, String field, String defaultProvider) {
        String value = raw == null ? "" : raw.trim();
        int separator = value.indexOf(':');
        if (separator < 0 && defaultProvider != null && !defaultProvider.trim().isEmpty()) {
            value = defaultProvider.trim().toLowerCase(Locale.ROOT) + ":" + value;
            separator = value.indexOf(':');
        }
        if (separator <= 0 || separator >= value.length() - 1) throw new IllegalArgumentException(field + "必须使用provider:item完整ID。");
        return value;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void collect(File directory, List<File> files) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, files);
            else if (child.isFile() && (child.getName().endsWith(".yml") || child.getName().endsWith(".yaml"))) files.add(child);
        }
    }

    public Optional<RecipeDefinition> find(String id) {
        return id == null ? Optional.<RecipeDefinition>empty() : Optional.ofNullable(recipes.get(normalize(id)));
    }

    public List<String> getIds() {
        return Collections.unmodifiableList(new ArrayList<String>(recipes.keySet()));
    }

    public int size() { return recipes.size(); }

    public static final class LoadResult {
        private final RecipeRegistry registry;
        private final List<String> errors;

        private LoadResult(RecipeRegistry registry, List<String> errors) {
            this.registry = registry;
            this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
        }

        public static LoadResult success(RecipeRegistry registry) { return new LoadResult(registry, Collections.<String>emptyList()); }
        public static LoadResult failure(String error) { return failure(Collections.singletonList(error)); }
        public static LoadResult failure(List<String> errors) { return new LoadResult(null, errors); }
        public boolean isSuccess() { return registry != null && errors.isEmpty(); }
        public RecipeRegistry getRegistry() { return registry; }
        public List<String> getErrors() { return errors; }
    }
}

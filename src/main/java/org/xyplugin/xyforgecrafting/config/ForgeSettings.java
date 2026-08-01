package org.xyplugin.xyforgecrafting.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.xyplugin.xyforgecrafting.util.Text;

/** Immutable, fully validated GUI and message snapshot. */
public final class ForgeSettings {
    private final String title;
    private final List<String> layout;
    private final Map<Character, GuiComponent> components;
    private final Map<GuiComponentType, List<Integer>> slots;
    private final Map<String, String> messages;
    private final boolean animationEnabled;
    private final int animationInterval;
    private final int animationLoops;
    private final String animationPreset;
    private final List<List<Integer>> animationFrames;
    private final DisplayItemSpec animationHeadDisplay;
    private final DisplayItemSpec animationTrailDisplay;
    private final ForgeRecordSettings forgeRecord;

    private ForgeSettings(String title, List<String> layout, Map<Character, GuiComponent> components,
                          Map<GuiComponentType, List<Integer>> slots, Map<String, String> messages,
                          boolean animationEnabled, int animationInterval, int animationLoops,
                          String animationPreset, List<List<Integer>> animationFrames,
                          DisplayItemSpec animationHeadDisplay, DisplayItemSpec animationTrailDisplay,
                          ForgeRecordSettings forgeRecord) {
        this.title = title;
        this.layout = Collections.unmodifiableList(new ArrayList<String>(layout));
        this.components = Collections.unmodifiableMap(new LinkedHashMap<Character, GuiComponent>(components));
        EnumMap<GuiComponentType, List<Integer>> copied = new EnumMap<GuiComponentType, List<Integer>>(GuiComponentType.class);
        for (Map.Entry<GuiComponentType, List<Integer>> entry : slots.entrySet()) {
            copied.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<Integer>(entry.getValue())));
        }
        this.slots = Collections.unmodifiableMap(copied);
        this.messages = Collections.unmodifiableMap(new LinkedHashMap<String, String>(messages));
        this.animationEnabled = animationEnabled;
        this.animationInterval = animationInterval;
        this.animationLoops = animationLoops;
        this.animationPreset = animationPreset;
        List<List<Integer>> copiedFrames = new ArrayList<List<Integer>>();
        for (List<Integer> frame : animationFrames) {
            copiedFrames.add(Collections.unmodifiableList(new ArrayList<Integer>(frame)));
        }
        this.animationFrames = Collections.unmodifiableList(copiedFrames);
        this.animationHeadDisplay = animationHeadDisplay;
        this.animationTrailDisplay = animationTrailDisplay;
        this.forgeRecord = forgeRecord;
    }

    public static ForgeSettings load(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        List<String> layout = new ArrayList<String>(yaml.getStringList("gui.layout"));
        if (layout.isEmpty() && yaml.isList("gui")) layout.addAll(yaml.getStringList("gui"));
        if (layout.isEmpty() || layout.size() > 6) throw new IllegalArgumentException("gui.layout 必须包含1到6行。");
        for (String row : layout) {
            if (row == null || row.length() != 9) throw new IllegalArgumentException("gui.layout 每行必须正好9个字符。");
        }

        String title = yaml.getString("gui.title", yaml.getString("title", "&8锻造台"));
        if (Text.strip(title).length() > 32) throw new IllegalArgumentException("gui.title 去除颜色后不能超过32个字符。");
        ConfigurationSection root = yaml.getConfigurationSection("gui.cons");
        if (root == null) root = yaml.getConfigurationSection("gui.components");
        if (root == null) root = yaml.getConfigurationSection("cons");
        if (root == null) root = yaml.getConfigurationSection("components");
        if (root == null) throw new IllegalArgumentException("缺少 gui.cons 组件配置。");

        Map<Character, GuiComponent> components = new LinkedHashMap<Character, GuiComponent>();
        for (String rawKey : root.getKeys(false)) {
            if (rawKey.length() != 1) throw new IllegalArgumentException("GUI组件键必须是单个字符: " + rawKey);
            ConfigurationSection section = root.getConfigurationSection(rawKey);
            if (section == null) throw new IllegalArgumentException("GUI组件 " + rawKey + " 必须是配置节点。");
            if (!section.isString("type") || section.getString("type", "").trim().isEmpty()) {
                throw new IllegalArgumentException("GUI组件 " + rawKey + " 必须明确填写type。");
            }
            GuiComponentType type;
            try {
                type = GuiComponentType.valueOf(section.getString("type").trim().toUpperCase());
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("GUI组件 " + rawKey + " 的type无效。");
            }
            components.put(rawKey.charAt(0), new GuiComponent(rawKey.charAt(0), type,
                    parseDisplay(section.getConfigurationSection("display"), "GUI组件 " + rawKey)));
        }

        EnumMap<GuiComponentType, List<Integer>> slots = new EnumMap<GuiComponentType, List<Integer>>(GuiComponentType.class);
        for (GuiComponentType type : GuiComponentType.values()) slots.put(type, new ArrayList<Integer>());
        for (int row = 0; row < layout.size(); row++) {
            for (int column = 0; column < 9; column++) {
                char key = layout.get(row).charAt(column);
                if (key == ' ') continue;
                GuiComponent component = components.get(key);
                if (component == null) throw new IllegalArgumentException("布局使用了未配置的组件字符: " + key);
                slots.get(component.getType()).add(row * 9 + column);
            }
        }
        if (slots.get(GuiComponentType.FORGE_BLUEPRINT).size() != 1) {
            throw new IllegalArgumentException("GUI必须且只能包含一个FORGE_BLUEPRINT槽位。");
        }
        if (slots.get(GuiComponentType.FORGE_START).size() != 1) {
            throw new IllegalArgumentException("GUI必须且只能包含一个FORGE_START槽位。");
        }
        if (slots.get(GuiComponentType.FORGE_REQUIREMENTS).isEmpty()) {
            throw new IllegalArgumentException("GUI至少需要一个FORGE_REQUIREMENTS槽位。");
        }
        if (slots.get(GuiComponentType.FORGE_PROBABILITY).isEmpty()) {
            throw new IllegalArgumentException("GUI至少需要一个FORGE_PROBABILITY槽位。");
        }

        Map<String, String> messages = new LinkedHashMap<String, String>();
        ConfigurationSection messageSection = yaml.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) messages.put(key, messageSection.getString(key, ""));
        }
        ConfigurationSection animation = yaml.getConfigurationSection("animation");
        boolean animationEnabled = animation == null || animation.getBoolean("enabled", true);
        int interval = animation == null ? 1 : Math.max(1, Math.min(200, animation.getInt("interval-ticks", 1)));
        int loops = animation == null ? 1 : Math.max(1, Math.min(10, animation.getInt("loops", 1)));
        String animationPreset = animation == null ? "BORDER_CONVERGE"
                : animation.getString("active-preset", "BORDER_CONVERGE").trim().toUpperCase(Locale.ROOT);
        List<List<Integer>> animationFrames = buildAnimationFrames(animationPreset, layout.size() * 9,
                slots.get(GuiComponentType.BACKGROUND));
        DisplayItemSpec animationHead = parseAnimationDisplay(animation, "head", "STAINED_GLASS_PANE", 5,
                "&a锻造之火");
        DisplayItemSpec animationTrail = parseAnimationDisplay(animation, "trail", "STAINED_GLASS_PANE", 13,
                "&2锻造余焰");

        ConfigurationSection record = yaml.getConfigurationSection("forge-record");
        boolean recordEnabled = record == null || record.getBoolean("enabled", true);
        String recordTimezone = record == null ? "Asia/Shanghai"
                : record.getString("timezone", "Asia/Shanghai");
        String recordTimeFormat = record == null ? "yyyy-MM-dd HH:mm:ss"
                : record.getString("time-format", "yyyy-MM-dd HH:mm:ss");
        boolean replaceLastSeparator = record == null || record.getBoolean("replace-last-separator", true);
        List<String> recordLore = record != null && record.isList("lore")
                ? record.getStringList("lore") : defaultForgeRecordLore();
        ForgeRecordSettings forgeRecord = new ForgeRecordSettings(recordEnabled, recordTimezone,
                recordTimeFormat, replaceLastSeparator, recordLore);
        return new ForgeSettings(title, layout, components, slots, messages, animationEnabled, interval, loops,
                animationPreset, animationFrames, animationHead, animationTrail, forgeRecord);
    }

    private static DisplayItemSpec parseDisplay(ConfigurationSection section, String owner) {
        if (section == null) throw new IllegalArgumentException(owner + " 缺少display节点。");
        Material material = material(section.getString("material", "STONE"));
        int data = section.getInt("data", 0);
        if (data < Short.MIN_VALUE || data > Short.MAX_VALUE) throw new IllegalArgumentException(owner + " data超出范围。");
        String name = section.getString("name", "&f" + owner);
        List<String> lore = section.isList("lore") ? section.getStringList("lore")
                : Collections.singletonList(section.getString("lore", ""));
        return new DisplayItemSpec(material, (short) data, name, lore);
    }

    private static Material material(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name.trim());
        if (material == null || material == Material.AIR) throw new IllegalArgumentException("无效的1.12.2 Material: " + name);
        return material;
    }

    private static DisplayItemSpec parseAnimationDisplay(ConfigurationSection animation, String child,
                                                         String defaultMaterial, int defaultData,
                                                         String defaultName) {
        if (animation != null && animation.isConfigurationSection(child)) {
            return parseDisplay(animation.getConfigurationSection(child), "animation." + child);
        }
        String materialName;
        int data;
        String name;
        if (animation != null && animation.contains("material") && "head".equals(child)) {
            materialName = animation.getString("material", defaultMaterial);
            data = animation.getInt("data", defaultData);
            name = animation.getString("name", defaultName);
        } else {
            materialName = defaultMaterial;
            data = defaultData;
            name = defaultName;
        }
        if (data < Short.MIN_VALUE || data > Short.MAX_VALUE) throw new IllegalArgumentException("animation." + child + " data超出范围。");
        return new DisplayItemSpec(material(materialName), (short) data, name, Collections.<String>emptyList());
    }

    private static List<List<Integer>> buildAnimationFrames(String preset, int size, List<Integer> backgroundSlots) {
        if (backgroundSlots == null || backgroundSlots.isEmpty()) return Collections.emptyList();
        boolean[] background = new boolean[size];
        for (Integer slot : backgroundSlots) {
            if (slot != null && slot >= 0 && slot < size) background[slot] = true;
        }

        List<List<Integer>> frames;
        if ("BORDER_CONVERGE".equals(preset)) {
            frames = borderConverge(size, background);
        } else if ("BOTTOM_SWEEP".equals(preset)) {
            frames = bottomSweep(size, background);
        } else if ("DOUBLE_SWEEP".equals(preset)) {
            frames = doubleSweep(size, background);
        } else {
            throw new IllegalArgumentException("animation.active-preset 只能是 BORDER_CONVERGE、BOTTOM_SWEEP 或 DOUBLE_SWEEP。");
        }
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("animation.active-preset 当前布局没有可用的BACKGROUND路径。");
        }
        return frames;
    }

    private static List<List<Integer>> borderConverge(int size, boolean[] background) {
        int rows = size / 9;
        List<Integer> topThenRight = new ArrayList<Integer>();
        for (int column = 0; column < 9; column++) addIfBackground(topThenRight, column, background);
        for (int row = 1; row < rows; row++) addIfBackground(topThenRight, row * 9 + 8, background);

        List<Integer> leftThenBottom = new ArrayList<Integer>();
        for (int row = 0; row < rows; row++) addIfBackground(leftThenBottom, row * 9, background);
        for (int column = 1; column < 9; column++) addIfBackground(leftThenBottom, (rows - 1) * 9 + column, background);
        return combineFrames(topThenRight, leftThenBottom);
    }

    private static List<List<Integer>> bottomSweep(int size, boolean[] background) {
        int rows = size / 9;
        List<List<Integer>> frames = new ArrayList<List<Integer>>();
        for (int column = 0; column < 9; column++) {
            int slot = (rows - 1) * 9 + column;
            if (slot >= 0 && slot < background.length && background[slot]) frames.add(Collections.singletonList(slot));
        }
        return frames;
    }

    private static List<List<Integer>> doubleSweep(int size, boolean[] background) {
        int rows = size / 9;
        List<List<Integer>> frames = new ArrayList<List<Integer>>();
        int bottomRow = rows - 1;
        for (int column = 0; column < 9; column++) {
            List<Integer> frame = new ArrayList<Integer>();
            int top = column;
            int bottom = bottomRow * 9 + column;
            if (top >= 0 && top < background.length && background[top]) frame.add(top);
            if (bottom != top && bottom >= 0 && bottom < background.length && background[bottom]) frame.add(bottom);
            if (!frame.isEmpty()) frames.add(frame);
        }
        return frames;
    }

    private static List<List<Integer>> combineFrames(List<Integer> first, List<Integer> second) {
        List<List<Integer>> frames = new ArrayList<List<Integer>>();
        int steps = Math.max(first.size(), second.size());
        for (int index = 0; index < steps; index++) {
            List<Integer> frame = new ArrayList<Integer>();
            if (index < first.size()) frame.add(first.get(index));
            if (index < second.size() && !frame.contains(second.get(index))) frame.add(second.get(index));
            if (!frame.isEmpty()) frames.add(frame);
        }
        return frames;
    }

    private static void addIfBackground(List<Integer> slots, int slot, boolean[] background) {
        if (slot >= 0 && slot < background.length && background[slot]) slots.add(slot);
    }

    public String getTitle() { return Text.color(title); }
    public int getSize() { return layout.size() * 9; }
    public List<String> getLayout() { return layout; }
    public GuiComponent getComponent(char key) { return components.get(key); }
    public List<Integer> getSlots(GuiComponentType type) { return slots.get(type); }
    public int getOnlySlot(GuiComponentType type) { return slots.get(type).get(0); }
    public boolean isAnimationEnabled() { return animationEnabled; }
    public int getAnimationInterval() { return animationInterval; }
    public int getAnimationLoops() { return animationLoops; }
    public String getAnimationPreset() { return animationPreset; }
    public List<List<Integer>> getAnimationFrames() { return animationFrames; }
    public DisplayItemSpec getAnimationDisplay() { return animationHeadDisplay; }
    public DisplayItemSpec getAnimationHeadDisplay() { return animationHeadDisplay; }
    public DisplayItemSpec getAnimationTrailDisplay() { return animationTrailDisplay; }
    public ForgeRecordSettings getForgeRecord() { return forgeRecord; }

    public String message(String key) {
        return messages.containsKey(key) ? messages.get(key) : "";
    }

    private static List<String> defaultForgeRecordLore() {
        List<String> lore = new ArrayList<String>();
        lore.add("&7------------[ &c锻造&7 ]--------------");
        lore.add("&e锻造者：&7%player_name%");
        lore.add("&e锻造时间：&7%forge_time%");
        lore.add("&7----------------------------------");
        return lore;
    }
}

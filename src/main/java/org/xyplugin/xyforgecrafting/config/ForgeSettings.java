package org.xyplugin.xyforgecrafting.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final DisplayItemSpec animationDisplay;
    private final ForgeRecordSettings forgeRecord;

    private ForgeSettings(String title, List<String> layout, Map<Character, GuiComponent> components,
                          Map<GuiComponentType, List<Integer>> slots, Map<String, String> messages,
                          boolean animationEnabled, int animationInterval, int animationLoops,
                          DisplayItemSpec animationDisplay, ForgeRecordSettings forgeRecord) {
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
        this.animationDisplay = animationDisplay;
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
        Material animationMaterial = material(animation == null ? "FIREBALL" : animation.getString("material", "FIREBALL"));
        String animationName = animation == null ? "&6锻造之火" : animation.getString("name", "&6锻造之火");
        DisplayItemSpec animationDisplay = new DisplayItemSpec(animationMaterial, (short) 0, animationName,
                Collections.<String>emptyList());

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
                animationDisplay, forgeRecord);
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

    public String getTitle() { return Text.color(title); }
    public int getSize() { return layout.size() * 9; }
    public List<String> getLayout() { return layout; }
    public GuiComponent getComponent(char key) { return components.get(key); }
    public List<Integer> getSlots(GuiComponentType type) { return slots.get(type); }
    public int getOnlySlot(GuiComponentType type) { return slots.get(type).get(0); }
    public boolean isAnimationEnabled() { return animationEnabled; }
    public int getAnimationInterval() { return animationInterval; }
    public int getAnimationLoops() { return animationLoops; }
    public DisplayItemSpec getAnimationDisplay() { return animationDisplay; }
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

package org.xyplugin.xyforgecrafting.config;

public final class GuiComponent {
    private final char key;
    private final GuiComponentType type;
    private final DisplayItemSpec display;

    public GuiComponent(char key, GuiComponentType type, DisplayItemSpec display) {
        this.key = key;
        this.type = type;
        this.display = display;
    }

    public char getKey() {
        return key;
    }

    public GuiComponentType getType() {
        return type;
    }

    public DisplayItemSpec getDisplay() {
        return display;
    }
}

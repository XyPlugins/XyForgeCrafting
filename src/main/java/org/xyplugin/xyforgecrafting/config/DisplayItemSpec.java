package org.xyplugin.xyforgecrafting.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.xyplugin.xyforgecrafting.util.Text;

public final class DisplayItemSpec {
    private final Material material;
    private final short data;
    private final String name;
    private final List<String> lore;

    public DisplayItemSpec(Material material, short data, String name, List<String> lore) {
        this.material = material;
        this.data = data;
        this.name = name;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(lore));
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(material, 1);
        item.setDurability(data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(Text.colored(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}

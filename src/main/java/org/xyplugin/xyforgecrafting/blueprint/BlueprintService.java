package org.xyplugin.xyforgecrafting.blueprint;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.xyplugin.xycore.api.XyCore;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemTagService;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;
import org.xyplugin.xyforgecrafting.util.Text;

/** Generates and verifies signed blueprint NBT without trusting visible name or lore. */
public final class BlueprintService {
    public static final String ID_TAG = "xyforge-blueprint-id";
    public static final String SCHEMA_TAG = "xyforge-blueprint-schema";
    public static final String SIGNATURE_TAG = "xyforge-blueprint-signature";
    private static final String SCHEMA = "1";

    private final XyForgeCraftingPlugin plugin;
    private final ItemLibraryService items;
    private final ItemTagService tags;
    private final byte[] secret;

    public BlueprintService(XyForgeCraftingPlugin plugin) throws Exception {
        this.plugin = plugin;
        this.items = XyCore.get().getItems();
        this.tags = XyCore.get().getItemTags();
        if (!tags.isAvailable()) throw new IllegalStateException("XyCore物品NBT服务不可用。");
        this.secret = loadOrCreateSecret(new File(plugin.getDataFolder(), "blueprint-secret.key"));
    }

    public Optional<ItemStack> create(RecipeDefinition recipe, int amount) {
        if (recipe == null || amount <= 0 || amount > 64) return Optional.empty();
        Optional<ItemStack> base = items.create(recipe.getBlueprint().getTemplate(), amount);
        if (!base.isPresent()) return Optional.empty();
        ItemStack blueprint = base.get();
        ItemMeta meta = blueprint.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(recipe.getBlueprint().getDisplayName()));
            meta.setLore(Text.colored(recipe.getBlueprint().getLore()));
            blueprint.setItemMeta(meta);
        }
        blueprint = tags.setString(blueprint, ID_TAG, recipe.getId());
        blueprint = tags.setString(blueprint, SCHEMA_TAG, SCHEMA);
        blueprint = tags.setString(blueprint, SIGNATURE_TAG, sign(recipe.getId(), SCHEMA));
        blueprint.setAmount(amount);
        return Optional.of(blueprint);
    }

    public Optional<RecipeDefinition> identify(ItemStack item) {
        if (item == null || item.getAmount() <= 0) return Optional.empty();
        String id = tags.getString(item, ID_TAG).orElse("");
        String schema = tags.getString(item, SCHEMA_TAG).orElse("");
        String signature = tags.getString(item, SIGNATURE_TAG).orElse("");
        if (id.isEmpty() || !SCHEMA.equals(schema) || signature.isEmpty()) return Optional.empty();
        String expected = sign(id, schema);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        return plugin.getRecipeRegistry().find(id);
    }

    public boolean hasBlueprintIdentity(ItemStack item) {
        return item != null && tags.getString(item, ID_TAG).isPresent();
    }

    private String sign(String id, String schema) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal((id + "|" + schema).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("无法签名锻造图纸", failure);
        }
    }

    private byte[] loadOrCreateSecret(File file) throws Exception {
        if (file.exists()) {
            String encoded = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length < 32) throw new IllegalStateException("blueprint-secret.key长度无效，禁止自动替换现有密钥。");
            return decoded;
        }
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建插件数据目录。");
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        Files.write(file.toPath(), Base64.getEncoder().encode(generated));
        plugin.getLogger().info("已生成图纸签名密钥 blueprint-secret.key，请随服务器数据一起备份。");
        return generated;
    }
}

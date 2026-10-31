package dev.qeiwtpets;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PetMenu implements InventoryHolder {
    private final Qeiwtpets plugin;
    private final Inventory inv;
    private final Map<Integer, String> slots = new HashMap<>();

    public PetMenu(Qeiwtpets plugin, Player viewer) {
        this.plugin = plugin;
        int count = plugin.registry().all().size();
        int size = Math.min(54, Math.max(27, ((count - 1) / 9 + 1) * 9));
        this.inv = Bukkit.createInventory(this, size,
                Text.c(plugin.getConfig().getString("gui.title", "&8Pets")));
        render(viewer);
    }

    /** Pet id shown in the given slot, or null. */
    public String petAt(int slot) { return slots.get(slot); }

    public void render(Player viewer) {
        inv.clear();
        slots.clear();
        List<String> activeIds = plugin.store().getActive(viewer.getUniqueId());
        int slot = 0;
        for (PetType pet : plugin.registry().all()) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, buildItem(viewer, pet, activeIds.contains(pet.id())));
            slots.put(slot, pet.id());
            slot++;
        }
    }

    private ItemStack buildItem(Player viewer, PetType pet, boolean isActive) {
        long xp = plugin.store().getXp(viewer.getUniqueId(), pet.id());
        int level = pet.levelFor(xp);
        int next = pet.nextRequirement(xp);
        boolean allowed = pet.permission().isEmpty() || viewer.hasPermission(pet.permission());

        ItemStack item = new ItemStack(pet.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(pet.name()));

        List<Component> lore = new ArrayList<>();
        for (String line : pet.description()) lore.add(Text.c("&7" + line));
        lore.add(Component.empty());
        lore.add(Text.c("&eLevel: &f" + level + "&7/" + pet.maxLevel()));
        if (next > 0) lore.add(Text.c("&eXP: &f" + pet.progress(xp) + "&7/" + next));
        else lore.add(Text.c("&aMax level"));
        lore.add(Component.empty());
        if (!allowed) lore.add(Text.c("&cLocked"));
        else if (isActive) lore.add(Text.c("&cClick to deactivate"));
        else lore.add(Text.c("&aClick to activate"));

        meta.lore(lore);
        if (isActive) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() { return inv; }
}

package dev.qeiwtpets;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Qeiwtpets extends JavaPlugin implements Listener, TabExecutor {
    private PetRegistry registry;
    private PlayerStore store;
    private PetManager manager;

    public PetRegistry registry() { return registry; }
    public PlayerStore store() { return store; }
    public PetManager manager() { return manager; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registry = new PetRegistry(this);
        registry.load();
        store = new PlayerStore(this);
        manager = new PetManager(this);
        manager.start();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("pets").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.despawnAll();
        if (store != null) store.save();
    }

    // ---------- events ----------

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { manager.despawn(e.getPlayer()); }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof PetMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof PetMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        String id = menu.petAt(e.getRawSlot());
        if (id == null) return;
        toggle(p, id);
        menu.render(p);
    }

    private void toggle(Player p, String id) {
        PetType pet = registry.get(id);
        if (pet == null) return;
        if (!pet.permission().isEmpty() && !p.hasPermission(pet.permission())) {
            msg(p, "no-permission", "&cYou don't have access to this pet.", pet.name());
            return;
        }
        List<String> active = store.getActive(p.getUniqueId());
        if (active.contains(id)) {
            active.remove(id);
            store.setActive(p.getUniqueId(), active);
            manager.refresh(p);
            msg(p, "deactivated", "&7Deactivated pet: %pet%", pet.name());
        } else {
            if (active.size() >= getConfig().getInt("max-active-pets", 2)) {
                msg(p, "limit-reached", "&cYou can only have %max% active pets. Deactivate one first.", pet.name());
                return;
            }
            active.add(id);
            store.setActive(p.getUniqueId(), active);
            manager.refresh(p);
            msg(p, "activated", "&aActivated pet: %pet%", pet.name());
        }
    }

    private void msg(Player p, String key, String def, String petName) {
        String text = getConfig().getString("messages." + key, def)
                .replace("%pet%", petName)
                .replace("%max%", String.valueOf(getConfig().getInt("max-active-pets", 2)));
        p.sendMessage(Text.c(text));
    }

    // ---------- commands ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("qeiwtpets.use")) { p.sendMessage(Text.c("&cNo permission.")); return true; }
            p.openInventory(new PetMenu(this, p).getInventory());
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("qeiwtpets.admin")) { sender.sendMessage("No permission."); return true; }
                reloadConfig();
                registry.load();
                manager.refreshAll();
                sender.sendMessage("Qeiwtpets reloaded.");
            }
            case "givexp" -> {
                if (!sender.hasPermission("qeiwtpets.admin")) { sender.sendMessage("No permission."); return true; }
                if (args.length < 4) { sender.sendMessage("Usage: /pets givexp <player> <pet> <amount>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                PetType pet = registry.get(args[2].toLowerCase(Locale.ROOT));
                if (target == null) { sender.sendMessage("Player not found."); return true; }
                if (pet == null) { sender.sendMessage("Unknown pet."); return true; }
                long amount;
                try { amount = Long.parseLong(args[3]); }
                catch (NumberFormatException ex) { sender.sendMessage("Amount must be a number."); return true; }
                store.addXp(target.getUniqueId(), pet.id(), amount);
                manager.applyAbilities(target);
                sender.sendMessage("Gave " + amount + " XP to " + target.getName() + " for " + pet.id() + ".");
            }
            default -> sender.sendMessage("Usage: /pets [reload|givexp <player> <pet> <amount>]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("qeiwtpets.admin")) return out;
        if (args.length == 1) {
            out.add("reload");
            out.add("givexp");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("givexp")) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("givexp")) {
            registry.all().forEach(pt -> out.add(pt.id()));
        }
        return out;
    }
}

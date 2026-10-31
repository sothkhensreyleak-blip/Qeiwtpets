package dev.qeiwtpets;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spawns the floating pet displays and applies abilities. */
public class PetManager {
    private final Qeiwtpets plugin;
    // player -> (pet id -> floating display)
    private final Map<UUID, Map<String, ItemDisplay>> displays = new HashMap<>();
    private long tick = 0;

    public PetManager(Qeiwtpets plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 1L, 2L);
    }

    /** The pets the player has active (and may use), limited by max-active-pets. */
    public List<PetType> activePets(Player p) {
        int max = plugin.getConfig().getInt("max-active-pets", 2);
        List<PetType> result = new ArrayList<>();
        for (String id : plugin.store().getActive(p.getUniqueId())) {
            PetType pet = plugin.registry().get(id);
            if (pet == null) continue;
            if (!pet.permission().isEmpty() && !p.hasPermission(pet.permission())) continue;
            result.add(pet);
            if (result.size() >= max) break;
        }
        return result;
    }

    private void update() {
        tick += 2;
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<PetType> pets = activePets(p);
            if (pets.isEmpty()) { despawn(p); continue; }

            Map<String, ItemDisplay> current = displays.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());

            // remove displays that are no longer wanted or broke (unloaded chunk, world change...)
            current.entrySet().removeIf(en -> {
                ItemDisplay d = en.getValue();
                boolean wanted = pets.stream().anyMatch(x -> x.id().equals(en.getKey()));
                boolean keep = wanted && d.isValid() && d.getWorld() == p.getWorld();
                if (!keep) d.remove();
                return !keep;
            });

            for (int i = 0; i < pets.size(); i++) {
                PetType pet = pets.get(i);
                ItemDisplay d = current.get(pet.id());
                if (d == null) {
                    d = spawn(p, pet, i);
                    current.put(pet.id(), d);
                }
                d.teleport(floatLocation(p, i));
            }

            if (tick % 40 == 0) applyAbilities(p);
        }
    }

    private Location floatLocation(Player p, int index) {
        Location base = p.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        // right-hand vector and "behind" vector relative to where the player faces
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double bx = Math.sin(yaw), bz = -Math.cos(yaw);
        double side = (index % 2 == 0) ? 1.0 : -1.0; // first pet on the right, second on the left
        double bob = Math.sin(tick * 0.1 + index * Math.PI) * 0.15;

        Location loc = base.clone().add(
                rx * 0.9 * side + bx * 0.6,
                1.6 + bob,
                rz * 0.9 * side + bz * 0.6);
        loc.setYaw((float) ((tick * 6 * side) % 360));
        loc.setPitch(0);
        return loc;
    }

    private ItemDisplay spawn(Player p, PetType pet, int index) {
        float s = pet.scale();
        ItemDisplay d = p.getWorld().spawn(floatLocation(p, index), ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(pet.displayItem()));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTeleportDuration(2);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f()));
        });
        applyAbilities(p);
        return d;
    }

    /**
     * Applies the abilities of all active pets. If several pets give the same
     * effect, only the strongest amplifier is used (no stacking).
     */
    public void applyAbilities(Player p) {
        Map<PotionEffectType, Integer> best = new HashMap<>();
        for (PetType pet : activePets(p)) {
            int level = pet.levelFor(plugin.store().getXp(p.getUniqueId(), pet.id()));
            for (PetType.Ability a : pet.abilities()) {
                if (level >= a.minLevel()) best.merge(a.effect(), a.amplifier(), Math::max);
            }
        }
        best.forEach((type, amp) -> p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, true)));
    }

    public void despawn(Player p) {
        Map<String, ItemDisplay> m = displays.remove(p.getUniqueId());
        if (m != null) m.values().forEach(ItemDisplay::remove);
    }

    public void despawnAll() {
        displays.values().forEach(m -> m.values().forEach(ItemDisplay::remove));
        displays.clear();
    }

    /** Re-create the pet displays right away (after activate / reload). */
    public void refresh(Player p) {
        despawn(p);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }
}

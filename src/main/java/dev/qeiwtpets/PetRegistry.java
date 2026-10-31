package dev.qeiwtpets;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

public class PetRegistry {
    private final Qeiwtpets plugin;
    private final Map<String, PetType> pets = new LinkedHashMap<>();

    public PetRegistry(Qeiwtpets plugin) { this.plugin = plugin; }

    public PetType get(String id) { return id == null ? null : pets.get(id); }

    public Collection<PetType> all() { return pets.values(); }

    public void load() {
        pets.clear();
        File dir = new File(plugin.getDataFolder(), "pets");
        if (!dir.exists()) {
            for (String n : List.of("gojo", "luffy")) plugin.saveResource("pets/" + n + ".yml", false);
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            try {
                PetType pet = parse(f);
                pets.put(pet.id(), pet);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load pet " + f.getName() + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + pets.size() + " pet(s).");
    }

    private PetType parse(File f) {
        String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT).replace('.', '_');
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        Material icon = Material.matchMaterial(y.getString("icon", "BONE"));
        if (icon == null) icon = Material.BARRIER;
        Material display = Material.matchMaterial(y.getString("display-item", icon.name()));
        if (display == null) display = icon;

        List<PetType.Ability> abilities = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("abilities")) {
            String effectName = String.valueOf(m.get("effect")).toLowerCase(Locale.ROOT);
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(effectName));
            if (type == null) {
                plugin.getLogger().warning(f.getName() + ": unknown effect '" + effectName + "'");
                continue;
            }
            abilities.add(new PetType.Ability(type, num(m.get("amplifier"), 0), num(m.get("min-level"), 1)));
        }

        return new PetType(id,
                y.getString("name", id),
                y.getStringList("description"),
                icon, display,
                (float) y.getDouble("scale", 0.7),
                y.getString("permission", ""),
                y.getIntegerList("level-xp-requirements"),
                abilities);
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }
}

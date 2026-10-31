package dev.qeiwtpets;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerStore {
    private final Qeiwtpets plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PlayerStore(Qeiwtpets plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Active pet ids, in the order they were activated (also reads the old single-pet format). */
    public List<String> getActive(UUID id) {
        String path = id + ".active";
        if (yaml.isList(path)) return new ArrayList<>(yaml.getStringList(path));
        List<String> list = new ArrayList<>();
        String single = yaml.getString(path);
        if (single != null) list.add(single);
        return list;
    }

    public void setActive(UUID id, List<String> petIds) {
        yaml.set(id + ".active", petIds.isEmpty() ? null : new ArrayList<>(petIds));
        save();
    }

    public long getXp(UUID id, String petId) { return yaml.getLong(id + ".xp." + petId, 0L); }

    public void addXp(UUID id, String petId, long amount) {
        yaml.set(id + ".xp." + petId, Math.max(0L, getXp(id, petId) + amount));
        save();
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}

package dev.qeiwtpets;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public record PetType(String id, String name, List<String> description, Material icon,
                      Material displayItem, float scale, String permission,
                      List<Integer> xpRequirements, List<Ability> abilities) {

    public record Ability(PotionEffectType effect, int amplifier, int minLevel) {}

    public int maxLevel() { return xpRequirements.size() + 1; }

    public int levelFor(long xp) {
        int level = 1;
        for (int req : xpRequirements) {
            if (xp >= req) { xp -= req; level++; } else break;
        }
        return level;
    }

    /** XP gathered inside the current level. */
    public long progress(long xp) {
        for (int req : xpRequirements) {
            if (xp >= req) xp -= req; else return xp;
        }
        return 0;
    }

    /** XP needed for the next level, or -1 if the pet is max level. */
    public int nextRequirement(long xp) {
        for (int req : xpRequirements) {
            if (xp >= req) xp -= req; else return req;
        }
        return -1;
    }
}

package me.Iretemi.itemBan;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ForbiddenManager {
    private static final Set<Material> forbidden = new HashSet<>();

    public static void load(JavaPlugin plugin) {
        forbidden.clear();
        for (String s : plugin.getConfig().getStringList("forbidden-items")) {
            Material mat = Material.matchMaterial(s);
            if (mat != null) forbidden.add(mat);
        }
    }

    public static boolean isForbidden(ItemStack item) {
        return item != null && forbidden.contains(item.getType());
    }

    public static boolean add(JavaPlugin plugin, Material mat) {
        if (forbidden.contains(mat)) return false;

        List<String> list = plugin.getConfig().getStringList("forbidden-items");
        list.add(mat.name());
        plugin.getConfig().set("forbidden-items", list);
        plugin.saveConfig();

        forbidden.add(mat);
        return true;
    }

    public static boolean remove(JavaPlugin plugin, Material mat) {
        if (!forbidden.contains(mat)) return false;

        List<String> list = plugin.getConfig().getStringList("forbidden-items");
        list.remove(mat.name());
        plugin.getConfig().set("forbidden-items", list);
        plugin.saveConfig();

        forbidden.remove(mat);
        return true;
    }

    public static boolean isForbidden(Material mat) {
        return forbidden.contains(mat);
    }
}

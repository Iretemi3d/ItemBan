package me.Iretemi.itemBan;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import me.Iretemi.itemBan.ForbiddenManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemBan extends JavaPlugin implements CommandExecutor, Listener {
    private JavaPlugin plugin = this;
    private Map<Material, Integer> itemLimits = new HashMap<>();
    
    @Override
    public void onEnable() {
        getCommand("ItemBan").setExecutor(this);
        saveDefaultConfig();
        ForbiddenManager.load(this);
        loadItemLimits();

        int pluginId = 29741;
        Metrics metrics = new Metrics(this, pluginId);
        UpdateChecker updateChecker = new UpdateChecker(this, "item-ban");
        updateChecker.checkForUpdates();
        getServer().getPluginManager().registerEvents(updateChecker, this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PickupListener(), this);
        getServer().getPluginManager().registerEvents(new CraftListener(), this);
        getServer().getPluginManager().registerEvents(new LootListener(), this);
        check();
        getLogger().info("BanItems Enabled! Made by AmethystMC.dev");
    }


    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            return List.of("reload", "ban", "unban", "list");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("ban")  || args[0].equalsIgnoreCase("unban"))) {
            for (Material mat : Material.values()) {
                if (mat.isItem()) {
                    completions.add(mat.name());
                }
            }

            return StringUtil.copyPartialMatches(
                    args[1].toUpperCase(),
                    completions,
                    new ArrayList<>());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.isOp()) {
            sender.sendMessage("You must be an operator to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /ItemBan [reload | unban | list | ban]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadItemLimits();
            for (Player p : Bukkit.getOnlinePlayers()) {
                purgeInventory(p);
                enforceItemLimits(p);
            }
            sender.sendMessage("Config reloaded.");
            return true;
        }


        if (args[0].equalsIgnoreCase("list")) {
            for (String s : getConfig().getStringList("forbidden-items")) {sender.sendMessage(s);}
            return true;
        }

        Material mat = Material.matchMaterial(args[1].toUpperCase());
        if (mat == null) {
            sender.sendMessage("§cInvalid material.");
            return true;
        }

        if (args[0].equalsIgnoreCase("ban")) {
            if (ForbiddenManager.add(this, mat)) {
                sender.sendMessage("§aAdded " + mat.name() + " to forbidden list.");
                reloadConfig();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    purgeInventory(p);
                }
            } else {
                sender.sendMessage("§eItem already forbidden.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("unban")) {
            if (ForbiddenManager.remove(this, mat)) {
                sender.sendMessage("§aRemoved " + mat.name() + " from forbidden list.");
                reloadConfig();
            } else {
                sender.sendMessage("§eItem not in forbidden list.");
            }
            return true;

        }
        return true;
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (ForbiddenManager.isForbidden(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockDrop(BlockDropItemEvent event) {
        event.getItems().removeIf(
                item -> ForbiddenManager.isForbidden(item.getItemStack())
        );
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        event.getDrops().removeIf(ForbiddenManager::isForbidden);
    }

    @EventHandler
    public void onSmelt(FurnaceSmeltEvent event) {
        if (ForbiddenManager.isForbidden(event.getResult())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTrade(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory inv)) return;

        ItemStack result = inv.getSelectedRecipe().getResult();
        if (ForbiddenManager.isForbidden(result)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();

        if (ForbiddenManager.isForbidden(result)) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                purgeInventory(player);
                enforceItemLimits(player);
            }
        }.runTaskLater(this, 20L * 5);

    }

    public void check() {

        new BukkitRunnable(){
            @Override
            public void run() {
                reloadConfig();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    purgeInventory(player);
                }
            }
        }.runTaskTimer(this, 20L * 60*5,20L * 60 * 3); // check every 3 minutes
    }



    public void purgeInventory(Player player) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (ForbiddenManager.isForbidden(item)) {
                    player.getInventory().remove(item);

        }
        }
    }

    public class CraftListener implements Listener {
        @EventHandler
        public void onCraft(CraftItemEvent event) {
            if (ForbiddenManager.isForbidden(event.getRecipe().getResult())) {
                event.setCancelled(true);
            }
        }
    }

    public class LootListener implements Listener {
        @EventHandler
        public void onLootGenerate(LootGenerateEvent event) {
            event.getLoot().removeIf(ForbiddenManager::isForbidden);
        }
    }

    public class PickupListener implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onPickup(EntityPickupItemEvent event) {
            if (!(event.getEntity() instanceof Player player)) return;

            ItemStack item = event.getItem().getItemStack();
            if (ForbiddenManager.isForbidden(item)) {
                event.setCancelled(true);
                event.getItem().remove();
                return;
            }

            // Check if picking this up would exceed the limit
            Material mat = item.getType();
            if (itemLimits.containsKey(mat)) {
                int limit = itemLimits.get(mat);
                int current = 0;
                for (ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem != null && invItem.getType() == mat) {
                        current += invItem.getAmount();
                    }
                }
                int incoming = item.getAmount();
                if (current + incoming > limit) {
                    event.setCancelled(true);
                    int canTake = limit - current;
                    if (canTake > 0) {
                        ItemStack partial = item.clone();
                        partial.setAmount(canTake);
                        player.getInventory().addItem(partial);
                        item.setAmount(incoming - canTake);
                        event.getItem().setItemStack(item);
                    }
                }
            }

        }
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                enforceItemLimits(p);
                purgeInventory(p);
            });
        }
    }

    private void loadItemLimits() {
        itemLimits.clear();
        if (getConfig().contains("item-limits")) {
            for (String key : getConfig().getConfigurationSection("item-limits").getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) {
                    itemLimits.put(mat, getConfig().getInt("item-limits." + key));
                } else {
                    getLogger().warning("Invalid material in item-limits: " + key);
                }
            }
        }
        getLogger().info("Loaded " + itemLimits.size() + " item limits.");
    }

    public void enforceItemLimits(Player player) {
        PlayerInventory inv = player.getInventory();

        for (Map.Entry<Material, Integer> entry : itemLimits.entrySet()) {
            Material mat = entry.getKey();
            int limit = entry.getValue();

            int total = 0;

            // First pass: count
            for (ItemStack item : inv.getContents()) {
                if (item == null || item.getType() != mat) continue;
                total += item.getAmount();
            }

            if (total <= limit) continue;

            int toRemove = total - limit;

            // Second pass: remove excess and DROP them
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() != mat) continue;

                int remove = Math.min(item.getAmount(), toRemove);
                toRemove -= remove;

                // Drop excess items
                ItemStack toDrop = item.clone();
                toDrop.setAmount(remove);
                player.getWorld().dropItemNaturally(player.getLocation(), toDrop);

                if (remove >= item.getAmount()) {
                    inv.setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remove);
                    inv.setItem(i, item);
                }

                if (toRemove <= 0) break;
            }
        }
    }
    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

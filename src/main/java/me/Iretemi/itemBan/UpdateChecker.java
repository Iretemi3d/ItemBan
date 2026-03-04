package me.Iretemi.itemBan;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.logging.Level;

public class UpdateChecker implements Listener {

    private final JavaPlugin plugin;
    private final String modrinthSlug;
    private String latestVersion = null;
    private boolean updateAvailable = false;
    private String currentVersion;

    public UpdateChecker(JavaPlugin plugin, String modrinthSlug) {
        this.plugin = plugin;
        this.modrinthSlug = modrinthSlug;
    }

    /**
     * Checks for updates asynchronously. Call this in onEnable().
     */
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URI uri = new URI("https://api.modrinth.com/v2/project/" + modrinthSlug + "/version");
                HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", plugin.getName() + "/" + plugin.getPluginMeta().getVersion());
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                if (con.getResponseCode() != 200) {
                    plugin.getLogger().warning("Update check failed: HTTP " + con.getResponseCode());
                    return;
                }

                JsonArray versions = JsonParser.parseReader(
                        new InputStreamReader(con.getInputStream())
                ).getAsJsonArray();

                if (versions.isEmpty()) return;

                // First element is the latest version
                JsonObject latest = versions.get(0).getAsJsonObject();
                latestVersion = latest.get("version_number").getAsString();

                currentVersion = plugin.getPluginMeta().getVersion();

                if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                    updateAvailable = true;
                    plugin.getLogger().info("A new version is available: " + latestVersion
                            + " (current: " + currentVersion + ")");
                    plugin.getLogger().info("Download: https://modrinth.com/plugin/" + modrinthSlug);
                } else {
                    plugin.getLogger().info("You are running the latest version (" + currentVersion + ").");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates", e);
            }
        });
    }

    /**
     * Notifies operators when they join if an update is available.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;

        Player player = event.getPlayer();
        if (player.isOp()) {
            player.sendMessage("§e[ItemBan] §fA new version of ItemBan is available");
            player.sendMessage("Current version: §a" + currentVersion + "Latest version: §a" + latestVersion);
            player.sendMessage("§e[ItemBan] §fDownload: §bhttps://modrinth.com/plugin/" + modrinthSlug);
        }
    }
}

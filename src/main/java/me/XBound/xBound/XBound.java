package me.XBound.xBound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class XBound extends JavaPlugin {

    private int baseBorderSize;
    private int xpPerBlock;
    private int minBorderSize;
    private World world;
    private final Map<UUID, Integer> storedXp = new HashMap<>();
    private File dataFile;
    private YamlConfiguration dataConfig;
    private String webhookUrl;
    private FileConfiguration config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<UUID, net.kyori.adventure.text.Component> prefixes = new HashMap<>();
    private final Map<UUID, net.kyori.adventure.text.Component> suffixes = new HashMap<>();
    private static XBound instance;
    private File rulesFile;

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        config = getConfig();

        loadConfig();
        setupDataFile();
        loadDataFile();
        loadPrefixSuffix();

        // load stored player xp for currently online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadPlayerXp(p.getUniqueId());
        }

        restoreBorder();
        startBorderCheckTask();

        updateTablist();

        // register commands
        Objects.requireNonNull(getCommand("xbound")).setExecutor(new me.XBound.xBound.commands.XBoundCommand(this, prefixes, suffixes));
        Objects.requireNonNull(getCommand("discord")).setExecutor(new me.XBound.xBound.commands.DiscordCommand(this));
        Objects.requireNonNull(getCommand("rules")).setExecutor(new me.XBound.xBound.commands.RulesCommand(this));
        // note: no separate BorderCenterCommand - bordercenter handled as subcommand of /xbound

        // register listeners (some command classes register their own listeners)
        Bukkit.getPluginManager().registerEvents(new me.XBound.xBound.listeners.CoreListeners(this), this);

        webhookUrl = config.getString("webhook-url", "");

        getLogger().info("XBound enabled. Instance set. Global border operating on stored XP.");
    }

    @Override
    public void onDisable() {
        // persist player xp
        Bukkit.getOnlinePlayers().forEach(p -> savePlayerXp(p.getUniqueId()));

        saveBorder();
        saveDataFile();

        savePrefixSuffix();

        if (config.getBoolean("events.server-stop", true)) {
            sendToDiscord(config.getString("messages.server-stop", "Server stopping"));
        }

        getLogger().info("XBound disabled.");
    }

    public static XBound getInstance() {
        return instance;
    }

    public YamlConfiguration getDataConfig() {
        return dataConfig;
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();
        baseBorderSize = cfg.getInt("base-border-size", 50);
        xpPerBlock = cfg.getInt("xp-per-block", 10);
        double xpStealPercent = cfg.getDouble("xp-steal-percent", 50) / 100.0;
        minBorderSize = cfg.getInt("min-border-size", 20);

        String worldName = cfg.getString("world", null);
        if (worldName != null) {
            world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().severe("Configured world '" + worldName + "' not found! Defaulting to first world.");
            }
        }
        if (world == null) world = Bukkit.getWorlds().getFirst();
    }

    private void setupDataFile() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().severe("Failed to create plugin data folder!");
        }

        dataFile = new File(dataFolder, "data.yml");
        if (!dataFile.exists()) {
            try {
                saveResource("data.yml", false);
            } catch (Exception e) {
                try {
                    if (!dataFile.createNewFile()) {
                        getLogger().severe("Failed to create data.yml!");
                    }
                } catch (IOException ex) {
                    getLogger().log(Level.SEVERE, "Error while creating data.yml", ex);
                }
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveDataFile() {
        if (dataConfig == null || dataFile == null) return;
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    /**
     * Recomputes global border size from the combined stored XP of all players.
     */
    public synchronized void updateBorder() {
        int totalXP = storedXp.values().stream().mapToInt(Integer::intValue).sum();
        double extra = (double) totalXP / Math.max(1, xpPerBlock);
        double newSize = Math.max(minBorderSize, baseBorderSize + Math.floor(extra));
        WorldBorder border = world.getWorldBorder();
        border.setSize(newSize);
        saveBorder();
    }

    private void restoreBorder() {
        if (dataConfig == null) return;
        double size = dataConfig.getDouble("border.size", baseBorderSize);
        double centerX = dataConfig.getDouble("border.center-x", world.getSpawnLocation().getX());
        double centerZ = dataConfig.getDouble("border.center-z", world.getSpawnLocation().getZ());
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
    }

    private void startBorderCheckTask() {
        // runs every second
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double centerX = world.getWorldBorder().getCenter().getX();
            double centerZ = world.getWorldBorder().getCenter().getZ();
            double radius = world.getWorldBorder().getSize() / 2.0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(world)) continue;
                if (player.hasPermission("xbound.bypassborder")) continue;

                double dx = Math.abs(player.getLocation().getX() - centerX);
                double dz = Math.abs(player.getLocation().getZ() - centerZ);

                if (dx > radius || dz > radius) {
                    // Teleport safely to border center (async chunk load then sync teleport)
                    int bx = (int) Math.floor(centerX);
                    int bz = (int) Math.floor(centerZ);
                    CompletableFuture.runAsync(() -> {
                        // load chunk async via Paper API if available; fallback to sync chunk load
                        try {
                            // Paper provides world.getChunkAtAsync; use reflection-like safe call
                            world.getChunkAtAsync(bx, bz).join();
                        } catch (Throwable ignored) {
                            // fallback: ensure chunk is loaded sync (less ideal)
                            world.getChunkAt(bx, bz);
                        }
                    }).thenRun(() -> {
                        Bukkit.getScheduler().runTask(this, () -> {
                            Location safe = findSafeLocationNear(world, bx, bz);
                            if (safe == null) {
                                // fallback: highest block
                                safe = world.getHighestBlockAt(bx, bz).getLocation().add(0.5, 1, 0.5);
                            }
                            player.teleport(safe);
                            player.sendMessage(Component.text("§cYou went out of the border! Teleporting you back to the center."));
                        });
                    });
                }
            }
        }, 20L, 20L);
    }

    /**
     * Finds a safe surface location at (x,z) scanning down from highest -> minHeight.
     * Returns Location with +0.5 offsets on x/z and y set to the first safe standing position (y+1).
     */
    private Location findSafeLocationNear(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z);
        int minY = world.getMinHeight();
        for (int y = highestY; y >= minY; y--) {
            org.bukkit.block.Block floor = world.getBlockAt(x, y, z);
            org.bukkit.block.Block above = world.getBlockAt(x, y + 1, z);
            org.bukkit.block.Block above2 = world.getBlockAt(x, y + 2, z);

            // must be solid to stand on, above blocks must be non-solid and non-liquid
            boolean floorSolid = floor.getType().isSolid();
            boolean aboveFree = !above.getType().isSolid() && !above.isLiquid();
            boolean above2Free = !above2.getType().isSolid() && !above2.isLiquid();

            if (floorSolid && aboveFree && above2Free) {
                // avoid cactus/magma/lava floor
                if (floor.getType().name().contains("CACTUS") ||
                        floor.getType().name().contains("MAGMA") ||
                        floor.getType().name().contains("LAVA")) {
                    continue;
                }
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    public void savePrefixSuffix() {
        if (dataConfig == null) return;
        for (UUID uuid : prefixes.keySet()) {
            dataConfig.set("prefixes." + uuid.toString(), GSON.serialize(prefixes.get(uuid)));
        }
        for (UUID uuid : suffixes.keySet()) {
            dataConfig.set("suffixes." + uuid.toString(), GSON.serialize(suffixes.get(uuid)));
        }
        saveDataFile();
    }

    private void loadPrefixSuffix() {
        if (dataConfig == null) return;
        if (dataConfig.isConfigurationSection("prefixes")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("prefixes")).getKeys(false)) {
                String json = dataConfig.getString("prefixes." + key, null);
                if (json != null) {
                    try {
                        prefixes.put(UUID.fromString(key), GSON.deserialize(json));
                    } catch (Exception e) {
                        getLogger().warning("Failed to deserialize prefix for " + key + ": " + e.getMessage());
                    }
                }
            }
        }
        if (dataConfig.isConfigurationSection("suffixes")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("suffixes")).getKeys(false)) {
                String json = dataConfig.getString("suffixes." + key, null);
                if (json != null) {
                    try {
                        suffixes.put(UUID.fromString(key), GSON.deserialize(json));
                    } catch (Exception e) {
                        getLogger().warning("Failed to deserialize suffix for " + key + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private void loadDataFile() {
        if (dataFile == null) dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Called by listeners when a player should have xp changed in storedXp map.
     */
    public void modifyStoredXp(UUID uuid, int delta) {
        storedXp.put(uuid, Math.max(0, storedXp.getOrDefault(uuid, 0) + delta));
        updateBorder();
    }

    public Map<UUID, Integer> getStoredXpMap() {
        return Collections.unmodifiableMap(storedXp);
    }

    /**
     * Sends a message to Discord webhook asynchronously.
     */
    public void sendToDiscord(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        if (message == null || message.isEmpty()) return;

        String jsonPayload = "{\"content\":\"" + message.replace("\"", "\\\"") + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    getLogger().warning("Failed to send message to Discord: " + e.getMessage());
                    return null;
                });
    }

    public Map<UUID, Component> getPrefixes() {
        return Collections.unmodifiableMap(prefixes);
    }

    public Map<UUID, Component> getSuffixes() {
        return Collections.unmodifiableMap(suffixes);
    }

    public void updatePlayerName(org.bukkit.entity.Player player) {
        // update player list name using Adventure Component
        Component name = Component.empty();
        if (prefixes.containsKey(player.getUniqueId())) {
            name = name.append(prefixes.get(player.getUniqueId())).append(Component.text(" "));
        }
        name = name.append(Component.text(player.getName()));
        if (suffixes.containsKey(player.getUniqueId())) {
            name = name.append(Component.text(" ")).append(suffixes.get(player.getUniqueId()));
        }
        player.playerListName(name);
    }

    private void updateTablist() {
        List<String> headerLines = getConfig().getStringList("tablist.header");
        List<String> footerLines = getConfig().getStringList("tablist.footer");
        String header = String.join("\n", headerLines);
        String footer = String.join("\n", footerLines);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeader(net.kyori.adventure.text.Component.text(header));
            player.sendPlayerListFooter(net.kyori.adventure.text.Component.text(footer));
        }
    }

    public Map<UUID, Integer> getStoredXp() {
        return storedXp;
    }

    // make public
    public void savePlayerXp(UUID uuid) {
        dataConfig.set("players." + uuid, storedXp.getOrDefault(uuid, 0));
    }

    public void loadPlayerXp(UUID uuid) {
        int xp = dataConfig.getInt("players." + uuid, 0);
        storedXp.put(uuid, xp);
    }

    public void saveBorder() {
        dataConfig.set("border.size", world.getWorldBorder().getSize());
        dataConfig.set("border.center-x", world.getWorldBorder().getCenter().getX());
        dataConfig.set("border.center-z", world.getWorldBorder().getCenter().getZ());
    }
}
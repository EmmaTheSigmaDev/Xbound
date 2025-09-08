package me.XBound.xBound;

import me.XBound.xBound.managers.MessageManager;
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

// LuckPerms imports
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

public final class XBound extends JavaPlugin {

    private int baseBorderSize;
    private int xpPerBlock;
    private int minBorderSize;
    private World world;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private String webhookUrl;
    private FileConfiguration config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<UUID, Component> prefixes = new HashMap<>();
    private final Map<UUID, Component> suffixes = new HashMap<>();
    private static XBound instance;
    private MessageManager messageManager;
    private final Map<UUID, Double> storedXp = new HashMap<>();

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private boolean useLuckPerms = false;
    private LuckPerms luckPermsApi;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        config = getConfig();

        setupLuckPerms();
        loadConfig();
        setupDataFile();
        loadDataFile();
        if (!useLuckPerms) loadPrefixSuffix();

        messageManager = new MessageManager(this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            loadPlayerXp(p.getUniqueId());
            updatePlayerName(p);
        }

        restoreBorder();
        startBorderCheckTask();
        updateTablist();

        Objects.requireNonNull(getCommand("xbound")).setExecutor(new me.XBound.xBound.commands.XBoundCommand(this, prefixes, suffixes));
        Objects.requireNonNull(getCommand("discord")).setExecutor(new me.XBound.xBound.commands.DiscordCommand(this));
        Objects.requireNonNull(getCommand("rules")).setExecutor(new me.XBound.xBound.commands.RulesCommand(this));
        Bukkit.getPluginManager().registerEvents(new me.XBound.xBound.listeners.CoreListeners(this), this);
        Bukkit.getPluginManager().registerEvents(new me.XBound.xBound.listeners.MiningXpListener(this), this);

        webhookUrl = config.getString("webhook-url", "");
        getLogger().info("XBound enabled. Instance set. Global border operating on stored XP.");
    }

    private void setupLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            useLuckPerms = true;
            luckPermsApi = LuckPermsProvider.get();
            getLogger().info("LuckPerms detected! Prefixes/Suffixes will come from LuckPerms.");
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayerXp(p.getUniqueId()));
        saveBorder();
        saveDataFile();
        if (!useLuckPerms) savePrefixSuffix();

        if (config.getBoolean("events.server-stop", true)) {
            sendToDiscord(config.getString("messages.server-stop", "Server stopping"));
        }

        getLogger().info("XBound disabled.");
    }

    public void setStoredXp(UUID uuid, double amount) {
        storedXp.put(uuid, Math.max(0.0, amount));
        updateBorder();
    }

    public static XBound getInstance() {
        return instance;
    }

    public YamlConfiguration getDataConfig() {
        return dataConfig;
    }

    public MessageManager getMessages() {
        return messageManager;
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();
        baseBorderSize = cfg.getInt("base-border-size", 50);
        xpPerBlock = cfg.getInt("xp-per-block", 10);
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

    public synchronized void updateBorder() {
        double totalXP = storedXp.values().stream().mapToDouble(Double::doubleValue).sum();
        double extra = totalXP / Math.max(1, xpPerBlock);
        double newSize = Math.max(minBorderSize, baseBorderSize + Math.floor(extra));
        world.getWorldBorder().setSize(newSize);
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
                    int bx = (int) Math.floor(centerX);
                    int bz = (int) Math.floor(centerZ);
                    CompletableFuture.runAsync(() -> {
                        try {
                            world.getChunkAtAsync(bx, bz).join();
                        } catch (Throwable ignored) {
                            world.getChunkAt(bx, bz);
                        }
                    }).thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                        Location safe = findSafeLocationNear(world, bx, bz);
                        if (safe == null) safe = world.getHighestBlockAt(bx, bz).getLocation().add(0.5, 1, 0.5);
                        player.teleport(safe);
                        player.sendMessage(Component.text("§cYou went out of the border! Teleporting you back to the center."));
                    }));
                }
            }
        }, 20L, 20L);
    }

    private Location findSafeLocationNear(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z);
        int minY = world.getMinHeight();
        for (int y = highestY; y >= minY; y--) {
            var floor = world.getBlockAt(x, y, z);
            var above = world.getBlockAt(x, y + 1, z);
            var above2 = world.getBlockAt(x, y + 2, z);

            if (floor.getType().isSolid() && !above.getType().isSolid() && !above.isLiquid()
                    && !above2.getType().isSolid() && !above2.isLiquid()
                    && !floor.getType().name().contains("CACTUS")
                    && !floor.getType().name().contains("MAGMA")
                    && !floor.getType().name().contains("LAVA")) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    public void updatePlayerName(Player player) {
        if (useLuckPerms) {
            User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
            String prefix = "";
            String suffix = "";
            if (user != null) {
                prefix = Optional.ofNullable(user.getCachedData().getMetaData().getPrefix()).orElse("");
                suffix = Optional.ofNullable(user.getCachedData().getMetaData().getSuffix()).orElse("");
            }
            player.playerListName(Component.text(prefix + player.getName() + suffix));
        } else {
            Component name = Component.empty();
            if (prefixes.containsKey(player.getUniqueId())) name = name.append(prefixes.get(player.getUniqueId())).append(Component.text(" "));
            name = name.append(Component.text(player.getName()));
            if (suffixes.containsKey(player.getUniqueId())) name = name.append(Component.text(" ")).append(suffixes.get(player.getUniqueId()));
            player.playerListName(name);
        }
    }

    private void updateTablist() {
        List<String> headerLines = getConfig().getStringList("tablist.header");
        List<String> footerLines = getConfig().getStringList("tablist.footer");
        String header = String.join("\n", headerLines);
        String footer = String.join("\n", footerLines);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeader(Component.text(header));
            player.sendPlayerListFooter(Component.text(footer));
            updatePlayerName(player);
        }
    }

    public synchronized void modifyStoredXp(UUID uuid, double delta) {
        storedXp.put(uuid, Math.max(0, storedXp.getOrDefault(uuid, 0.0) + delta));
        updateBorder();
    }

    public Map<UUID, Double> getStoredXpMap() {
        return Collections.unmodifiableMap(storedXp);
    }

    public void sendToDiscord(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty() || message == null || message.isEmpty()) return;

        String jsonPayload = "{\"content\":\"" + message.replace("\"", "\\\"") + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> { getLogger().warning("Failed to send message to Discord: " + e.getMessage()); return null; });
    }

    public Map<UUID, Component> getPrefixes() {
        return Collections.unmodifiableMap(prefixes);
    }

    public Map<UUID, Component> getSuffixes() {
        return Collections.unmodifiableMap(suffixes);
    }

    public Map<UUID, Double> getStoredXp() {
        return storedXp;
    }

    public void savePlayerXp(UUID uuid) {
        dataConfig.set("players." + uuid, storedXp.getOrDefault(uuid, 0.0));
    }

    public void loadPlayerXp(UUID uuid) {
        double xp = dataConfig.getDouble("players." + uuid, 0.0);
        storedXp.put(uuid, xp);
    }

    public void saveBorder() {
        dataConfig.set("border.size", world.getWorldBorder().getSize());
        dataConfig.set("border.center-x", world.getWorldBorder().getCenter().getX());
        dataConfig.set("border.center-z", world.getWorldBorder().getCenter().getZ());
    }

    public void savePrefixSuffix() {
        if (useLuckPerms || dataConfig == null) return;
        for (UUID uuid : prefixes.keySet()) dataConfig.set("prefixes." + uuid, GSON.serialize(prefixes.get(uuid)));
        for (UUID uuid : suffixes.keySet()) dataConfig.set("suffixes." + uuid, GSON.serialize(suffixes.get(uuid)));
        saveDataFile();
    }

    private void loadPrefixSuffix() {
        if (useLuckPerms || dataConfig == null) return;
        if (dataConfig.isConfigurationSection("prefixes")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("prefixes")).getKeys(false)) {
                String json = dataConfig.getString("prefixes." + key);
                if (json != null) {
                    try { prefixes.put(UUID.fromString(key), GSON.deserialize(json)); }
                    catch (Exception e) { getLogger().warning("Failed to deserialize prefix for " + key + ": " + e.getMessage()); }
                }
            }
        }
        if (dataConfig.isConfigurationSection("suffixes")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("suffixes")).getKeys(false)) {
                String json = dataConfig.getString("suffixes." + key);
                if (json != null) {
                    try { suffixes.put(UUID.fromString(key), GSON.deserialize(json)); }
                    catch (Exception e) { getLogger().warning("Failed to deserialize suffix for " + key + ": " + e.getMessage()); }
                }
            }
        }
    }

    private void loadDataFile() {
        if (dataFile == null) dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) saveResource("data.yml", false);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
}
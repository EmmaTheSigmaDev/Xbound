/*
 * MIT License
 *
 * Copyright (c) 2025 EMMA_THE_SIGMA
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.XBound.xBound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public final class XBound extends JavaPlugin implements Listener {

    private int baseBorderSize;
    private int xpPerBlock;
    private double xpStealPercent;
    private int minBorderSize;
    private World world;

    private final Map<UUID, Integer> storedXp = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    private String webhookUrl;
    private FileConfiguration config;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<UUID, Component> prefixes = new HashMap<>();
    private final Map<UUID, Component> suffixes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        setupDataFile();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Load stored XP and border info
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadPlayerXp(p.getUniqueId());
        }
        restoreBorder();
        startBorderCheckTask();
        updateTablist();

        getLogger().info("XP Border plugin enabled!");

        loadDataFile();
        loadPrefixSuffix();  // loads stored prefixes/suffixes

        double size = dataConfig.getDouble("border.size", 100); // default size if not saved
        double centerX = dataConfig.getDouble("border.center-x", 0);
        double centerZ = dataConfig.getDouble("border.center-z", 0);

        WorldBorder border = Bukkit.getWorlds().getFirst().getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);

        config = getConfig();
        webhookUrl = config.getString("webhook-url");

        // Server start event
        if (config.getBoolean("events.server-start", true)) {
            sendToDiscord(config.getString("messages.server-start"));
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(p -> savePlayerXp(p.getUniqueId()));
        saveBorder();
        saveDataFile();
        saveDataFile();
        savePrefixSuffix();
        if (config.getBoolean("events.server-stop", true)) {
            sendToDiscord(config.getString("messages.server-stop"));
        }
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();
        baseBorderSize = cfg.getInt("base-border-size", 50);
        xpPerBlock = cfg.getInt("xp-per-block", 10);
        xpStealPercent = cfg.getDouble("xp-steal-percent", 50) / 100.0;
        minBorderSize = cfg.getInt("min-border-size", 20);
        world = Bukkit.getWorld(cfg.getString("world", "world"));
        if (world == null) {
            getLogger().severe("Configured world not found! Defaulting to first world.");
            world = Bukkit.getWorlds().getFirst();
        }
    }

    private void setupDataFile() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().severe("Failed to create plugin data folder!");
        }

        dataFile = new File(dataFolder, "data.yml");
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    getLogger().severe("Failed to create data.yml!");
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Error while creating data.yml", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile); // <— actually load it
    }

    private void saveDataFile() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    private void loadPlayerXp(UUID uuid) {
        int xp = dataConfig.getInt("players." + uuid, 0);
        storedXp.put(uuid, xp);
    }

    private void savePlayerXp(UUID uuid) {
        dataConfig.set("players." + uuid, storedXp.getOrDefault(uuid, 0));
    }

    private void updateBorder() {
        int totalXP = storedXp.values().stream().mapToInt(Integer::intValue).sum();
        int newSize = Math.max(minBorderSize, baseBorderSize + (totalXP / xpPerBlock));
        world.getWorldBorder().setSize(newSize);
        saveBorder();
    }

    private void saveBorder() {
        dataConfig.set("border.size", world.getWorldBorder().getSize());
        dataConfig.set("border.center-x", world.getWorldBorder().getCenter().getX());
        dataConfig.set("border.center-z", world.getWorldBorder().getCenter().getZ());
    }

    private void restoreBorder() {
        double size = dataConfig.getDouble("border.size", baseBorderSize);
        double centerX = dataConfig.getDouble("border.center-x", world.getSpawnLocation().getX());
        double centerZ = dataConfig.getDouble("border.center-z", world.getSpawnLocation().getZ());
        world.getWorldBorder().setSize(size);
        world.getWorldBorder().setCenter(centerX, centerZ);
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
                    player.teleport(world.getHighestBlockAt((int) centerX, (int) centerZ).getLocation().add(0.5, 1, 0.5));
                    player.sendMessage("§cYou went out of the border! Teleporting you back to the center.");
                }
            }
        }, 20L, 20L);
    }

    private void updateTablist() {
        String header = String.join("\n", getConfig().getStringList("tablist.header"));
        String footer = String.join("\n", getConfig().getStringList("tablist.footer"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeader(net.kyori.adventure.text.Component.text(header));
            player.sendPlayerListFooter(net.kyori.adventure.text.Component.text(footer));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayerXp(event.getPlayer().getUniqueId());
        saveBorder();
        saveDataFile();
    }

    @EventHandler
    public void onXpGain(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0) {
            UUID id = event.getPlayer().getUniqueId();
            storedXp.put(id, storedXp.getOrDefault(id, 0) + event.getAmount());
            updateBorder();
        }
    }

    @EventHandler
    public void onPlayerKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player victim && victim.getKiller() != null) {
            Player killer = victim.getKiller();
            UUID vId = victim.getUniqueId();
            UUID kId = killer.getUniqueId();

            int victimXP = storedXp.getOrDefault(vId, 0);
            int stolenXP = (int) (victimXP * xpStealPercent);

            storedXp.put(vId, victimXP - stolenXP);
            storedXp.put(kId, storedXp.getOrDefault(kId, 0) + stolenXP);

            updateBorder();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/" + label + " prefix <player> <text>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " suffix <player> <text>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " clearprefix <player>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " clearsuffix <player>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " setbordermiddle", NamedTextColor.GRAY));
            return true;
        }

        String sub = args[0].toLowerCase();

        // Handle prefix/suffix commands
        if (sub.equals("prefix") || sub.equals("suffix") || sub.equals("clearprefix") || sub.equals("clearsuffix")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("You must specify a player!", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                return true;
            }

            switch (sub) {
                case "prefix" -> {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /" + label + " prefix <player> <text>", NamedTextColor.RED));
                        return true;
                    }
                    String prefix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    prefixes.put(target.getUniqueId(), Component.text(prefix, NamedTextColor.GOLD));
                    savePrefixSuffix();
                    updatePlayerName(target);
                    sender.sendMessage(Component.text("Prefix set!", NamedTextColor.GREEN));
                }
                case "suffix" -> {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /" + label + " suffix <player> <text>", NamedTextColor.RED));
                        return true;
                    }
                    String suffix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    suffixes.put(target.getUniqueId(), Component.text(suffix, NamedTextColor.AQUA));
                    savePrefixSuffix();
                    updatePlayerName(target);
                    sender.sendMessage(Component.text("Suffix set!", NamedTextColor.GREEN));
                }
                case "clearprefix" -> {
                    prefixes.remove(target.getUniqueId());
                    savePrefixSuffix();
                    updatePlayerName(target);
                    sender.sendMessage(Component.text("Prefix cleared!", NamedTextColor.GREEN));
                }
                case "clearsuffix" -> {
                    suffixes.remove(target.getUniqueId());
                    savePrefixSuffix();
                    updatePlayerName(target);
                    sender.sendMessage(Component.text("Suffix cleared!", NamedTextColor.GREEN));
                }
            }
            return true;
        }

        // Handle setbordermiddle
        if (sub.equals("setbordermiddle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can run this command!", NamedTextColor.RED));
                return true;
            }
            if (!player.hasPermission("xbound.setbordermiddle")) {
                player.sendMessage(Component.text("You do not have permission to run this command.", NamedTextColor.RED));
                return true;
            }

            double x = player.getLocation().getX();
            double z = player.getLocation().getZ();
            player.getWorld().getWorldBorder().setCenter(x, z);

            dataConfig.set("border.center-x", x);
            dataConfig.set("border.center-z", z);
            saveDataFile();

            player.sendMessage(Component.text("World border center set to your location!", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Unknown subcommand!", NamedTextColor.RED));
        return true;
    }

    private void updatePlayerName(Player player) {
        Component name = Component.empty();
        if (prefixes.containsKey(player.getUniqueId())) {
            name = name.append(prefixes.get(player.getUniqueId())).append(Component.text(" "));
        }
        name = name.append(Component.text(player.getName(), NamedTextColor.WHITE));
        if (suffixes.containsKey(player.getUniqueId())) {
            name = name.append(Component.text(" ")).append(suffixes.get(player.getUniqueId()));
        }
        player.playerListName(name);
    }

    private void savePrefixSuffix() {
        for (UUID uuid : prefixes.keySet()) {
            dataConfig.set("prefixes." + uuid, prefixes.get(uuid).toString());
        }
        for (UUID uuid : suffixes.keySet()) {
            dataConfig.set("suffixes." + uuid, suffixes.get(uuid).toString());
        }
        saveDataFile();
    }

    private void loadPrefixSuffix() {
        if (dataConfig.isConfigurationSection("prefixes")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("prefixes")).getKeys(false)) {
                String prefixText = dataConfig.getString("prefixes." + key);
                if (prefixText != null) {
                    prefixes.put(UUID.fromString(key), Component.text(prefixText, NamedTextColor.GOLD));
                }
            }
        }
        if (dataConfig.isConfigurationSection("suffixes")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("suffixes")).getKeys(false)) {
                String suffixText = dataConfig.getString("suffixes." + key);
                if (suffixText != null) {
                    suffixes.put(UUID.fromString(key), Component.text(suffixText, NamedTextColor.AQUA));
                }
            }
        }
    }

    private void loadDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            File parentFolder = dataFile.getParentFile();
            if (!parentFolder.exists() && !parentFolder.mkdirs()) {
                getLogger().severe("Failed to create plugin data folder: " + parentFolder.getAbsolutePath());
                return;
            }
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private double calculateBorderSizeFromXP(double xp) {
        double baseSize = 50;
        double scale = 0.5;
        return baseSize + (xp * scale);
    }

    private void updateBorderForXP(Player player, double xp) {
        double newSize = calculateBorderSizeFromXP(xp); // however you calculate it
        WorldBorder border = player.getWorld().getWorldBorder();
        border.setSize(newSize);

        dataConfig.set("border.size", newSize);
        saveDataFile();
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            double xp = player.getTotalExperience();
            updateBorderForXP(player, xp);
        }, 1L); // delay 1 tick so XP is updated before checking
    }

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            double xp = player.getTotalExperience();
            updateBorderForXP(player, xp);
        }, 1L);
    }

    private void sendToDiscord(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        if (message == null || message.isEmpty()) return;

        String jsonPayload = "{\"content\":\"" + message.replace("\"", "\\\"") + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        // Run async to avoid blocking the server thread
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    getLogger().warning("Failed to send message to Discord: " + e.getMessage());
                    return null;
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayerXp(event.getPlayer().getUniqueId());
        updateBorder();

        if (config.getBoolean("events.join", true)) {
            sendToDiscord(Objects.requireNonNull(config.getString("messages.join")).replace("{player}", event.getPlayer().getName()));
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        if (config.getBoolean("events.leave", true)) {
            sendToDiscord(Objects.requireNonNull(config.getString("messages.leave")).replace("{player}", event.getPlayer().getName()));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (config.getBoolean("events.chat", true)) {
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            sendToDiscord(Objects.requireNonNull(config.getString("messages.chat"))
                    .replace("{player}", event.getPlayer().getName())
                    .replace("{message}", message));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!config.getBoolean("events.death", true)) return;

        Component deathComponent = event.deathMessage();
        String deathMsg = (deathComponent != null)
                ? PlainTextComponentSerializer.plainText().serialize(deathComponent)
                : "died";

        sendToDiscord(Objects.requireNonNull(config.getString("messages.death"))
                .replace("{player}", event.getEntity().getName())
                .replace("{message}", deathMsg));
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (config.getBoolean("events.advancement", true)) {
            String advName = event.getAdvancement().getKey().getKey();
            if (!advName.contains("recipes/")) { // avoid recipe spam
                sendToDiscord(Objects.requireNonNull(config.getString("messages.advancement"))
                        .replace("{player}", event.getPlayer().getName())
                        .replace("{advancement}", advName.replace("_", " ")));
            }
        }
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        if (!config.getBoolean("events.kick", true)) return;

        String reason = PlainTextComponentSerializer.plainText().serialize(event.reason());

        sendToDiscord(Objects.requireNonNull(config.getString("messages.kick"))
                .replace("{player}", event.getPlayer().getName())
                .replace("{reason}", reason));
    }
}
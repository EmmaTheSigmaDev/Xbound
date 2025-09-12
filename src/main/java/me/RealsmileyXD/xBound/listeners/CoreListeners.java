package me.RealsmileyXD.xBound.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.RealsmileyXD.xBound.XBound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public class CoreListeners implements Listener {

    private final XBound plugin;

    public CoreListeners(XBound plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.savePlayerXp(event.getPlayer().getUniqueId());
        plugin.saveBorder();
        plugin.saveDataFile();
    }

    @EventHandler
    public void onXpGain(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0) {
            UUID id = event.getPlayer().getUniqueId();
            // store xp in bank
            plugin.modifyStoredXp(id, event.getAmount());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID vId = victim.getUniqueId();

        double victimXP = plugin.getStoredXp().getOrDefault(vId, 0.0);

        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            // PvP kill → steal a portion
            UUID kId = killer.getUniqueId();
            double stealPercent = plugin.getConfig().getDouble("xp-steal-percent", 50);
            double stolenXP = victimXP * stealPercent / 100.0;

            plugin.modifyStoredXp(vId, -stolenXP);
            plugin.modifyStoredXp(kId, stolenXP);

            victim.sendMessage(Component.text(
                    "You lost " + String.format("%.1f", stolenXP) + " XP to " + killer.getName(),
                    NamedTextColor.RED
            ));
            killer.sendMessage(Component.text(
                    "You gained " + String.format("%.1f", stolenXP) + " XP from " + victim.getName(),
                    NamedTextColor.GREEN
            ));
        } else {
            // Suicide or non-PvP death → lose a fixed percent
            double deathLossPercent = plugin.getConfig().getDouble("xp-death-percent", 25);
            double lostXP = victimXP * deathLossPercent / 100.0;
            plugin.modifyStoredXp(vId, -lostXP);

            victim.sendMessage(Component.text(
                    "You lost " + String.format("%.1f", lostXP) + " XP due to death.",
                    NamedTextColor.RED
            ));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.loadPlayerXp(event.getPlayer().getUniqueId());
        plugin.updateBorder();
        plugin.updatePlayerName(event.getPlayer());

        if (plugin.getConfig().getBoolean("events.join", true)) {
            plugin.sendToDiscord(Objects.requireNonNull(plugin.getConfig().getString("messages.join", "player joined"))
                    .replace("{player}", event.getPlayer().getName()));
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("events.leave", true)) {
            plugin.sendToDiscord(Objects.requireNonNull(plugin.getConfig().getString("messages.leave", "player left"))
                    .replace("{player}", event.getPlayer().getName()));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (plugin.getConfig().getBoolean("events.chat", true)) {
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.sendToDiscord(Objects.requireNonNull(plugin.getConfig().getString("messages.chat", "{player}: {message}"))
                    .replace("{player}", event.getPlayer().getName())
                    .replace("{message}", message));
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (plugin.getConfig().getBoolean("events.advancement", true)) {
            String advName = event.getAdvancement().getKey().getKey();
            if (!advName.contains("recipes/")) {
                plugin.sendToDiscord(Objects.requireNonNull(plugin.getConfig().getString("messages.advancement", "{player} advanced"))
                        .replace("{player}", event.getPlayer().getName())
                        .replace("{advancement}", advName.replace("_", " ")));
            }
        }
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        if (!plugin.getConfig().getBoolean("events.kick", true)) return;

        String reason = PlainTextComponentSerializer.plainText().serialize(event.reason());

        plugin.sendToDiscord(Objects.requireNonNull(plugin.getConfig().getString("messages.kick", "{player} kicked"))
                .replace("{player}", event.getPlayer().getName())
                .replace("{reason}", reason));
    }
}

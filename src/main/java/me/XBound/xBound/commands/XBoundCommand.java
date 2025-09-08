package me.XBound.xBound.commands;

import me.XBound.xBound.XBound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class XBoundCommand implements CommandExecutor {

    private final XBound plugin;

    public XBoundCommand(XBound plugin, Map<UUID, Component> prefixes, Map<UUID, Component> suffixes) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {

        // ✅ Alias: /xpbalance acts like /xbound balance
        if (label.equalsIgnoreCase("xpbalance")) {
            if (sender instanceof Player player) {
                sendBalance(player, player);
            } else {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            // ================= Stats Commands =================
            case "balance", "stats" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
                    return true;
                }

                if (args.length == 1) {
                    sendBalance(sender, player);
                } else {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                        return true;
                    }
                    sendBalance(sender, target);
                }
            }

            case "leaderboard" -> {
                int topN = 10;
                if (args.length >= 2) {
                    try {
                        topN = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {}
                }
                sendLeaderboard(sender, topN);
            }

            // keep all your old subcommands (reload, discord, prefix, suffix, bordercenter etc.)
            default -> sender.sendMessage(Component.text("Unknown subcommand! Use /" + label + " for help.", NamedTextColor.RED));
        }
        return true;
    }

    // ================= Helpers =================

    private void sendBalance(CommandSender viewer, Player target) {
        int xp = plugin.getStoredXp().getOrDefault(target.getUniqueId(), 0);
        viewer.sendMessage(Component.text(
                target.getName() + " has contributed " + xp + " XP.",
                NamedTextColor.GOLD
        ));
    }

    private void sendLeaderboard(CommandSender sender, int topN) {
        Map<UUID, Integer> xpMap = plugin.getStoredXp();

        if (xpMap.isEmpty()) {
            sender.sendMessage(Component.text("No XP contributions yet!", NamedTextColor.RED));
            return;
        }

        List<Map.Entry<UUID, Integer>> sorted = xpMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .toList();

        sender.sendMessage(Component.text("=== XP Leaderboard (Top " + topN + ") ===", NamedTextColor.YELLOW));
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName())
                    .orElse("Unknown");
            sender.sendMessage(Component.text(rank + ". " + name + " — " + entry.getValue() + " XP", NamedTextColor.AQUA));
            rank++;
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== XBound Commands ====", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " balance [player]", NamedTextColor.GRAY)
                .append(Component.text(" - Show XP contribution", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " stats [player]", NamedTextColor.GRAY)
                .append(Component.text(" - Alias of balance", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " leaderboard [N]", NamedTextColor.GRAY)
                .append(Component.text(" - Show top XP contributors", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/xpbalance", NamedTextColor.GRAY)
                .append(Component.text(" - Quick balance shortcut", NamedTextColor.DARK_GRAY)));
        // … keep your existing help lines here
    }
}
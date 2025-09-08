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

            // ================= Leaderboard =================
            case "leaderboard" -> {
                int topN = 10;
                if (args.length >= 2) {
                    try {
                        topN = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {}
                }
                sendLeaderboard(sender, topN);
            }

            // ================= XP Management =================
            case "xp" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /xbound xp <check|give|set> [player] [amount]", NamedTextColor.RED));
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);

                switch (action) {
                    case "check" -> {
                        Player target = (args.length >= 3) ? Bukkit.getPlayer(args[2]) : (Player) sender;
                        if (target == null) {
                            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                            return true;
                        }
                        double xp = plugin.getStoredXp().getOrDefault(target.getUniqueId(), 0.0);
                        sender.sendMessage(Component.text(target.getName() + " has " + String.format("%.1f", xp) + " XP.", NamedTextColor.GOLD));
                    }

                    case "give", "add" -> {
                        if (args.length < 4) {
                            sender.sendMessage(Component.text("Usage: /xbound xp give <player> <amount>", NamedTextColor.RED));
                            return true;
                        }
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target == null) {
                            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                            return true;
                        }

                        double amount;
                        try {
                            amount = Double.parseDouble(args[3]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("Amount must be a number!", NamedTextColor.RED));
                            return true;
                        }

                        plugin.modifyStoredXp(target.getUniqueId(), amount); // ✅ use helper
                        sender.sendMessage(Component.text("Added " + String.format("%.1f", amount) + " XP to " + target.getName(), NamedTextColor.GREEN));
                        target.sendMessage(Component.text("You received " + String.format("%.1f", amount) + " XP!", NamedTextColor.GOLD));
                    }

                    case "set" -> {
                        if (args.length < 4) {
                            sender.sendMessage(Component.text("Usage: /xbound xp set <player> <amount>", NamedTextColor.RED));
                            return true;
                        }
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target == null) {
                            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                            return true;
                        }

                        double amount;
                        try {
                            amount = Double.parseDouble(args[3]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("Amount must be a number!", NamedTextColor.RED));
                            return true;
                        }

                        plugin.setStoredXp(target.getUniqueId(), amount); // ✅ use helper
                        sender.sendMessage(Component.text("Set " + target.getName() + "'s XP to " + String.format("%.1f", amount), NamedTextColor.GREEN));
                        target.sendMessage(Component.text("Your XP was set to " + String.format("%.1f", amount), NamedTextColor.GOLD));
                    }

                    default -> sender.sendMessage(Component.text("Unknown action! Use check, give, or set.", NamedTextColor.RED));
                }
            }

            default -> sender.sendMessage(Component.text("Unknown subcommand! Use /" + label + " for help.", NamedTextColor.RED));
        }

        return true;
    }

    // ================= Helpers =================

    private void sendBalance(CommandSender viewer, Player target) {
        double xp = plugin.getStoredXp().getOrDefault(target.getUniqueId(), 0.0);
        viewer.sendMessage(Component.text(
                target.getName() + " has contributed " + String.format("%.1f", xp) + " XP.",
                NamedTextColor.GOLD
        ));
    }

    private void sendLeaderboard(CommandSender sender, int topN) {
        Map<UUID, Double> xpMap = plugin.getStoredXp();

        if (xpMap.isEmpty()) {
            sender.sendMessage(Component.text("No XP contributions yet!", NamedTextColor.RED));
            return;
        }

        List<Map.Entry<UUID, Double>> sorted = xpMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .toList();

        sender.sendMessage(Component.text("=== XP Leaderboard (Top " + topN + ") ===", NamedTextColor.YELLOW));
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : sorted) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName())
                    .orElse("Unknown");
            sender.sendMessage(Component.text(rank + ". " + name + " — " + String.format("%.1f", entry.getValue()) + " XP", NamedTextColor.AQUA));
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
    }
}
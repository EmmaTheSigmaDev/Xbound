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

package me.XBound.xBound.commands;

import me.XBound.xBound.XBound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class XBoundCommand implements CommandExecutor {

    private final XBound plugin;
    private final Map<UUID, Component> prefixes;
    private final Map<UUID, Component> suffixes;

    public XBoundCommand(XBound plugin, Map<UUID, Component> prefixes, Map<UUID, Component> suffixes) {
        this.plugin = plugin;
        this.prefixes = prefixes;
        this.suffixes = suffixes;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // ================= Reload Commands =================
            case "reloadrules" -> {
                if (checkPerm(sender)) return true;
                plugin.reloadRulesFile();
                sender.sendMessage(Component.text("Rules file reloaded!", NamedTextColor.GREEN));
            }
            case "reloadconfig" -> {
                if (checkPerm(sender)) return true;
                plugin.reloadConfig();
                sender.sendMessage(Component.text("Config file reloaded!", NamedTextColor.GREEN));
            }
            case "reloaddata" -> {
                if (checkPerm(sender)) return true;
                plugin.reloadDataFile();
                sender.sendMessage(Component.text("Data file reloaded!", NamedTextColor.GREEN));
            }
            case "reload" -> {
                if (checkPerm(sender)) return true;
                plugin.reloadConfig();
                plugin.reloadRulesFile();
                plugin.reloadDataFile();
                sender.sendMessage(Component.text("All plugin files reloaded!", NamedTextColor.GREEN));
            }

            // ================= Discord Command =================
            case "discord" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
                    return true;
                }
                sendDiscordLink(player);
            }

            // ================= Prefix / Suffix Commands =================
            case "prefix", "suffix", "clearprefix", "clearsuffix" -> {
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
                        plugin.savePrefixSuffix();
                        plugin.updatePlayerName(target);
                        sender.sendMessage(Component.text("Prefix set!", NamedTextColor.GREEN));
                    }
                    case "suffix" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Component.text("Usage: /" + label + " suffix <player> <text>", NamedTextColor.RED));
                            return true;
                        }
                        String rawSuffix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                        Component suffixComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawSuffix);
                        suffixes.put(target.getUniqueId(), suffixComp);
                        plugin.savePrefixSuffix();
                        plugin.updatePlayerName(target);
                        sender.sendMessage(Component.text("Suffix set!", NamedTextColor.GREEN));
                    }
                    case "clearprefix" -> {
                        prefixes.remove(target.getUniqueId());
                        plugin.savePrefixSuffix();
                        plugin.updatePlayerName(target);
                        sender.sendMessage(Component.text("Prefix cleared!", NamedTextColor.GREEN));
                    }
                    case "clearsuffix" -> {
                        suffixes.remove(target.getUniqueId());
                        plugin.savePrefixSuffix();
                        plugin.updatePlayerName(target);
                        sender.sendMessage(Component.text("Suffix cleared!", NamedTextColor.GREEN));
                    }
                }
            }

            // ================= Border Command =================
            case "setbordermiddle" -> {
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

                plugin.getDataConfig().set("border.center-x", x);
                plugin.getDataConfig().set("border.center-z", z);
                plugin.saveDataFile();

                player.sendMessage(Component.text("World border center set to your location!", NamedTextColor.GREEN));
            }
            case "bordercenter" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can run this command!", NamedTextColor.RED));
                    return true;
                }
                WorldBorder border = player.getWorld().getWorldBorder();
                Location center = new Location(
                        player.getWorld(),
                        border.getCenter().getX(),
                        player.getWorld().getHighestBlockYAt(
                                (int) border.getCenter().getX(),
                                (int) border.getCenter().getZ()
                        ) + 1,
                        border.getCenter().getZ()
                );

                player.teleport(center);
                player.sendMessage(Component.text("Teleported to the center of the border!", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand! Use /" + label + " for help.", NamedTextColor.RED));
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== XBound Commands ====", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " reloadrules", NamedTextColor.GRAY).append(Component.text(" - Reloads rules.yml", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " reloadconfig", NamedTextColor.GRAY).append(Component.text(" - Reloads config.yml", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " reloaddata", NamedTextColor.GRAY).append(Component.text(" - Reloads data.yml", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY).append(Component.text(" - Reloads all plugin files", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " discord", NamedTextColor.GRAY).append(Component.text(" - Shows the Discord invite link", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("/" + label + " prefix <player> <text>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " suffix <player> <text>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " clearprefix <player>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " clearsuffix <player>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " setbordermiddle", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " bordercenter", NamedTextColor.GRAY));
    }

    private boolean checkPerm(CommandSender sender) {
        if (!sender.hasPermission("xbound.reload")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    private void sendDiscordLink(Player player) {
        String link = plugin.getConfig().getString("discord.link", "https://discord.gg/example");
        String message = plugin.getConfig().getString("discord.message", "Join our Discord here!");
        String hover = plugin.getConfig().getString("discord.hover", "Click to open the Discord");

        Component clickableMessage = Component.text(message, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));

        player.sendMessage(clickableMessage);
    }
}
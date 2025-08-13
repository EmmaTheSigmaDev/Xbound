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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class XBoundCommand implements CommandExecutor {

    private final XBound plugin;

    public XBoundCommand(XBound plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {

        if (!sender.hasPermission("xbound.reload")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage:").color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/" + label + " reloadrules").color(NamedTextColor.GRAY)
                    .append(Component.text(" - Reloads rules.yml").color(NamedTextColor.DARK_GRAY)));
            sender.sendMessage(Component.text("/" + label + " reloadconfig").color(NamedTextColor.GRAY)
                    .append(Component.text(" - Reloads config.yml").color(NamedTextColor.DARK_GRAY)));
            sender.sendMessage(Component.text("/" + label + " reload").color(NamedTextColor.GRAY)
                    .append(Component.text(" - Reloads all plugin files").color(NamedTextColor.DARK_GRAY)));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reloadrules":
                plugin.reloadRulesFile();
                sender.sendMessage(Component.text("Rules file reloaded!").color(NamedTextColor.GREEN));
                break;

            case "reloadconfig":
                plugin.reloadConfig();
                sender.sendMessage(Component.text("Config file reloaded!").color(NamedTextColor.GREEN));
                break;

            case "reload":
                plugin.reloadConfig();
                plugin.reloadRulesFile();
                plugin.reloadDataFile(); // Reloads your saved player/data file
                sender.sendMessage(Component.text("All plugin files reloaded!").color(NamedTextColor.GREEN));
                break;

            default:
                sender.sendMessage(Component.text("Unknown subcommand. Use /" + label + " for help.").color(NamedTextColor.RED));
                break;
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
                        String suffix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                        suffixes.put(target.getUniqueId(), Component.text(suffix, NamedTextColor.AQUA));
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

            default -> sender.sendMessage(Component.text("Unknown subcommand! Use /" + label + " for help.", NamedTextColor.RED));
        }

        return true;
    }
}
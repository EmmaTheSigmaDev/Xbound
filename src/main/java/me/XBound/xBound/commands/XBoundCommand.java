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

        return true;
    }
}
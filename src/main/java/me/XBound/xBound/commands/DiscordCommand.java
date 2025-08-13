package me.XBound.xBound.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.XBound.xBound.XBound;
import org.jetbrains.annotations.NotNull;

public class DiscordCommand implements CommandExecutor {

    private final XBound plugin;

    public DiscordCommand(XBound plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String link = plugin.getConfig().getString("discord.link", "https://discord.gg/example");
        String message = plugin.getConfig().getString("discord.message", "Join our Discord here!");
        String hover = plugin.getConfig().getString("discord.hover", "Click to open the Discord");

        Component clickableMessage = Component.text(message)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));

        player.sendMessage(clickableMessage);
        return true;
    }
}
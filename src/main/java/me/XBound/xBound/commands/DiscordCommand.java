package me.XBound.xBound.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public DiscordCommand(XBound plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String link = plugin.getConfig().getString("discord.link", "https://discord.gg/example");
        String message = plugin.getConfig().getString("discord.message", "<aqua>Join our Discord!");
        String hover = plugin.getConfig().getString("discord.hover", "<yellow>Click to open the Discord");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Discord link: " + link);
            return true;
        }

        Component clickableMessage = MM.deserialize(message)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(MM.deserialize(hover)));

        player.sendMessage(clickableMessage);
        return true;
    }
}
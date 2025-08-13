package me.XBound.xBound.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class BorderCenterCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
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
        return true;
    }
}
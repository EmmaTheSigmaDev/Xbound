package me.XBound.xBound.commands;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class BorderCenterCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        World world = player.getWorld();
        WorldBorder border = world.getWorldBorder();

        // Center coordinates
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        // Start at highest block
        int highestY = world.getHighestBlockYAt((int) centerX, (int) centerZ);

        Location safeLocation = null;
        for (int y = highestY; y > world.getMinHeight(); y--) {
            Material blockType = world.getBlockAt((int) centerX, y, (int) centerZ).getType();
            Material aboveType = world.getBlockAt((int) centerX, y + 1, (int) centerZ).getType();

            // Make sure block is solid & above is safe to stand in
            if (blockType.isSolid() &&
                    aboveType == Material.AIR &&
                    blockType != Material.CACTUS &&
                    blockType != Material.MAGMA_BLOCK &&
                    blockType != Material.LAVA) {

                safeLocation = new Location(world, centerX + 0.5, y + 1, centerZ + 0.5);
                break;
            }
        }

        if (safeLocation != null) {
            player.teleport(safeLocation);
            player.sendMessage("§aTeleported to the safe center of the world border.");
        } else {
            player.sendMessage("§cNo safe location found at the border center!");
        }

        return true;
    }
}
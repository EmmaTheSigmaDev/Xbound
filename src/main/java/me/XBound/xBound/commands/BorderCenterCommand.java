package me.XBound.xBound.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BorderCenterCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        World world = player.getWorld();
        WorldBorder border = world.getWorldBorder();

        // Center coordinates (use block-aligned ints)
        int centerX = border.getCenter().getBlockX();
        int centerZ = border.getCenter().getBlockZ();

        // Start at highest block at that column
        int highestY = world.getHighestBlockYAt(centerX, centerZ);

        Location safeLocation = null;
        for (int y = highestY; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(centerX, y, centerZ);
            Block above = block.getRelative(0, 1, 0);
            Block above2 = block.getRelative(0, 2, 0);

            Material type = block.getType();
            Material aboveType = above.getType();
            Material above2Type = above2.getType();

            // Ensure standing block is safe and at least 2 blocks of air above
            if (type.isSolid()
                    && !isDangerous(type)
                    && aboveType == Material.AIR
                    && above2Type == Material.AIR) {

                safeLocation = new Location(world, centerX + 0.5, y + 1, centerZ + 0.5);
                break;
            }
        }

        if (safeLocation != null) {
            player.teleport(safeLocation);
            player.sendMessage(MM.deserialize("<green>Teleported to the safe center of the world border."));
        } else {
            player.sendMessage(MM.deserialize("<red>No safe location found at the border center!"));
        }

        return true;
    }

    private boolean isDangerous(Material material) {
        return material == Material.CACTUS
                || material == Material.MAGMA_BLOCK
                || material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE;
    }
}
package me.XBound.xBound.listeners;

import me.XBound.xBound.XBound;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;

public class MiningXpListener implements Listener {

    private final XBound plugin;

    public MiningXpListener(XBound plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();

        // Base XP for every block
        double xp = 0.1;

        // Extra XP for ores
        xp += switch (type) {
            case COAL_ORE,DEEPSLATE_COAL_ORE -> 0.5;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 1;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> 3;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 10;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 15;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 0.8;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 0.25;
            default -> 0.0;
        };

        plugin.modifyStoredXp(player.getUniqueId(), xp);
    }
}
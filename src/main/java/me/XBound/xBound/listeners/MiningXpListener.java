package me.XBound.xBound.listeners;

import me.XBound.xBound.XBound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Objects;

public class MiningXpListener implements Listener {

    private final XBound plugin;

    public MiningXpListener(XBound plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        // Default XP per block
        double xp = plugin.getConfig().getDouble("xp.default-block", 0.1);

        // Ore-specific overrides (from config: xp.ores.<MATERIAL>)
        if (plugin.getConfig().isConfigurationSection("xp.ores")) {
            String key = type.name();
            if (Objects.requireNonNull(plugin.getConfig().getConfigurationSection("xp.ores")).contains(key)) {
                xp = plugin.getConfig().getDouble("xp.ores." + key, xp);
            }
        }

        // Global multiplier
        double globalMult = plugin.getConfig().getDouble("xp.multipliers.global", 1.0);
        xp *= globalMult;

        // World-specific multiplier
        String worldName = player.getWorld().getName();
        if (plugin.getConfig().isConfigurationSection("xp.multipliers.worlds")) {
            if (plugin.getConfig().getConfigurationSection("xp.multipliers.worlds").contains(worldName)) {
                double worldMult = plugin.getConfig().getDouble("xp.multipliers.worlds." + worldName, 1.0);
                xp *= worldMult;
            }
        }

        // ✅ Add XP via helper
        if (xp > 0) {
            plugin.modifyStoredXp(player.getUniqueId(), xp);
        }
    }
}
package me.XBound.xBound.commands;

import me.XBound.xBound.XBound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RulesCommand implements CommandExecutor, Listener {

    private final XBound plugin;
    private YamlConfiguration rulesConfig;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Map<Player, Component> openTitles = new ConcurrentHashMap<>();

    public RulesCommand(XBound plugin) {
        this.plugin = plugin;
        setupRulesFile();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void setupRulesFile() {
        File rulesFile = new File(plugin.getDataFolder(), "rules.yml");
        if (!rulesFile.exists()) {
            File parent = rulesFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder for rules.yml!");
            }
            plugin.saveResource("rules.yml", false);
        }
        rulesConfig = YamlConfiguration.loadConfiguration(rulesFile);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use /rules.");
            return true;
        }
        openRulesGui(p);
        return true;
    }

    private void openRulesGui(Player player) {
        String titleRaw = rulesConfig.getString("title", "<black>Rules");
        Component title = MM.deserialize(titleRaw);
        openTitles.put(player, title);

        int rows = Math.max(1, Math.min(6, rulesConfig.getInt("rows", 3)));
        Inventory gui = Bukkit.createInventory(null, rows * 9, title);

        ConfigurationSection itemsSection = rulesConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", 0);
                String name = itemSec.getString("name", "<white>Item");
                List<String> loreList = itemSec.getStringList("lore");

                gui.setItem(slot, makeItem(itemSec.getString("material", "KNOWLEDGE_BOOK"), name, loreList));
            }
        }

        player.openInventory(gui);
    }

    private ItemStack makeItem(String materialName, String title, List<String> loreLines) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.KNOWLEDGE_BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(title));
        if (loreLines != null && !loreLines.isEmpty()) {
            meta.lore(loreLines.stream().map(MM::deserialize).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;

        Component expected = openTitles.get(player);
        if (expected != null && event.getView().title().equals(expected)) {
            event.setCancelled(true);
        }
    }
}
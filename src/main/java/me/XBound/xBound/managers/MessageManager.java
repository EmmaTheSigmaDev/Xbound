package me.XBound.xBound.managers;

import me.XBound.xBound.XBound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {

    private final XBound plugin;
    private FileConfiguration messages;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MessageManager(XBound plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public Component get(String key, Object... placeholders) {
        String lang = messages.getString("lang", "en");
        String raw = messages.getString(lang + "." + key, "<red>Missing message: " + key);

        // replace placeholders {0}, {1}, etc.
        for (int i = 0; i < placeholders.length; i++) {
            raw = raw.replace("{" + i + "}", String.valueOf(placeholders[i]));
        }
        return MM.deserialize(raw);
    }
}
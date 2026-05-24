package me.abhiram.puddlesplus.file;

import me.abhiram.puddlesplus.PuddlesPlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class AbstractFile {
    private final PuddlesPlus plugin;
    private final File file;
    protected FileConfiguration configuration;


    public AbstractFile(PuddlesPlus plugin, String filename, String datafolder, boolean saveResource) {
        this.plugin = plugin;
        File directory = datafolder == null || datafolder.isBlank()
                ? plugin.getDataFolder()
                : new File(plugin.getDataFolder(), datafolder);

        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Unable to create config directory: " + directory.getPath());
        }

        file = new File(directory, filename);
        if (!file.exists()) {
            if (saveResource) {
                this.plugin.saveResource(filename, false);
                configuration = YamlConfiguration.loadConfiguration(file);
                return;
            }

            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Unable to create config file: " + file.getPath());
                }
            } catch (IOException exp) {
                plugin.getLogger().log(Level.WARNING, "Unable to create config file: " + file.getPath(), exp);
            }
        }

        configuration = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to save config file: " + file.getPath(), e);
        }
    }

    public FileConfiguration getConfig() {
        return configuration;
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(file);
    }

    public void delete() {
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Unable to delete config file: " + file.getPath());
        }
    }
}

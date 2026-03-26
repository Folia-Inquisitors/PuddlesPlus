package me.abhiram.puddlesplus.file;

import me.abhiram.puddlesplus.PuddlesPlus;

public class PluginConfig extends AbstractFile {


    public PluginConfig(PuddlesPlus plugin) {
        super(plugin, "config.yml", "", true);
    }


}

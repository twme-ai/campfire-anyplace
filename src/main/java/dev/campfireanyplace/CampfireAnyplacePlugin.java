package dev.campfireanyplace;

import org.bukkit.plugin.java.JavaPlugin;

public final class CampfireAnyplacePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new CampfireListener(this), this);
    }
}

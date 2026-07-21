package dev.campfireanyplace;

import org.bukkit.plugin.java.JavaPlugin;

public final class CampfireAnyplacePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        CampfireListener listener = new CampfireListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.stabilizeLoadedCampfires();
    }
}

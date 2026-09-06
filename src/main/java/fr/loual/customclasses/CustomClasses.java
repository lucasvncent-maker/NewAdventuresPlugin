package fr.loual.customclasses;

import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.commands.ClassCommand;
import fr.loual.customclasses.listeners.ClassListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomClasses extends JavaPlugin {

    private ClassManager classManager;

    @Override
    public void onEnable() {
        this.classManager = new ClassManager(this);

        getServer().getPluginManager().registerEvents(new ClassListener(this), this);

        ClassCommand classCommand = new ClassCommand(this);
        PluginCommand cmd = getCommand("class");
        if (cmd != null) {
            cmd.setExecutor(classCommand);
            cmd.setTabCompleter(classCommand);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            classManager.refreshPlayer(player);
        }

        getLogger().info("CustomClasses v" + getPluginMeta().getVersion() + " est activé !");
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomClasses a été désactivé.");
    }

    public ClassManager getClassManager() {
        return classManager;
    }
}

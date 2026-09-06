package fr.loual.customclasses;

import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.commands.ClassCommand;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.JobRecipes;
import fr.loual.customclasses.jobs.commands.JobCommand;
import fr.loual.customclasses.jobs.listeners.JobListener;
import fr.loual.customclasses.listeners.ClassListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomClasses extends JavaPlugin {

    private ClassManager classManager;
    private JobManager jobManager;

    @Override
    public void onEnable() {
        this.classManager = new ClassManager(this);
        this.jobManager = new JobManager(this);

        // Enregistrement des écouteurs d'événements
        getServer().getPluginManager().registerEvents(new ClassListener(this), this);
        getServer().getPluginManager().registerEvents(new JobListener(this), this);

        // Enregistrement des recettes personnalisées
        JobRecipes.registerRecipes(this);

        // Commande /class
        ClassCommand classCommand = new ClassCommand(this);
        PluginCommand cmdClass = getCommand("class");
        if (cmdClass != null) {
            cmdClass.setExecutor(classCommand);
            cmdClass.setTabCompleter(classCommand);
        }

        // Commande /job
        JobCommand jobCommand = new JobCommand(this);
        PluginCommand cmdJob = getCommand("job");
        if (cmdJob != null) {
            cmdJob.setExecutor(jobCommand);
            cmdJob.setTabCompleter(jobCommand);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            classManager.refreshPlayer(player);
        }

        getLogger().info("CustomClasses v" + getPluginMeta().getVersion() + " (Classes + Métiers) est activé !");
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomClasses a été désactivé.");
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public JobManager getJobManager() {
        return jobManager;
    }
}

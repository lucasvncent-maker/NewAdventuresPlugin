package fr.loual.newadventure;

import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.commands.ClassCommand;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.JobRecipes;
import fr.loual.customclasses.jobs.commands.CraftCommand;
import fr.loual.customclasses.jobs.commands.JobCommand;
import fr.loual.customclasses.jobs.commands.JumpBoostCommand;
import fr.loual.customclasses.jobs.commands.NightVisionCommand;
import fr.loual.customclasses.jobs.commands.StonecutterCommand;
import fr.loual.customclasses.jobs.listeners.JobListener;
import fr.loual.customclasses.listeners.ClassListener;
import fr.loual.customminerals.commands.CustomMineralsCommand;
import fr.loual.customminerals.listeners.CopperMiningListener;
import fr.loual.customminerals.listeners.CupriteChestListener;
import fr.loual.customminerals.listeners.DeathChestListener;
import fr.loual.customminerals.listeners.HammerMiningListener;
import fr.loual.customminerals.listeners.SmithingListener;
import fr.loual.customminerals.listeners.SpawnerMiningListener;
import fr.loual.customminerals.listeners.TreeMiningListener;
import fr.loual.customminerals.recipes.RecipeManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class NewAdventurePlugin extends JavaPlugin {

    private ClassManager classManager;
    private JobManager jobManager;
    private RecipeManager mineralRecipeManager;

    @Override
    public void onEnable() {
        // Sauvegarde de la configuration (custom minerals, cuprite, etc.)
        saveDefaultConfig();

        // 1. Initialisation des modules de classes et métiers
        this.classManager = new ClassManager(this);
        this.jobManager = new JobManager(this);

        // 2. Initialisation des minéraux & Cuprite
        this.mineralRecipeManager = new RecipeManager(this);
        this.mineralRecipeManager.registerRecipes();

        // 3. Enregistrement des écouteurs d'événements
        PluginManager pm = getServer().getPluginManager();
        // Classes & Métiers
        pm.registerEvents(new ClassListener(this), this);
        pm.registerEvents(new JobListener(this), this);
        // CustomMinerals
        pm.registerEvents(new CopperMiningListener(this), this);
        pm.registerEvents(new HammerMiningListener(this), this);
        pm.registerEvents(new SpawnerMiningListener(this), this);
        pm.registerEvents(new SmithingListener(this), this);
        pm.registerEvents(new TreeMiningListener(this), this);
        pm.registerEvents(new CupriteChestListener(this), this);
        pm.registerEvents(new DeathChestListener(this), this);
        pm.registerEvents(this.mineralRecipeManager, this);

        // 4. Enregistrement des recettes artisanales de métiers
        JobRecipes.registerRecipes(this);

        // 5. Commandes /class
        ClassCommand classCommand = new ClassCommand(this);
        PluginCommand cmdClass = getCommand("class");
        if (cmdClass != null) {
            cmdClass.setExecutor(classCommand);
            cmdClass.setTabCompleter(classCommand);
        }

        // 6. Commandes /job
        JobCommand jobCommand = new JobCommand(this);
        PluginCommand cmdJob = getCommand("job");
        if (cmdJob != null) {
            cmdJob.setExecutor(jobCommand);
            cmdJob.setTabCompleter(jobCommand);
        }

        // 7. Commandes /cuprite & /customminerals
        CustomMineralsCommand mineralCommand = new CustomMineralsCommand(this);
        PluginCommand cupriteCmd = getCommand("cuprite");
        if (cupriteCmd != null) {
            cupriteCmd.setExecutor(mineralCommand);
            cupriteCmd.setTabCompleter(mineralCommand);
        }
        PluginCommand customMineralsCmd = getCommand("customminerals");
        if (customMineralsCmd != null) {
            customMineralsCmd.setExecutor(mineralCommand);
            customMineralsCmd.setTabCompleter(mineralCommand);
        }

        // 8. Commande /nv (Night Vision pour Mineur & Architecte)
        NightVisionCommand nvCommand = new NightVisionCommand(this);
        PluginCommand cmdNv = getCommand("nv");
        if (cmdNv != null) {
            cmdNv.setExecutor(nvCommand);
            cmdNv.setTabCompleter(nvCommand);
        }

        // 8bis. Commandes de l'Architecte (/craft, /stonecutter, /jb)
        CraftCommand craftCommand = new CraftCommand(this);
        PluginCommand cmdCraft = getCommand("craft");
        if (cmdCraft != null) {
            cmdCraft.setExecutor(craftCommand);
            cmdCraft.setTabCompleter(craftCommand);
        }

        StonecutterCommand scCommand = new StonecutterCommand(this);
        PluginCommand cmdSc = getCommand("stonecutter");
        if (cmdSc != null) {
            cmdSc.setExecutor(scCommand);
            cmdSc.setTabCompleter(scCommand);
        }

        JumpBoostCommand jbCommand = new JumpBoostCommand(this);
        PluginCommand cmdJb = getCommand("jb");
        if (cmdJb != null) {
            cmdJb.setExecutor(jbCommand);
            cmdJb.setTabCompleter(jbCommand);
        }

        // 9. Tâche périodique pour les auras sous la couche Y=30 (Mineur M3) et l'armure de l'Architecte
        getServer().getScheduler().runTaskTimer(this, () -> {
            jobManager.tickLayerEffects();
            jobManager.tickArmorEffects();
        }, 20L, 20L);

        // 10. Export du resource pack au format .zip
        exportResourcePackZip();

        // 11. Rafraîchissement des effets pour les joueurs déjà connectés
        for (Player player : getServer().getOnlinePlayers()) {
            classManager.refreshPlayer(player);
            jobManager.applyJobEffects(player);
        }

        getLogger().info("newAdventurePlugin v" + getPluginMeta().getVersion() + " (Classes + Métiers + Cuprite) est activé !");
    }

    @Override
    public void onDisable() {
        getLogger().info("newAdventurePlugin a été désactivé.");
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public RecipeManager getMineralRecipeManager() {
        return mineralRecipeManager;
    }

    public RecipeManager getRecipeManager() {
        return mineralRecipeManager;
    }

    private void exportResourcePackZip() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File zipFile = new File(dataFolder, "newAdventurePack.zip");

            // 1. Si un dossier local ../resourcepack existe
            File localRp = new File("../resourcepack");
            if (localRp.exists() && localRp.isDirectory()) {
                createZipFromFolder(localRp.toPath(), zipFile.toPath());
                getLogger().info("Pack de ressources généré dans " + zipFile.getName());
                return;
            }

            // 2. Extraire directement depuis les ressources embarquées dans le jar
            try {
                java.net.URL jarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
                File jarFile = new File(jarUrl.toURI());
                if (jarFile.isFile()) {
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile);
                         ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {

                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                        String prefix = "resourcepack/";
                        boolean foundAny = false;
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(prefix) && name.length() > prefix.length() && !entry.isDirectory()) {
                                String relName = name.substring(prefix.length());
                                zos.putNextEntry(new ZipEntry(relName));
                                try (InputStream is = jar.getInputStream(entry)) {
                                    is.transferTo(zos);
                                }
                                zos.closeEntry();
                                foundAny = true;
                            }
                        }
                        if (foundAny) {
                            getLogger().info("Pack de ressources extrait depuis le jar dans " + zipFile.getName());
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Impossible d'extraire le resourcepack depuis le jar: " + e.getMessage());
            }
        } catch (Exception e) {
            getLogger().warning("Impossible de créer newAdventurePack.zip: " + e.getMessage());
        }
    }

    private void createZipFromFolder(Path sourceDirPath, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Files.walk(sourceDirPath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(sourceDirPath.relativize(path).toString().replace('\\', '/'));
                try {
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    getLogger().warning("Erreur lors de l'archivage: " + e.getMessage());
                }
            });
        }
    }
}

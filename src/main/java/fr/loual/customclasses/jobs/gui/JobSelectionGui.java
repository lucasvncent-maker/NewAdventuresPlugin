package fr.loual.customclasses.jobs.gui;

import fr.loual.customclasses.jobs.AgriculteurMissions;
import fr.loual.customclasses.jobs.ArchitecteMissions;
import fr.loual.customclasses.jobs.AventurierMissions;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.JobMission;
import fr.loual.customclasses.jobs.MineurMissions;
import fr.loual.customclasses.jobs.PlayerJob;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class JobSelectionGui {

    public static final NamespacedKey JOB_ICON_KEY = new NamespacedKey("customclasses", "job_choice");
    public static final NamespacedKey MISSION_ITEM_KEY = new NamespacedKey("customclasses", "mission_num");
    public static final NamespacedKey RECIPE_BOOK_KEY = new NamespacedKey("customclasses", "recipe_book_btn");

    public static void open(NewAdventurePlugin plugin, Player player) {
        JobGuiHolder holder = new JobGuiHolder();
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("✦ Métiers & Missions ✦", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        // Fond vitres teintées
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, glass);
        }

        JobManager jobManager = plugin.getJobManager();
        PlayerJob currentJob = jobManager.getPlayerJob(player);

        // 1. Icônes des métiers (Slot 10 : Agriculteur, Slot 12 : Mineur, Slot 14 : Architecte, Slot 16 : Aventurier)
        ItemStack agriIcon = createJobIcon(player, PlayerJob.AGRICULTEUR, currentJob, jobManager);
        inv.setItem(10, agriIcon);

        ItemStack mineurIcon = createJobIcon(player, PlayerJob.MINEUR, currentJob, jobManager);
        inv.setItem(12, mineurIcon);

        ItemStack archIcon = createJobIcon(player, PlayerJob.ARCHITECTE, currentJob, jobManager);
        inv.setItem(14, archIcon);

        ItemStack aventurierIcon = createJobIcon(player, PlayerJob.AVENTURIER, currentJob, jobManager);
        inv.setItem(16, aventurierIcon);

        // 2. Affichage des missions selon le métier actif
        if (currentJob == PlayerJob.AGRICULTEUR) {
            int completedLevel = jobManager.getJobLevel(player, PlayerJob.AGRICULTEUR);

            int[] missionSlots = { 20, 21, 22, 23 };
            for (int i = 0; i < 4; i++) {
                JobMission mission = AgriculteurMissions.getMission(i + 1);
                if (mission != null) {
                    ItemStack missionItem = createMissionItem(player, jobManager, PlayerJob.AGRICULTEUR, mission, completedLevel);
                    inv.setItem(missionSlots[i], missionItem);
                }
            }

            // Bouton du Livre de Recettes au slot 31
            ItemStack recipeBook = new ItemStack(Material.KNOWLEDGE_BOOK);
            ItemMeta bookMeta = recipeBook.getItemMeta();
            if (bookMeta != null) {
                bookMeta.displayName(Component.text("✦ Livre des Recettes de l'Agriculteur ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
                bookMeta.lore(List.of(
                        Component.text("Consultez toutes les recettes exclusives", NamedTextColor.GRAY),
                        Component.text("de l'Agriculteur avec leur patron de craft !", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("➜ Cliquez pour ouvrir le livre des recettes", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ));
                bookMeta.getPersistentDataContainer().set(RECIPE_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
                recipeBook.setItemMeta(bookMeta);
            }
            inv.setItem(31, recipeBook);

        } else if (currentJob == PlayerJob.MINEUR) {
            int completedLevel = jobManager.getJobLevel(player, PlayerJob.MINEUR);

            int[] missionSlots = { 20, 22, 24 };
            for (int i = 0; i < 3; i++) {
                JobMission mission = MineurMissions.getMission(i + 1);
                if (mission != null) {
                    ItemStack missionItem = createMissionItem(player, jobManager, PlayerJob.MINEUR, mission, completedLevel);
                    inv.setItem(missionSlots[i], missionItem);
                }
            }

            // Guide Mineur au slot 31
            ItemStack minerInfo = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = minerInfo.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(Component.text("✦ Guide du Mineur ✦", NamedTextColor.AQUA, TextDecoration.BOLD));
                infoMeta.lore(List.of(
                        Component.text("Passif : 25% de chance de bonus Fortune sur les minerais", NamedTextColor.YELLOW),
                        Component.text("M1 : Célérité I permanent + Commande /nv pour la Vision Nocturne", NamedTextColor.GRAY),
                        Component.text("M2 : 5% de drop de Cuprite + 1 Fortune supplémentaire garanti", NamedTextColor.GRAY),
                        Component.text("M3 : Célérité II + Bénédiction sous la couche Y=30 (Regen, Résistance, Feu)", NamedTextColor.GRAY)
                ));
                minerInfo.setItemMeta(infoMeta);
            }
            inv.setItem(31, minerInfo);

        } else if (currentJob == PlayerJob.ARCHITECTE) {
            int completedLevel = jobManager.getJobLevel(player, PlayerJob.ARCHITECTE);

            int[] missionSlots = { 20, 21, 22, 23, 24 };
            for (int i = 0; i < 5; i++) {
                JobMission mission = ArchitecteMissions.getMission(i + 1);
                if (mission != null) {
                    ItemStack missionItem = createMissionItem(player, jobManager, PlayerJob.ARCHITECTE, mission, completedLevel);
                    inv.setItem(missionSlots[i], missionItem);
                }
            }

            // Guide de l'Architecte au slot 31
            ItemStack archInfo = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = archInfo.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(Component.text("✦ Guide de l'Architecte ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
                infoMeta.lore(List.of(
                        Component.text("Outils nomades : /craft (/wb) et /stonecutter (/sc)", NamedTextColor.YELLOW),
                        Component.text("M1 : Commande /craft pour ouvrir un établi partout", NamedTextColor.GRAY),
                        Component.text("M2 : Commande /sc + Chapeau (Vitesse II)", NamedTextColor.GRAY),
                        Component.text("M3 : Chemise (Célérité II)", NamedTextColor.GRAY),
                        Component.text("M4 : Pantalon (Vision Nocturne /nv)", NamedTextColor.GRAY),
                        Component.text("M5 : Chaussures (Saut II /jb) + Plume (Vol 30s) !", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("✦ L'armure complète de 4 pièces active le vol avec la Plume !", NamedTextColor.AQUA)
                ));
                archInfo.setItemMeta(infoMeta);
            }
            inv.setItem(31, archInfo);

        } else if (currentJob == PlayerJob.AVENTURIER) {
            int completedLevel = jobManager.getJobLevel(player, PlayerJob.AVENTURIER);

            int[] missionSlots = { 20, 22, 24 };
            for (int i = 0; i < 3; i++) {
                JobMission mission = AventurierMissions.getMission(i + 1);
                if (mission != null) {
                    ItemStack missionItem = createMissionItem(player, jobManager, PlayerJob.AVENTURIER, mission, completedLevel);
                    inv.setItem(missionSlots[i], missionItem);
                }
            }

            // Guide de l'Aventurier au slot 31
            ItemStack advInfo = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = advInfo.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(Component.text("✦ Guide de l'Aventurier ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
                infoMeta.lore(List.of(
                        Component.text("Passif : Vitesse I permanente + Trésors rares dans les coffres de structures", NamedTextColor.YELLOW),
                        Component.text("M1 : 5 structures Overworld ➔ Meilleurs butins + /sethome & /home", NamedTextColor.GRAY),
                        Component.text("M2 : 5 biomes du Nether ➔ Perle Infinie (infinie & sans dégât de chute)", NamedTextColor.GRAY),
                        Component.text("M3 : 3 Pommes Cheat, 3 Élytres, 8 Éponges ➔ Élytres Incassables + Fusée Infinie !", NamedTextColor.GRAY)
                ));
                advInfo.setItemMeta(infoMeta);
            }
            inv.setItem(31, advInfo);

        } else {
            // Indication pour choisir le métier
            ItemStack info = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(Component.text("Information", NamedTextColor.YELLOW, TextDecoration.BOLD));
                infoMeta.lore(List.of(
                        Component.text("Cliquez sur l'un des métiers ci-dessus", NamedTextColor.GRAY),
                        Component.text("pour sélectionner votre voie et débloquer ses missions !", NamedTextColor.GRAY)
                ));
                info.setItemMeta(infoMeta);
            }
            inv.setItem(22, info);
        }

        player.openInventory(inv);
    }

    private static ItemStack createJobIcon(Player player, PlayerJob job, PlayerJob currentJob, JobManager jm) {
        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            boolean isCurrent = (job == currentJob);
            NamedTextColor titleColor = isCurrent ? NamedTextColor.GREEN : NamedTextColor.GOLD;

            meta.displayName(Component.text(job.getDisplayName(), titleColor, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>(job.getDescription());
            lore.add(Component.empty());

            int maxMissions = (job == PlayerJob.AGRICULTEUR) ? 4 : (job == PlayerJob.ARCHITECTE ? 5 : 3);
            if (isCurrent) {
                int level = jm.getJobLevel(player, job);
                lore.add(Component.text("✔ Métier actif - Niveau de mission : " + level + " / " + maxMissions, NamedTextColor.GREEN, TextDecoration.BOLD));
            } else {
                lore.add(Component.text("➜ Cliquez pour devenir " + job.getDisplayName() + " !", NamedTextColor.YELLOW, TextDecoration.BOLD));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(JOB_ICON_KEY, PersistentDataType.STRING, job.getId());
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createMissionItem(Player player, JobManager jm, PlayerJob job, JobMission mission, int completedLevel) {
        int missionNum = mission.getLevel();
        boolean isCompleted = (completedLevel >= missionNum);
        boolean isCurrent = (completedLevel == missionNum - 1);

        Material mat;
        NamedTextColor statusColor;
        String statusText;

        if (isCompleted) {
            mat = Material.LIME_CONCRETE;
            statusColor = NamedTextColor.GREEN;
            statusText = "✔ Accomplie";
        } else if (isCurrent) {
            mat = Material.YELLOW_CONCRETE;
            statusColor = NamedTextColor.GOLD;
            statusText = "➜ En cours";
        } else {
            mat = Material.GRAY_CONCRETE;
            statusColor = NamedTextColor.DARK_GRAY;
            statusText = "🔒 Verrouillée";
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(mission.getTitle(), statusColor, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Statut : " + statusText, statusColor));
            lore.add(Component.empty());
            lore.add(Component.text("Objectifs :", NamedTextColor.YELLOW, TextDecoration.BOLD));

            for (JobMission.Requirement req : mission.getRequirements()) {
                int progress = isCompleted ? req.requiredAmount() : jm.getRequirementProgress(player, job, missionNum, req.key());
                NamedTextColor reqColor = (progress >= req.requiredAmount()) ? NamedTextColor.GREEN : NamedTextColor.GRAY;
                lore.add(Component.text("  • " + req.displayName() + " : " + progress + " / " + req.requiredAmount(), reqColor));
            }

            lore.add(Component.empty());
            lore.add(Component.text("✦ Récompense :", NamedTextColor.AQUA, TextDecoration.BOLD));
            lore.add(Component.text("  " + mission.getRewardDescription(), NamedTextColor.WHITE));

            if (job == PlayerJob.AGRICULTEUR) {
                if (missionNum == 1) {
                    lore.add(Component.empty());
                    lore.add(Component.text("✦ Recette de craft :", NamedTextColor.GOLD, TextDecoration.BOLD));
                    lore.add(Component.text("  • 1x Bol + 1x Carotte + 1x Patate + 1x Blé", NamedTextColor.YELLOW));
                    lore.add(Component.text("➜ Clic pour voir la recette dans l'établi", NamedTextColor.AQUA));
                } else if (missionNum == 3) {
                    lore.add(Component.empty());
                    lore.add(Component.text("✦ Recette de craft :", NamedTextColor.GOLD, TextDecoration.BOLD));
                    lore.add(Component.text("  • 1x Cookie + 1x Baie lumineuse", NamedTextColor.YELLOW));
                    lore.add(Component.text("➜ Clic pour voir la recette dans l'établi", NamedTextColor.AQUA));
                } else if (missionNum == 4) {
                    lore.add(Component.empty());
                    lore.add(Component.text("✦ Recettes de craft :", NamedTextColor.GOLD, TextDecoration.BOLD));
                    lore.add(Component.text("  • Soupe Merveilleuse (9 récoltes)", NamedTextColor.YELLOW));
                    lore.add(Component.text("  • Houe Merveilleuse (2 Cuivres + 2 Bâtons)", NamedTextColor.YELLOW));
                    lore.add(Component.text("➜ Clic pour voir les recettes dans l'établi", NamedTextColor.AQUA));
                }
            }

            if (isCurrent) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.getPersistentDataContainer().set(MISSION_ITEM_KEY, PersistentDataType.INTEGER, missionNum);
            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
}

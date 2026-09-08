package fr.loual.casino.stats;

import fr.loual.newadventure.NewAdventurePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CasinoStatsManager {

    private final NewAdventurePlugin plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;

    public record PlayerStats(UUID uuid, String name, int handsPlayed, int handsWon, int naturalBlackjacks, int highestChips, int cupritesWon) {
        public double getWinRate() {
            if (handsPlayed <= 0) return 0.0;
            return ((double) handsWon / handsPlayed) * 100.0;
        }
    }

    public CasinoStatsManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "casino_stats.yml");
        load();
    }

    public synchronized void load() {
        if (!statsFile.exists()) {
            try {
                if (statsFile.getParentFile() != null) statsFile.getParentFile().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de créer casino_stats.yml: " + e.getMessage());
            }
        }
        this.statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public synchronized void save() {
        if (statsConfig == null || statsFile == null) return;
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder casino_stats.yml: " + e.getMessage());
        }
    }

    public synchronized PlayerStats getStats(UUID uuid, String defaultName) {
        String key = "players." + uuid.toString();
        if (!statsConfig.contains(key)) {
            return new PlayerStats(uuid, defaultName != null ? defaultName : "Inconnu", 0, 0, 0, 0, 0);
        }
        String name = statsConfig.getString(key + ".name", defaultName != null ? defaultName : "Inconnu");
        int played = statsConfig.getInt(key + ".hands_played", 0);
        int won = statsConfig.getInt(key + ".hands_won", 0);
        int naturals = statsConfig.getInt(key + ".natural_blackjacks", 0);
        int highestChips = statsConfig.getInt(key + ".highest_chips", 0);
        int cuprites = statsConfig.getInt(key + ".cuprites_won", 0);
        return new PlayerStats(uuid, name, played, won, naturals, highestChips, cuprites);
    }

    public synchronized void recordHand(Player player, boolean won, boolean naturalBlackjack, int currentChips) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String key = "players." + uuid;

        int played = statsConfig.getInt(key + ".hands_played", 0) + 1;
        int wonCount = statsConfig.getInt(key + ".hands_won", 0) + (won ? 1 : 0);
        int naturals = statsConfig.getInt(key + ".natural_blackjacks", 0) + (naturalBlackjack ? 1 : 0);
        int highest = statsConfig.getInt(key + ".highest_chips", 0);
        if (currentChips > highest) {
            highest = currentChips;
        }

        statsConfig.set(key + ".name", player.getName());
        statsConfig.set(key + ".hands_played", played);
        statsConfig.set(key + ".hands_won", wonCount);
        statsConfig.set(key + ".natural_blackjacks", naturals);
        statsConfig.set(key + ".highest_chips", highest);
        save();
    }

    public synchronized void recordCuprite(Player player, int amount) {
        if (player == null || amount <= 0) return;
        UUID uuid = player.getUniqueId();
        String key = "players." + uuid;

        int cuprites = statsConfig.getInt(key + ".cuprites_won", 0) + amount;
        statsConfig.set(key + ".name", player.getName());
        statsConfig.set(key + ".cuprites_won", cuprites);
        save();
    }

    public synchronized void updateHighestChips(Player player, int chips) {
        if (player == null || chips <= 0) return;
        UUID uuid = player.getUniqueId();
        String key = "players." + uuid;

        int current = statsConfig.getInt(key + ".highest_chips", 0);
        if (chips > current) {
            statsConfig.set(key + ".name", player.getName());
            statsConfig.set(key + ".highest_chips", chips);
            save();
        }
    }

    public synchronized List<PlayerStats> getAllStats() {
        List<PlayerStats> list = new ArrayList<>();
        if (!statsConfig.contains("players")) return list;
        var section = statsConfig.getConfigurationSection("players");
        if (section == null) return list;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                list.add(getStats(uuid, null));
            } catch (Exception ignored) {}
        }
        return list;
    }

    public List<PlayerStats> getTopByCuprites(int limit) {
        List<PlayerStats> all = getAllStats();
        all.sort((a, b) -> Integer.compare(b.cupritesWon(), a.cupritesWon()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public List<PlayerStats> getTopByHighestChips(int limit) {
        List<PlayerStats> all = getAllStats();
        all.sort((a, b) -> Integer.compare(b.highestChips(), a.highestChips()));
        return all.subList(0, Math.min(limit, all.size()));
    }
}

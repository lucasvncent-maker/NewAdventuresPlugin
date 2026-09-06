package fr.loual.customclasses.jobs;

import org.bukkit.Material;

import java.util.List;

public class JobMission {

    public record Requirement(String key, String displayName, Material icon, int requiredAmount) {}

    private final int level;
    private final String title;
    private final String rewardDescription;
    private final List<Requirement> requirements;

    public JobMission(int level, String title, String rewardDescription, List<Requirement> requirements) {
        this.level = level;
        this.title = title;
        this.rewardDescription = rewardDescription;
        this.requirements = requirements;
    }

    public int getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public String getRewardDescription() {
        return rewardDescription;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public Requirement getRequirement(String key) {
        for (Requirement req : requirements) {
            if (req.key().equalsIgnoreCase(key)) {
                return req;
            }
        }
        return null;
    }
}

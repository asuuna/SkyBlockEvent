/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

public final class EventDefinition {

    private final String id;
    private final String displayName;
    private final EventType type;
    private final int durationSeconds;
    private final int targetScore;
    private final int pointsPerAction;
    private final double dropMultiplier;
    private final Set<Material> materials;
    private final ComboSettings comboSettings;
    private final CustomDropSettings customDropSettings;
    private final Map<Integer, MilestoneReward> serverMilestones;
    private final Map<Integer, MilestoneReward> personalMilestones;
    private final Map<Integer, List<String>> topRewardCommands;
    private final int participationMinScore;
    private final List<String> participationCommands;

    public EventDefinition(
        String id,
        String displayName,
        EventType type,
        int durationSeconds,
        int targetScore,
        int pointsPerAction,
        double dropMultiplier,
        Set<Material> materials,
        ComboSettings comboSettings,
        CustomDropSettings customDropSettings,
        Map<Integer, MilestoneReward> serverMilestones,
        Map<Integer, MilestoneReward> personalMilestones,
        Map<Integer, List<String>> topRewardCommands,
        int participationMinScore,
        List<String> participationCommands
    ) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.durationSeconds = durationSeconds;
        this.targetScore = targetScore;
        this.pointsPerAction = pointsPerAction;
        this.dropMultiplier = dropMultiplier;
        this.materials = Set.copyOf(materials);
        this.comboSettings = comboSettings == null ? ComboSettings.DISABLED : comboSettings;
        this.customDropSettings = customDropSettings == null ? CustomDropSettings.DISABLED : customDropSettings;
        this.serverMilestones = copyMilestoneMap(serverMilestones);
        this.personalMilestones = copyMilestoneMap(personalMilestones);
        this.topRewardCommands = copyRewardMap(topRewardCommands);
        this.participationMinScore = participationMinScore;
        this.participationCommands = List.copyOf(participationCommands);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EventType getType() {
        return type;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getTargetScore() {
        return targetScore;
    }

    public int getPointsPerAction() {
        return pointsPerAction;
    }

    public double getDropMultiplier() {
        return dropMultiplier;
    }

    public boolean matchesMaterial(Material material) {
        return materials.isEmpty() || materials.contains(material);
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public ComboSettings getComboSettings() {
        return comboSettings;
    }

    public CustomDropSettings getCustomDropSettings() {
        return customDropSettings;
    }

    public Map<Integer, MilestoneReward> getServerMilestones() {
        return serverMilestones;
    }

    public Map<Integer, MilestoneReward> getPersonalMilestones() {
        return personalMilestones;
    }

    public Map<Integer, List<String>> getTopRewardCommands() {
        return topRewardCommands;
    }

    public int getParticipationMinScore() {
        return participationMinScore;
    }

    public List<String> getParticipationCommands() {
        return participationCommands;
    }

    private Map<Integer, MilestoneReward> copyMilestoneMap(Map<Integer, MilestoneReward> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return source.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<Integer, List<String>> copyRewardMap(Map<Integer, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return source.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}

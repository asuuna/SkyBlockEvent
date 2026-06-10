package com.skyblockevent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ActiveEventTest {

    @Test
    void scoreIsAccumulatedAndSortedDescending() {
        EventDefinition definition = definition(Map.of(), Map.of(), ComboSettings.DISABLED);
        ActiveEvent activeEvent = new ActiveEvent(definition, Instant.now(), 600_000L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        activeEvent.addScore(first, "Alpha", 5);
        activeEvent.addScore(second, "Beta", 10);
        activeEvent.addScore(first, "Alpha", 8);

        List<ParticipantScore> scores = activeEvent.getSortedScores();

        assertEquals(23, activeEvent.getTotalScore());
        assertEquals("Alpha", scores.get(0).getPlayerName());
        assertEquals(13, scores.get(0).getScore());
        assertEquals("Beta", scores.get(1).getPlayerName());
    }

    @Test
    void comboAndMilestonesAreClaimedOnce() {
        MilestoneReward serverMilestone = new MilestoneReward(10, "server-milestone", List.of());
        MilestoneReward personalMilestone = new MilestoneReward(10, "personal-milestone", List.of());
        EventDefinition definition = definition(
            Map.of(10, serverMilestone),
            Map.of(10, personalMilestone),
            new ComboSettings(true, 5_000L, 1, 0.5D, 2.0D, true)
        );
        ActiveEvent activeEvent = new ActiveEvent(definition, Instant.now(), 600_000L);
        UUID player = UUID.randomUUID();

        ScoreUpdate first = activeEvent.recordAction(player, "Alpha", 4, 1_000L);
        ScoreUpdate second = activeEvent.recordAction(player, "Alpha", 4, 2_000L);
        ScoreUpdate third = activeEvent.recordAction(player, "Alpha", 4, 3_000L);

        assertEquals(4, first.getAwardedPoints());
        assertEquals(6, second.getAwardedPoints());
        assertEquals(8, third.getAwardedPoints());
        assertEquals(18, third.getTotalScore());
        assertEquals(1, second.getServerMilestones().size());
        assertEquals(1, second.getPersonalMilestones().size());
        assertEquals(0, third.getServerMilestones().size());
        assertEquals(0, third.getPersonalMilestones().size());
    }

    private EventDefinition definition(
        Map<Integer, MilestoneReward> serverMilestones,
        Map<Integer, MilestoneReward> personalMilestones,
        ComboSettings comboSettings
    ) {
        return new EventDefinition(
            "mine-rush",
            "Mine Rush",
            EventType.MINING,
            600,
            100,
            1,
            1.0D,
            Set.of(),
            comboSettings,
            CustomDropSettings.DISABLED,
            serverMilestones,
            personalMilestones,
            Map.of(),
            0,
            List.of()
        );
    }
}

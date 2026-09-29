package io.pzstorm.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkshopUpdateTest {

    @Test
    void anyFailureFromARunningSteamBlocksTheJoin() {
        assertTrue(joinBlocked(new WorkshopUpdate.Result(false, 67, 67, true)));
        assertTrue(joinBlocked(new WorkshopUpdate.Result(false, 1, 1, true)));
        assertTrue(
                joinBlocked(new WorkshopUpdate.Result(false, 2, 67, true)),
                "a client missing even one item is mismatched with the server");
    }

    @Test
    void noChildForUnknownReasonsFallsBackToTheInGameFlow() {
        assertFalse(
                joinBlocked(new WorkshopUpdate.Result(false, 67, 67, false)),
                "jar-on-disk missing leaves Steam's state unknown — the game may still boot");
        assertFalse(joinBlocked(new WorkshopUpdate.Result(true, 0, 0, false)));
    }

    @Test
    void steamProvablyNotRunningBlocksTheJoin() {
        SteamRestartRequiredException blocker =
                JoinFlow.joinBlocker(
                        new WorkshopUpdate.Result(false, 67, 67, false, true, Set.of()));
        assertTrue(
                blocker.summary().contains("Steam is not running"),
                "a Steam-build game exe can only die at its own Steam error screen");
        assertTrue(blocker.getMessage().contains("Start Steam"));
    }

    @Test
    void successNeverBlocksTheJoin() {
        assertFalse(joinBlocked(new WorkshopUpdate.Result(true, 0, 67, true)));
        assertNull(JoinFlow.joinBlocker(new WorkshopUpdate.Result(true, 0, 67, true)));
    }

    @Test
    void blockerNamesTheFailedItems() {
        Set<String> failed = new LinkedHashSet<>();
        failed.add("3752227135");
        failed.add("3671847630");
        SteamRestartRequiredException blocker =
                JoinFlow.joinBlocker(new WorkshopUpdate.Result(false, 2, 67, true, failed));
        assertTrue(blocker.summary().contains("2 of 67"));
        assertTrue(blocker.summary().contains("3752227135, 3671847630"));
        assertTrue(blocker.getMessage().contains("Restart Steam"));
        assertTrue(blocker.getMessage().contains("One/Life Discord"));
    }

    @Test
    void blockerCountsFailuresWhenNoIdsWereParsed() {
        SteamRestartRequiredException blocker =
                JoinFlow.joinBlocker(new WorkshopUpdate.Result(false, 3, 67, true));
        assertTrue(blocker.summary().contains("3 of 67"));
    }

    @Test
    void repairFailureMessageNamesItemsAndTheWorkingFix() {
        String message = JoinFlow.repairFailedMessage(java.util.List.of("2928660831"));
        assertTrue(message.contains("2928660831"));
        assertTrue(message.contains("unsubscribe"), "the fix that works must be spelled out");
        assertTrue(
                message.contains("Restarting Steam does NOT clear this"),
                "must counter the restart-Steam advice the other failure paths give");
    }

    @Test
    void nonZeroExitWithNoParsedFailuresStillBlocks() {
        SteamRestartRequiredException blocker =
                JoinFlow.joinBlocker(new WorkshopUpdate.Result(false, 0, 67, true));
        assertTrue(blocker.summary().contains("1 of 67"));
    }

    @Test
    void failedItemIdsAreExposed() {
        Set<String> failed = new LinkedHashSet<>();
        failed.add("3670772371");
        WorkshopUpdate.Result r = new WorkshopUpdate.Result(false, 1, 67, true, failed);
        assertTrue(r.failedItemIds.contains("3670772371"));
        assertEquals(1, r.failedItemIds.size());
    }

    @Test
    void failedItemIdsDefaultsEmptyOnLegacyConstructor() {
        WorkshopUpdate.Result r = new WorkshopUpdate.Result(true, 0, 0, false);
        assertTrue(r.failedItemIds.isEmpty());
    }

    private static boolean joinBlocked(WorkshopUpdate.Result result) {
        return JoinFlow.joinBlocker(result) != null;
    }

    @Test
    void childArgsPrefixesVerifyAndCheckIdsInOrder() {
        assertEquals(
                java.util.List.of("1", "2", "verify:3", "verify:4", "check:5", "check:6"),
                WorkshopUpdate.childArgs(
                        java.util.List.of("1", "2"),
                        java.util.List.of("3", "4"),
                        java.util.List.of("5", "6")));
    }

    @Test
    void childArgsWithNoVerifyOrCheckIdsIsJustTheUpdateIds() {
        assertEquals(
                java.util.List.of("1", "2"),
                WorkshopUpdate.childArgs(
                        java.util.List.of("1", "2"), java.util.List.of(), java.util.List.of()));
    }

    @Test
    void startMessageWithNothingExtraMatchesTheOriginalText() {
        assertEquals(
                "Updating 3 workshop item(s) via Steam …", WorkshopUpdate.startMessage(3, 0, 0));
    }

    @Test
    void startMessageAppendsQuickStateCheckSuffix() {
        assertEquals(
                "Updating 3 workshop item(s) via Steam (+2 quick state check(s)) …",
                WorkshopUpdate.startMessage(3, 2, 0));
    }

    @Test
    void startMessageAppendsPublishedVersionCheckSuffix() {
        assertEquals(
                "Updating 3 workshop item(s) via Steam (+5 published-version check(s)) …",
                WorkshopUpdate.startMessage(3, 0, 5));
    }

    @Test
    void startMessageAppendsBothSuffixesInOrder() {
        assertEquals(
                "Updating 3 workshop item(s) via Steam (+2 quick state check(s)) (+5"
                        + " published-version check(s)) …",
                WorkshopUpdate.startMessage(3, 2, 5));
    }
}

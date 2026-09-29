package io.pzstorm.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The pure parts of the published-version check; none of these connect to Steam. */
class SteamUgcDetailsQueryTest {

    private static final long FILE_ID = 3141592653L;
    private static final int RESULT_OFFSET = 8;

    /** Well past 2010-01-01 and well before now + 1 day for any test run. */
    private static final long SANE_TIME_UPDATED = 1_700_000_000L;

    /** Large enough to hold m_rtimeUpdated at either callback pack size's offset (8168 or 8172). */
    private static final int DETAILS_BUFFER_SIZE = 8176;

    private static MemorySegment syntheticDetails(
            long fileId, int result, long timeUpdated, boolean packSmall) {
        int timeOffset = packSmall ? 8168 : 8172;
        ByteBuffer buffer = ByteBuffer.allocate(DETAILS_BUFFER_SIZE).order(ByteOrder.nativeOrder());
        buffer.putLong(0, fileId);
        buffer.putInt(RESULT_OFFSET, result);
        buffer.putInt(timeOffset, (int) timeUpdated);
        return MemorySegment.ofBuffer(buffer);
    }

    @Test
    void structReadersFollowSteamUgcDetailsLayoutBothPackSizes() {
        for (boolean packSmall : new boolean[] {true, false}) {
            MemorySegment details =
                    syntheticDetails(FILE_ID, SteamUgc.RESULT_OK, SANE_TIME_UPDATED, packSmall);
            assertEquals(
                    FILE_ID, SteamUgc.detailsPublishedFileId(details), "packSmall=" + packSmall);
            assertEquals(
                    SteamUgc.RESULT_OK, SteamUgc.detailsResult(details), "packSmall=" + packSmall);
            assertEquals(
                    SANE_TIME_UPDATED,
                    SteamUgc.detailsTimeUpdated(details, packSmall),
                    "packSmall=" + packSmall);
        }
    }

    @Test
    void detailsTimeUpdatedReadsUnsignedAndOffsetDependsOnPackSize() {
        // past 2038 the raw int32 is negative; the reader must return it unsigned
        long past2038 = 3_000_000_000L;
        MemorySegment small = syntheticDetails(FILE_ID, SteamUgc.RESULT_OK, past2038, true);
        MemorySegment wide = syntheticDetails(FILE_ID, SteamUgc.RESULT_OK, past2038, false);
        assertEquals(past2038, SteamUgc.detailsTimeUpdated(small, true));
        assertEquals(past2038, SteamUgc.detailsTimeUpdated(wide, false));

        // reading at the other pack size's offset must not find the value
        assertEquals(0L, SteamUgc.detailsTimeUpdated(small, false));
        assertEquals(0L, SteamUgc.detailsTimeUpdated(wide, true));
    }

    @Test
    void isCurrentByTimestampTruthTable() {
        int joinReady = SteamUgc.STATE_SUBSCRIBED | SteamUgc.STATE_INSTALLED;
        int notJoinReady =
                SteamUgc.STATE_SUBSCRIBED | SteamUgc.STATE_INSTALLED | SteamUgc.STATE_NEEDS_UPDATE;

        // known, equal, join-ready -> current
        assertTrue(SteamUgc.isCurrentByTimestamp(OptionalLong.of(100), 100L, joinReady));

        // published stamp unknown (null) -> never current, regardless of state
        assertFalse(SteamUgc.isCurrentByTimestamp(OptionalLong.of(100), null, joinReady));

        // install stamp unknown (not installed) -> never current
        assertFalse(SteamUgc.isCurrentByTimestamp(OptionalLong.empty(), 100L, joinReady));

        // stamps differ -> not current even though join-ready
        assertFalse(SteamUgc.isCurrentByTimestamp(OptionalLong.of(99), 100L, joinReady));

        // stamps equal but not join-ready (e.g. needs update) -> not current
        assertFalse(SteamUgc.isCurrentByTimestamp(OptionalLong.of(100), 100L, notJoinReady));

        assertFalse(SteamUgc.isCurrentByTimestamp(OptionalLong.empty(), null, 0));
    }

    @Test
    void isSaneDetailsAcceptsARequestedIdWithAPlausibleTimestamp() {
        Set<Long> requested = Set.of(FILE_ID, 42L);
        long now = SANE_TIME_UPDATED + 1_000;
        assertTrue(SteamUgc.isSaneDetails(FILE_ID, SANE_TIME_UPDATED, requested, now));
    }

    @Test
    void isSaneDetailsRejectsAnUnrequestedPublishedFileId() {
        Set<Long> requested = Set.of(42L, 99L);
        long now = SANE_TIME_UPDATED + 1_000;
        assertFalse(SteamUgc.isSaneDetails(FILE_ID, SANE_TIME_UPDATED, requested, now));
    }

    @Test
    void isSaneDetailsRejectsATimestampBeforeTheSaneFloor() {
        Set<Long> requested = Set.of(FILE_ID);
        long now = SANE_TIME_UPDATED + 1_000;
        long before2010 = 1_262_303_999L; // one second before 2010-01-01T00:00:00Z
        assertFalse(SteamUgc.isSaneDetails(FILE_ID, before2010, requested, now));
        assertFalse(SteamUgc.isSaneDetails(FILE_ID, 0L, requested, now));
    }

    @Test
    void isSaneDetailsRejectsATimestampMoreThanADayInTheFuture() {
        Set<Long> requested = Set.of(FILE_ID);
        long now = 1_700_000_000L;
        long justOverOneDayAhead = now + 86_400 + 1;
        assertFalse(SteamUgc.isSaneDetails(FILE_ID, justOverOneDayAhead, requested, now));
        assertTrue(SteamUgc.isSaneDetails(FILE_ID, now + 86_400, requested, now));
    }

    /** A failed entry (deleted, banned, foreign-private) is skipped, not treated as corrupt. */
    @Test
    void detailsOutcomeSkipsANonOkEntryWithAZeroTimestampWithoutVoidingTheBatch() {
        Set<Long> requested = Set.of(FILE_ID);
        long now = SANE_TIME_UPDATED;
        int banned = 2; // k_EResultFail; any non-OK EResult exercises the same path
        assertEquals(
                SteamUgc.DetailsOutcome.SKIP,
                SteamUgc.detailsOutcome(banned, FILE_ID, 0L, requested, now));
    }

    /** An OK entry with a zero timestamp means a layout mismatch. */
    @Test
    void detailsOutcomeVoidsTheBatchWhenAnOkEntryHasAZeroTimestamp() {
        Set<Long> requested = Set.of(FILE_ID);
        long now = SANE_TIME_UPDATED;
        assertEquals(
                SteamUgc.DetailsOutcome.UNSANE,
                SteamUgc.detailsOutcome(SteamUgc.RESULT_OK, FILE_ID, 0L, requested, now));
    }

    /** An OK entry with an id we never asked for means a layout mismatch. */
    @Test
    void detailsOutcomeVoidsTheBatchWhenAnOkEntryHasAnUnrequestedId() {
        Set<Long> requested = Set.of(42L, 99L);
        long now = SANE_TIME_UPDATED;
        assertEquals(
                SteamUgc.DetailsOutcome.UNSANE,
                SteamUgc.detailsOutcome(
                        SteamUgc.RESULT_OK, FILE_ID, SANE_TIME_UPDATED, requested, now));
    }

    @Test
    void detailsOutcomeTrustsAnOkEntryThatPassesTheSanityCheck() {
        Set<Long> requested = Set.of(FILE_ID);
        long now = SANE_TIME_UPDATED;
        assertEquals(
                SteamUgc.DetailsOutcome.TRUSTED,
                SteamUgc.detailsOutcome(
                        SteamUgc.RESULT_OK, FILE_ID, SANE_TIME_UPDATED, requested, now));
    }
}

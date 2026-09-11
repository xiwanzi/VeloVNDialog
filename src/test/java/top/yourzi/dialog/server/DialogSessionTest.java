package top.yourzi.dialog.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import top.yourzi.dialog.model.DialogSequence;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class DialogSessionTest {
    private final UUID player = UUID.randomUUID();
    private final Predicate<String> visible = command -> true;

    private DialogSession session(String entries) {
        return new DialogSession(player, new Gson().fromJson(
                "{\"id\":\"test\",\"entries\":" + entries + "}", DialogSequence.class));
    }

    private DialogSession linear() {
        return session("""
                [{"id":"intro","command":["scoreboard players add @s story 1","give @s apple"]},
                 {"id":"end","commands":["tag @s add done"]}]
                """);
    }

    private DialogSession choices() {
        return session("""
                [{"id":"choose","commands":["tag @s add read"],"options":[
                  {"text":"A","target":"end","command":["tag @s add a"],"visibility_command":"condition"},
                  {"text":"B","target":"end","commands":["tag @s add b"]}]},
                 {"id":"end"}]
                """);
    }

    @Test void rejectsDifferentPlayersAndForgedSessions() {
        var s = linear();
        assertNull(s.transition(UUID.randomUUID(), s.id(), 0, "intro", -1, visible));
        assertNull(s.transition(player, UUID.randomUUID(), 0, "intro", -1, visible));
        assertEquals(0, s.revision());
        assertEquals("intro", s.entryId());
    }

    @Test void rejectsForgedNodeAndOutOfOrderRevision() {
        var s = linear();
        assertNull(s.transition(player, s.id(), 0, "end", -1, visible));
        assertNull(s.transition(player, s.id(), 0, "give @s diamond", -1, visible));
        assertNull(s.transition(player, s.id(), 1, "intro", -1, visible));
        assertNull(s.transition(player, s.id(), -1, "intro", -1, visible));
        assertEquals(0, s.revision());
    }

    @Test void advanceExecutesAllConfiguredCommandsOnceAndAcceptsBothFieldNames() {
        var s = linear();
        var first = s.transition(player, s.id(), 0, "intro", DialogSession.ADVANCE, visible);
        assertEquals(List.of("scoreboard players add @s story 1", "give @s apple"), first.commands());
        assertEquals("end", first.entryId());
        assertNull(s.transition(player, s.id(), 0, "intro", DialogSession.ADVANCE, visible));
        var end = s.transition(player, s.id(), 1, "end", DialogSession.ADVANCE, visible);
        assertEquals(List.of("tag @s add done"), end.commands());
        assertEquals("", end.entryId());
        assertNull(s.transition(player, s.id(), 1, "end", DialogSession.ADVANCE, visible));
    }

    @Test void cannotFastForwardPastChoicesOrChooseAnInvalidOption() {
        var s = choices();
        for (int index : new int[] {-1, -3, 2, Integer.MAX_VALUE}) {
            assertNull(s.transition(player, s.id(), 0, "choose", index, visible));
        }
        assertEquals("choose", s.entryId());
        assertEquals(0, s.revision());
    }

    @Test void chosenOptionRunsOnlyItsCommandsAfterTheEntryCommands() {
        var s = choices();
        var result = s.transition(player, s.id(), 0, "choose", 1, visible);
        assertEquals(List.of("tag @s add read", "tag @s add b"), result.commands());
        assertEquals("end", result.entryId());
        assertNull(s.transition(player, s.id(), 0, "choose", 0, visible));
    }

    @Test void rechecksOptionConditionAtSubmission() {
        var s = choices();
        assertNull(s.transition(player, s.id(), 0, "choose", 0, condition -> false));
        assertEquals(0, s.revision());
        assertEquals(List.of("tag @s add read", "tag @s add a"),
                s.transition(player, s.id(), 0, "choose", 0, condition -> true).commands());
    }

    @Test void rechecksEntryConditionBeforeExecutingRewards() {
        var s = session("""
                [{"id":"intro","visibility_command":"condition","commands":["give @s diamond"]}]
                """);
        assertNull(s.transition(player, s.id(), 0, "intro", -1, condition -> false));
        assertEquals(0, s.revision());
    }

    @Test void wrongOptionCannotExecuteCommandsOnALinearEntry() {
        var s = linear();
        assertNull(s.transition(player, s.id(), 0, "intro", 0, visible));
        assertEquals(0, s.revision());
    }

    @Test void cancellationExecutesNoRemainingCommandsEvenAfterAnInFlightAdvance() {
        var s = linear();
        s.transition(player, s.id(), 0, "intro", -1, visible);
        var cancel = s.transition(player, s.id(), 0, "intro", DialogSession.CANCEL, visible);
        assertTrue(cancel.commands().isEmpty());
        assertEquals("", cancel.entryId());
        assertNull(s.transition(player, s.id(), 1, "end", -1, visible));
    }

    @Test void cancellationIsStillBoundToPlayerAndSession() {
        var s = linear();
        assertNull(s.transition(UUID.randomUUID(), s.id(), 0, "intro", DialogSession.CANCEL, visible));
        assertNull(s.transition(player, UUID.randomUUID(), 0, "intro", DialogSession.CANCEL, visible));
        assertEquals("intro", s.entryId());
    }

    @Test void aNewSessionRejectsRequestsFromThePreviousOpening() {
        var old = linear();
        var fresh = linear();
        assertNotEquals(old.id(), fresh.id());
        assertNull(fresh.transition(player, old.id(), 0, "intro", -1, visible));
    }

    @Test void loopsCannotReplayAnEarlierRevision() {
        var s = session("""
                [{"id":"loop","next":"loop","commands":["tag @s add visited"]}]
                """);
        assertNotNull(s.transition(player, s.id(), 0, "loop", -1, visible));
        assertNull(s.transition(player, s.id(), 0, "loop", -1, visible));
        assertNotNull(s.transition(player, s.id(), 1, "loop", -1, visible));
    }

    @Test void missingTargetsDoNotExecuteCommands() {
        var s = session("""
                [{"id":"intro","next":"missing","commands":["give @s diamond"]}]
                """);
        assertNull(s.transition(player, s.id(), 0, "intro", -1, visible));
        assertEquals(0, s.revision());
    }

    @Test void duplicateOrMissingEntryIdsCannotCreateSessions() {
        assertThrows(IllegalArgumentException.class, () -> session("[{\"id\":\"x\"},{\"id\":\"x\"}]"));
        assertThrows(IllegalArgumentException.class, () -> session("[{}]"));
        assertThrows(IllegalArgumentException.class, () -> session("[]"));
    }
}

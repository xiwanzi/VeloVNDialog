package top.yourzi.dialog.core;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DialogCoreTest {
    private final UUID owner = UUID.randomUUID();
    private final TestContext context = new TestContext();

    private DialogSession session(String entries) {
        return new DialogSession(owner, DialogCompiler.compile("{\"id\":\"test\",\"entries\":" + entries + "}"), context, new Random(1));
    }
    private DialogSession.Frame advance(DialogSession s) { return s.act(owner, s.id(), s.frame().revision(), DialogSession.ADVANCE, "", context); }
    private DialogSession.Frame choose(DialogSession s, String id) { return s.act(owner, s.id(), s.frame().revision(), DialogSession.CHOOSE, id, context); }

    @Test void sameNpcSelectsDifferentRoutesForDifferentPlayers() {
        var def = DialogCompiler.compile("""
            {"id":"guard","start":[
              {"when":{"var":{"faction":"a"}},"target":"a"},
              {"when":{"var":{"faction":"b"}},"target":"b"},{"target":"neutral"}],
             "entries":[{"id":"a","text":"Welcome A","end":true},{"id":"b","text":"Welcome B","end":true},
                        {"id":"neutral","text":"Choose a faction"}]}
            """);
        context.state.set("faction", new JsonPrimitive("a"));
        var other = new TestContext(); other.state.set("faction", new JsonPrimitive("b"));
        var a = new DialogSession(owner, def, context, new Random(1));
        var b = new DialogSession(UUID.randomUUID(), def, other, new Random(1));
        assertTrue(a.frame().entryJson().contains("Welcome A"));
        assertTrue(b.frame().entryJson().contains("Welcome B"));
        assertFalse(a.frame().entryJson().contains("Welcome B"));
        assertFalse(b.frame().entryJson().contains("Welcome A"));
        assertTrue(new DialogSession(UUID.randomUUID(), def, new TestContext(), new Random()).frame().entryJson().contains("Choose a faction"));
    }

    @Test void stateChangeUnlocksALaterNodeInsteadOfDeletingItAtOpening() {
        var s = session("""
            [{"id":"start","text":"Join","options":[{"id":"join","text":"Join A","set":{"faction":"a"},"target":"a"}]},
             {"id":"a","when":{"var":{"faction":"a"}},"text":"Welcome ${faction}","end":true}]
            """);
        assertTrue(choose(s, "join").entryJson().contains("Welcome a"));
        assertEquals("a", context.state.get("faction").getAsString());
    }

    @Test void falseNodesAreSkippedWithoutExecutingTheirActions() {
        var s = session("""
            [{"id":"first","text":"first"},{"id":"hidden","when":{"permission":"secret"},"command":"give @s diamond"},
             {"id":"last","text":"last"}]
            """);
        assertTrue(advance(s).entryJson().contains("last"));
        assertTrue(context.commands.isEmpty());
    }

    @Test void permissionChangesAreRecheckedWithoutConsumingTheChoice() {
        context.permissions.add("quest.access");
        var s = session("""
            [{"id":"start","options":[{"id":"yes","text":"Yes","when":{"permission":"quest.access"},"command":"reward","target":"end"}]},
             {"id":"end"}]
            """);
        context.permissions.clear();
        var result = choose(s, "yes");
        assertEquals("refreshed", result.reason());
        assertTrue(JsonParser.parseString(result.entryJson()).getAsJsonObject().getAsJsonArray("options").isEmpty());
        assertTrue(context.commands.isEmpty());
        assertNull(advance(s)); // No visible options does not authorize bypassing the choice.
        context.permissions.add("quest.access");
        s.refresh(context);
        choose(s, "yes");
        assertEquals(List.of("reward"), context.commands);
    }

    @Test void currentAndRootConditionsAreRechecked() {
        context.permissions.add("access");
        var def = DialogCompiler.compile("""
            {"id":"secret","when":{"permission":"access"},"entries":[{"id":"start","command":"reward"}]}
            """);
        var s = new DialogSession(owner, def, context, new Random());
        context.permissions.clear();
        assertEquals("unavailable", advance(s).reason());
        assertTrue(s.closed()); assertTrue(context.commands.isEmpty());
    }

    @Test void commandsAndVariableChangesExecuteOnceForAllAdvanceModes() {
        var s = session("""
            [{"id":"start","set":{"seen":true},"add":{"visits":1},"commands":["first","second"]},{"id":"end","command":"last"}]
            """);
        advance(s);
        assertNull(s.act(owner, s.id(), 0, DialogSession.ADVANCE, "", context));
        assertEquals(List.of("first", "second"), context.commands);
        assertEquals(1, context.state.get("visits").getAsInt());
        assertTrue(context.state.get("seen").getAsBoolean());
        advance(s); assertTrue(s.closed());
        assertEquals(List.of("first", "second", "last"), context.commands);
    }

    @Test void rejectsWrongPlayerSessionRevisionAndUnOfferedOptions() {
        var s = session("""
            [{"id":"start","options":[{"id":"visible","text":"yes","target":"end"},
              {"id":"hidden","text":"secret","when":{"permission":"secret"},"command":"reward","target":"end"}]},{"id":"end"}]
            """);
        assertNull(s.act(UUID.randomUUID(), s.id(), 0, DialogSession.CHOOSE, "visible", context));
        assertNull(s.act(owner, UUID.randomUUID(), 0, DialogSession.CHOOSE, "visible", context));
        assertNull(s.act(owner, s.id(), -1, DialogSession.CHOOSE, "visible", context));
        assertNull(s.act(owner, s.id(), 1, DialogSession.CHOOSE, "visible", context));
        assertNull(choose(s, "hidden")); assertNull(choose(s, "give @s diamond")); assertNull(advance(s));
        assertTrue(context.commands.isEmpty()); assertEquals(0, s.frame().revision());
        assertFalse(s.frame().entryJson().contains("secret")); assertFalse(s.frame().entryJson().contains("reward"));
    }

    @Test void nodeAndOptionMutationsPrecedeLegacyCommandsInOrder() {
        var s = session("""
            [{"id":"start","set":{"faction":"neutral"},"command":"node","options":[
              {"id":"a","text":"A","set":{"faction":"a"},"add":{"visits":1},"command":"option","target":"end"}]},
             {"id":"end","text":"${faction}"}]
            """);
        context.commandCallback = command -> assertEquals("a", context.state.get("faction").getAsString());
        choose(s, "a"); assertEquals(List.of("node", "option"), context.commands);
    }

    @Test void reentrantRequestsCannotRunActionsTwice() {
        var s = session("[{\"id\":\"start\",\"command\":\"reward\"}]");
        context.commandCallback = command -> assertNull(s.act(owner, s.id(), 0, DialogSession.ADVANCE, "", context));
        advance(s); assertEquals(List.of("reward"), context.commands);
    }

    @Test void actionFailureClosesTheSessionAndCannotBeReplayed() {
        var s = session("[{\"id\":\"start\",\"command\":\"failing\"}]");
        context.commandCallback = command -> { throw new IllegalStateException("failure"); };
        assertThrows(IllegalStateException.class, () -> advance(s));
        assertTrue(s.closed()); assertEquals("error", s.frame().reason());
        assertNull(s.act(owner, s.id(), 0, DialogSession.ADVANCE, "", context));
        assertEquals(List.of("failing"), context.commands);
    }

    @Test void cancellationDoesNotExecuteRemainingActionsAndOldSessionsExpire() {
        var s = session("[{\"id\":\"start\",\"command\":\"reward\"}]");
        assertNull(s.act(UUID.randomUUID(), s.id(), 0, DialogSession.CANCEL, "", context));
        assertEquals("cancelled", s.act(owner, s.id(), -1, DialogSession.CANCEL, "", context).reason());
        assertNull(advance(s)); assertTrue(context.commands.isEmpty());
        var fresh = session("[{\"id\":\"start\"}]");
        assertNotEquals(s.id(), fresh.id());
        assertNull(fresh.act(owner, s.id(), 0, DialogSession.ADVANCE, "", context));
    }

    @Test void optionalCloseRestrictionIsEnforcedByTheServer() {
        var def = DialogCompiler.compile("{\"id\":\"locked\",\"allowClose\":false,\"entries\":[{\"id\":\"start\"}]}");
        var s = new DialogSession(owner, def, context, new Random());
        assertNull(s.act(owner, s.id(), 0, DialogSession.CANCEL, "", context));
        assertFalse(s.closed());
    }

    @Test void randomTextIsPickedPerVisitAndDoesNotRerollOnRefreshOrReplay() {
        AtomicInteger draws = new AtomicInteger();
        Random rng = new Random() { @Override public int nextInt(int bound) { draws.incrementAndGet(); return 1; } };
        var def = DialogCompiler.compile("""
            {"id":"random","entries":[{"id":"start","text":{"random":["Hello",{"text":"Hi @i","color":"gold"}]},"next":"start"}]}
            """);
        var s = new DialogSession(owner, def, context, rng);
        assertTrue(s.frame().entryJson().contains("Hi Player")); assertTrue(s.frame().entryJson().contains("gold"));
        s.refresh(context); assertEquals(1, draws.get());
        assertNull(s.act(owner, s.id(), 0, DialogSession.ADVANCE, "", context)); assertEquals(1, draws.get());
        advance(s); assertEquals(2, draws.get()); assertEquals(1, s.frame().visit());
    }

    @Test void papiUsesCurrentPlayerAndRunsAfterActionsForTheNextNode() {
        context.name = "Alice";
        context.papi = text -> text.replace("%xconomy_balance_value%", context.state.get("balance").isJsonNull() ? "0" : context.state.get("balance").getAsString());
        var s = session("""
            [{"id":"first","text":"@i has %xconomy_balance_value%","set":{"balance":42}},
             {"id":"second","text":{"text":"@i has %xconomy_balance_value%","color":"red"}}]
            """);
        assertTrue(s.frame().entryJson().contains("Alice has 0"));
        assertTrue(advance(s).entryJson().contains("Alice has 42"));
        assertTrue(s.frame().entryJson().contains("red"));
        assertFalse(s.frame().entryJson().contains("\"set\""));
    }

    @Test void legacyTextArraysRemainConcatenatedAndTagConditionsRemainUsable() {
        context.tags.add("member"); context.tags.add("execute if test");
        var s = session("""
            [{"id":"first","text":["hello ",{"text":"@i","color":"gold"}],"when":{"tag":"member"},"visibility_command":"execute if test"}]
            """);
        var text = JsonParser.parseString(s.frame().entryJson()).getAsJsonObject().getAsJsonArray("text");
        assertEquals(2, text.size()); assertTrue(s.frame().entryJson().contains("Player"));
        assertFalse(context.conditions.isEmpty());
    }

    @Test void hiddenCyclesAreDetectedButVisibleLoopsRemainValid() {
        assertThrows(IllegalStateException.class, () -> session("[{\"id\":\"a\",\"when\":{\"permission\":\"missing\"},\"next\":\"a\"}]"));
        var s = session("[{\"id\":\"a\",\"next\":\"a\"}]"); advance(s);
        assertEquals(1, s.frame().visit());
    }

    @ParameterizedTest @ValueSource(strings = {
        "{\"id\":\"x\",\"entries\":[]}",
        "{\"id\":\"x\",\"start\":\"missing\",\"entries\":[{\"id\":\"a\"}]}",
        "{\"id\":\"x\",\"entries\":[{\"id\":\"a\"},{\"id\":\"a\"}]}",
        "{\"id\":\"x\",\"entries\":[{\"id\":\"a\",\"next\":\"missing\"}]}",
        "{\"id\":\"x\",\"entries\":[{\"id\":\"a\",\"text\":{\"random\":[]}}]}",
        "{\"id\":\"x\",\"entries\":[{\"id\":\"a\",\"when\":{\"permisson\":\"secret\"}}]}",
        "{\"id\":\"x\",\"entries\":[{\"id\":\"a\",\"set\":{\"faction\":{\"bad\":1}}}]}"
    }) void invalidDefinitionsFailAtLoadTime(String json) { assertThrows(IllegalArgumentException.class, () -> DialogCompiler.compile(json)); }

    @Test void bundledLegacyExampleStillCompiles() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/dialog/dialogs/test_dialog.json")) {
            assertNotNull(stream);
            assertEquals("test_dialog", DialogCompiler.compile(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).id());
        }
    }
}

package top.yourzi.dialog.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityTest {
    @Test void optionalLegacyEmptyValuesStillWork() {
        var definition = DialogCompiler.compile("""
            {"id":"legacy","start":"","entries":[{"id":"a","next":"","visibility_command":"","command":["",null]}, {"id":"b"}]}
            """);
        assertEquals("b", definition.nodes().get("a").next());
        assertTrue(definition.nodes().get("a").actions().commands().isEmpty());
    }
    @Test void anExitOptionDoesNotRequireAnEmptyDummyNode() {
        var definition = DialogCompiler.compile("""
            {"id":"exit","entries":[{"id":"a","options":[{"id":"bye","text":"Bye","end":true}]}]}
            """);
        UUID player = UUID.randomUUID(); var context = new TestContext();
        var session = new DialogSession(player, definition, context, new Random());
        assertEquals("complete", session.act(player, session.id(), 0, DialogSession.CHOOSE, "bye", context).reason());
    }
    @Test void badRequestsDoNotReexecuteRootLegacyConditions() {
        var definition = DialogCompiler.compile("""
            {"id":"legacy","visibility_command":"check","entries":[{"id":"a"}]}
            """);
        var context = new TestContext(); context.tags.add("check"); UUID player = UUID.randomUUID();
        var session = new DialogSession(player, definition, context, new Random());
        int before = context.conditions.size();
        assertNull(session.act(player, session.id(), 0, 999, "", context));
        assertEquals(before, context.conditions.size());
    }
    @Test void shippedExamplesCompile() throws Exception {
        for (String name : new String[]{"/guard.json", "/minimal.json"}) {
            try (var stream = getClass().getResourceAsStream(name)) {
                assertNotNull(stream);
                assertNotNull(DialogCompiler.compile(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
    }
    @Test void malformedMediaIsRejectedBeforeItReachesAClient() {
        assertThrows(IllegalArgumentException.class, () -> DialogCompiler.compile("{\"id\":\"broken\",\"entries\":[{\"id\":\"a\",\"portraits\":{\"path\":\"a.png\"}}]}"));
    }
}

package top.yourzi.dialog.server;

import org.junit.jupiter.api.Test;
import top.yourzi.dialog.core.DialogCompiler;
import top.yourzi.dialog.core.DialogSession;
import top.yourzi.dialog.core.TestContext;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DialogCatalogTest {
    @Test void invalidReloadCannotReplaceTheActiveCatalog() {
        var catalog = new DialogCatalog();
        var initial = DialogCatalog.compile(Map.of("one.json", "{\"id\":\"one\",\"entries\":[{\"id\":\"a\"}]}"));
        catalog.publish(initial);
        assertThrows(IllegalArgumentException.class, () -> catalog.publish(DialogCatalog.compile(Map.of("broken.json", "{}"))));
        assertSame(initial.get("one"), catalog.get("one"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.snapshot().clear());
    }
    @Test void duplicateDialogIdsFailWithSourceDiagnostics() {
        var error = assertThrows(IllegalArgumentException.class, () -> DialogCatalog.compile(Map.of(
            "a.json", "{\"id\":\"same\",\"entries\":[{\"id\":\"a\"}]}",
            "b.json", "{\"id\":\"same\",\"entries\":[{\"id\":\"b\"}]}")));
        assertTrue(error.getMessage().contains("Duplicate dialog ID"));
    }
    @Test void existingSessionsRetainTheirOriginalDefinition() {
        var catalog = new DialogCatalog();
        var old = DialogCompiler.compile("{\"id\":\"story\",\"entries\":[{\"id\":\"a\",\"text\":\"old\"},{\"id\":\"b\",\"text\":\"old ending\"}]}");
        catalog.publish(Map.of("story", old));
        var owner = UUID.randomUUID(); var context = new TestContext();
        var session = new DialogSession(owner, old, context, new Random());
        var replacement = DialogCompiler.compile("{\"id\":\"story\",\"entries\":[{\"id\":\"a\",\"text\":\"new\"}]}");
        catalog.publish(Map.of("story", replacement));
        assertNotEquals(old.version(), replacement.version());
        assertTrue(session.act(owner, session.id(), 0, DialogSession.ADVANCE, "", context).entryJson().contains("old ending"));
        assertSame(replacement, catalog.get("story"));
    }
}

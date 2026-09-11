package top.yourzi.dialog.server;

import org.junit.jupiter.api.Test;
import net.minecraft.server.level.ServerPlayer;
import top.yourzi.dialog.core.DialogCompiler;
import top.yourzi.dialog.core.DialogSession;
import top.yourzi.dialog.core.TestContext;
import top.yourzi.dialog.network.DialogActionPacket;
import top.yourzi.dialog.network.DialogFramePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerDialogSessionsTest {
    private final TestContext context = new TestContext();
    private final List<DialogFramePacket> sent = new ArrayList<>();
    private final ServerDialogSessions runtime = new ServerDialogSessions(player -> context, (player, packet) -> sent.add(packet));
    private final ServerPlayer player = player();

    private ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        return player;
    }
    private void open() { assertTrue(runtime.open(player, DialogCompiler.compile("{\"id\":\"test\",\"entries\":[{\"id\":\"a\",\"command\":\"reward\"},{\"id\":\"b\"}]}"))); }
    private DialogActionPacket request() { return new DialogActionPacket(sent.getFirst().frame().sessionId(), 0, DialogSession.ADVANCE, ""); }

    @Test void noSessionCannotExecuteAnything() {
        runtime.handle(player, new DialogActionPacket(UUID.randomUUID(), 0, DialogSession.ADVANCE, ""));
        assertTrue(context.commands.isEmpty()); assertTrue(sent.isEmpty());
    }
    @Test void wrongPlayersAndReplaysDoNotExecuteCommands() {
        open(); var action = request();
        runtime.handle(player(), action); assertTrue(context.commands.isEmpty());
        runtime.handle(player, action); runtime.handle(player, action);
        assertEquals(List.of("reward"), context.commands);
        assertEquals(2, sent.size());
        assertFalse(sent.getLast().opening());
    }
    @Test void logoutInvalidatesSession() {
        open(); var action = request();
        runtime.remove(player.getUUID()); runtime.handle(player, action);
        assertTrue(context.commands.isEmpty()); assertEquals(1, sent.size());
    }
    @Test void reentrantOpeningIsNotOverwritten() {
        open(); var old = request();
        context.commandCallback = command -> open();
        runtime.handle(player, old);
        assertEquals(2, sent.size()); assertTrue(sent.getLast().opening());
        assertNotEquals(old.sessionId(), sent.getLast().frame().sessionId());
    }
    @Test void failedActionSendsClosedErrorFrameAndCannotBeRetried() {
        open(); var action = request();
        context.commandCallback = command -> { throw new IllegalStateException("test failure"); };
        runtime.handle(player, action); runtime.handle(player, action);
        assertEquals(List.of("reward"), context.commands);
        assertEquals("error", sent.getLast().frame().reason());
        assertTrue(sent.getLast().frame().entryJson().isEmpty());
    }
}

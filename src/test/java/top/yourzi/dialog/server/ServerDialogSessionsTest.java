package top.yourzi.dialog.server;

import com.google.gson.Gson;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.network.DialogActionPacket;
import top.yourzi.dialog.network.DialogStatePacket;
import top.yourzi.dialog.network.ShowDialogPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerDialogSessionsTest {
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final Commands commands = mock(Commands.class);
    private final CommandSourceStack source = mock(CommandSourceStack.class, RETURNS_SELF);
    private final ServerPlayer player = player();

    private ServerPlayer player() {
        var result = mock(ServerPlayer.class);
        when(result.getUUID()).thenReturn(UUID.randomUUID());
        when(result.getServer()).thenReturn(server);
        when(result.createCommandSourceStack()).thenReturn(source);
        when(server.getCommands()).thenReturn(commands);
        return result;
    }

    private DialogSequence sequence() {
        return new Gson().fromJson("""
                {"id":"test","entries":[{"id":"start","command":["give @s apple"]},{"id":"end"}]}
                """, DialogSequence.class);
    }

    @AfterEach void clear() { ServerDialogSessions.clear(); }

    @Test void noActiveSessionCannotExecuteAnything() {
        ServerDialogSessions.handle(player, new DialogActionPacket(UUID.randomUUID(), 0, "start", -1));
        verifyNoInteractions(commands);
    }

    @Test void serverExecutesOnlyConfiguredCommandsAndRejectsOtherPlayersAndReplays() {
        List<CustomPacketPayload> sent = new ArrayList<>();
        try (var packets = mockStatic(PacketDistributor.class, call -> { sent.add(call.getArgument(1)); return null; })) {
            assertTrue(ServerDialogSessions.open(player, sequence()));
            var opened = (ShowDialogPacket) sent.getFirst();
            var action = new DialogActionPacket(opened.sessionId(), 0, "start", -1);
            ServerDialogSessions.handle(player(), action);
            ServerDialogSessions.handle(player, new DialogActionPacket(opened.sessionId(), 0, "give @s diamond", -1));
            verifyNoInteractions(commands);
            ServerDialogSessions.handle(player, action);
            ServerDialogSessions.handle(player, action);
            verify(commands, times(1)).performPrefixedCommand(source, "give @s apple");
            verifyNoMoreInteractions(commands);
            assertTrue(sent.stream().anyMatch(packet -> packet instanceof DialogStatePacket state
                    && state.revision() == 1 && state.entryId().equals("end")));
        }
    }

    @Test void logoutInvalidatesThePreviouslyIssuedSession() {
        List<CustomPacketPayload> sent = new ArrayList<>();
        try (var packets = mockStatic(PacketDistributor.class, call -> { sent.add(call.getArgument(1)); return null; })) {
            ServerDialogSessions.open(player, sequence());
            var opened = (ShowDialogPacket) sent.getFirst();
            ServerDialogSessions.remove(player.getUUID());
            ServerDialogSessions.handle(player, new DialogActionPacket(opened.sessionId(), 0, "start", -1));
            verifyNoInteractions(commands);
        }
    }

    @Test void commandOpeningAnotherDialogDoesNotGetOverwrittenByTheOldAcknowledgement() {
        List<CustomPacketPayload> sent = new ArrayList<>();
        try (var packets = mockStatic(PacketDistributor.class, call -> { sent.add(call.getArgument(1)); return null; })) {
            ServerDialogSessions.open(player, sequence());
            var opened = (ShowDialogPacket) sent.getFirst();
            doAnswer(call -> {
                ServerDialogSessions.open(player, sequence());
                return null;
            }).when(commands).performPrefixedCommand(source, "give @s apple");
            ServerDialogSessions.handle(player, new DialogActionPacket(opened.sessionId(), 0, "start", -1));
            assertEquals(2, sent.size());
            assertInstanceOf(ShowDialogPacket.class, sent.getLast());
            assertNotEquals(opened.sessionId(), ((ShowDialogPacket) sent.getLast()).sessionId());
        }
    }
}

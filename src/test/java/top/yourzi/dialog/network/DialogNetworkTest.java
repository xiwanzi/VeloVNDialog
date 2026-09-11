package top.yourzi.dialog.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DialogNetworkTest {
    @Test void loginSyncOnlySendsToTheJoiningPlayer() {
        var joining = mock(ServerPlayer.class);
        Map<String, String> dialogs = Map.of("intro", "{\"id\":\"intro\"}");
        try (var packets = mockStatic(PacketDistributor.class)) {
            NetworkHandler.sendAllDialogsToPlayer(joining, dialogs);
            packets.verify(() -> PacketDistributor.sendToPlayer(joining, new SyncAllDialogsPacket(dialogs)));
            packets.verifyNoMoreInteractions();
        }
    }

    @Test void emptyLoginSyncIsAlsoTargetedAndExplicitReloadStillBroadcasts() {
        var joining = mock(ServerPlayer.class);
        Map<String, String> empty = Map.of();
        try (var packets = mockStatic(PacketDistributor.class)) {
            NetworkHandler.sendAllDialogsToPlayer(joining, empty);
            packets.verify(() -> PacketDistributor.sendToPlayer(joining, new SyncAllDialogsPacket(empty)));
            packets.verifyNoMoreInteractions();
            packets.clearInvocations();
            NetworkHandler.sendAllDialogsToAllPlayers(empty);
            packets.verify(() -> PacketDistributor.sendToAllPlayers(new SyncAllDialogsPacket(empty)));
            packets.verifyNoMoreInteractions();
        }
    }

    @Test void actionCodecPreservesIdentityRevisionAndChoiceWithoutACommandField() {
        var packet = new DialogActionPacket(UUID.randomUUID(), 12, "choose", 3);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DialogActionPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, DialogActionPacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void actionCodecRejectsOversizedNodeIds() {
        var packet = new DialogActionPacket(UUID.randomUUID(), 0, "x".repeat(257), -1);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(io.netty.handler.codec.EncoderException.class,
                    () -> DialogActionPacket.STREAM_CODEC.encode(buffer, packet));
        } finally {
            buffer.release();
        }
    }
}

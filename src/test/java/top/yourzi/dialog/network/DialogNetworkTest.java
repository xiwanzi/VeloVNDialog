package top.yourzi.dialog.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.junit.jupiter.api.Test;
import top.yourzi.dialog.core.DialogSession;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DialogNetworkTest {
    @Test void protocolOnlyRegistersActionsAndFrames() {
        var event = mock(RegisterPayloadHandlersEvent.class);
        var registrar = mock(PayloadRegistrar.class, RETURNS_SELF);
        when(event.registrar("3")).thenReturn(registrar);
        NetworkHandler.register(event);
        verify(event).registrar("3");
        verify(registrar).executesOn(HandlerThread.NETWORK);
        verify(registrar).playToServer(eq(DialogActionPacket.TYPE), same(DialogActionPacket.STREAM_CODEC), any());
        verify(registrar).playToClient(eq(DialogFramePacket.TYPE), same(DialogFramePacket.STREAM_CODEC), any());
        verifyNoMoreInteractions(registrar);
    }
    @Test void actionCodecPreservesSessionRevisionAndOfferedId() {
        var original = new DialogActionPacket(UUID.randomUUID(), 7, DialogSession.CHOOSE, "join_a");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DialogActionPacket.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, DialogActionPacket.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void actionCodecBoundsIds() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(io.netty.handler.codec.EncoderException.class, () -> DialogActionPacket.STREAM_CODEC.encode(buffer,
                    new DialogActionPacket(UUID.randomUUID(), 0, 1, "x".repeat(257))));
        } finally { buffer.release(); }
    }
    @Test void aSingleViewCanExceedTheOldWholeScriptStringLimit() {
        var original = new DialogFramePacket(new DialogSession.Frame(UUID.randomUUID(), 2, 1, "guard", "字".repeat(40000), "a", "advanced"), false);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DialogFramePacket.STREAM_CODEC.encode(buffer, original);
            assertTrue(buffer.readableBytes() < 1048576);
            assertEquals(original, DialogFramePacket.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}

package top.yourzi.dialog.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.server.ServerDialogSessions;

import java.util.UUID;

/** No command text or target node is ever accepted from the client. */
public record DialogActionPacket(UUID sessionId, int revision, String entryId, int optionIndex)
        implements CustomPacketPayload {
    public static final Type<DialogActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_action"));
    public static final StreamCodec<FriendlyByteBuf, DialogActionPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public DialogActionPacket decode(FriendlyByteBuf buf) {
            return new DialogActionPacket(buf.readUUID(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
        }
        @Override public void encode(FriendlyByteBuf buf, DialogActionPacket packet) {
            buf.writeUUID(packet.sessionId());
            buf.writeVarInt(packet.revision());
            buf.writeUtf(packet.entryId(), 256);
            buf.writeVarInt(packet.optionIndex());
        }
    };
    public static void handleServer(DialogActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ServerDialogSessions.handle(player, packet);
        });
    }
    @Override public Type<DialogActionPacket> type() { return TYPE; }
}

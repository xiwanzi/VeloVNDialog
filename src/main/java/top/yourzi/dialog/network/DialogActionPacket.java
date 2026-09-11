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

/** The client only names an offered option; command text and variables never cross this boundary. */
public record DialogActionPacket(UUID sessionId, int revision, int action, String optionId) implements CustomPacketPayload {
    public static final Type<DialogActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_action"));
    public static final StreamCodec<FriendlyByteBuf, DialogActionPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public DialogActionPacket decode(FriendlyByteBuf buf) {
            return new DialogActionPacket(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
        }
        @Override public void encode(FriendlyByteBuf buf, DialogActionPacket packet) {
            buf.writeUUID(packet.sessionId()); buf.writeVarInt(packet.revision());
            buf.writeVarInt(packet.action()); buf.writeUtf(packet.optionId(), 256);
        }
    };
    public static void handleServer(DialogActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ServerDialogSessions.get().handle(player, packet);
        });
    }
    @Override public Type<DialogActionPacket> type() { return TYPE; }
}

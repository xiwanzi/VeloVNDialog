package top.yourzi.dialog.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

import java.util.UUID;

/** Acknowledges a transition; an empty entryId ends the session. */
public record DialogStatePacket(UUID sessionId, int revision, String entryId, int optionIndex)
        implements CustomPacketPayload {
    public static final Type<DialogStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_state"));
    public static final StreamCodec<FriendlyByteBuf, DialogStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public DialogStatePacket decode(FriendlyByteBuf buf) {
            return new DialogStatePacket(buf.readUUID(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
        }
        @Override public void encode(FriendlyByteBuf buf, DialogStatePacket packet) {
            buf.writeUUID(packet.sessionId());
            buf.writeVarInt(packet.revision());
            buf.writeUtf(packet.entryId(), 256);
            buf.writeVarInt(packet.optionIndex());
        }
    };
    public static void handleClient(DialogStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DialogManager.getInstance().receiveDialogState(packet));
    }
    @Override public Type<DialogStatePacket> type() { return TYPE; }
}

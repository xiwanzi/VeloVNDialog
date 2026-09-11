package top.yourzi.dialog.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.client.ClientDialogController;
import top.yourzi.dialog.core.DialogSession;

/** Only the current player's visible node is sent, never the script or server actions. */
public record DialogFramePacket(DialogSession.Frame frame, boolean opening) implements CustomPacketPayload {
    public static final Type<DialogFramePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_frame"));
    public static final StreamCodec<FriendlyByteBuf, DialogFramePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public DialogFramePacket decode(FriendlyByteBuf buf) {
            var frame = new DialogSession.Frame(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256),
                    buf.readUtf(DialogSession.MAX_FRAME_CHARS), buf.readUtf(256), buf.readUtf(64));
            return new DialogFramePacket(frame, buf.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buf, DialogFramePacket packet) {
            var frame = packet.frame();
            buf.writeUUID(frame.sessionId()); buf.writeVarInt(frame.revision()); buf.writeVarInt(frame.visit());
            buf.writeUtf(frame.dialogId(), 256); buf.writeUtf(frame.entryJson(), DialogSession.MAX_FRAME_CHARS);
            buf.writeUtf(frame.selectedOptionId(), 256); buf.writeUtf(frame.reason(), 64); buf.writeBoolean(packet.opening());
        }
    };
    public static void handleClient(DialogFramePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDialogController.getInstance().receive(packet));
    }
    @Override public Type<DialogFramePacket> type() { return TYPE; }
}

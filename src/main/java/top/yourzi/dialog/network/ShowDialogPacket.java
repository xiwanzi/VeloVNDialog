package top.yourzi.dialog.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import java.util.UUID;

public record ShowDialogPacket(UUID sessionId, String dialogId, String dialogJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowDialogPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "show_dialog_packet"));

    public static final StreamCodec<FriendlyByteBuf, ShowDialogPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public ShowDialogPacket decode(FriendlyByteBuf buf) {
            return new ShowDialogPacket(buf.readUUID(), buf.readUtf(), buf.readUtf());
        }
        @Override public void encode(FriendlyByteBuf buf, ShowDialogPacket packet) {
            buf.writeUUID(packet.sessionId());
            buf.writeUtf(packet.dialogId());
            buf.writeUtf(packet.dialogJson());
        }
    };



    public static void handleClient(final ShowDialogPacket message, final IPayloadContext context) {
        // 在客户端显示对话
        context.enqueueWork(() -> {
            if (message.dialogJson != null && !message.dialogJson.isEmpty()) {
                DialogManager.getInstance().receiveAndShowPlayerSpecificDialog(message.sessionId, message.dialogId, message.dialogJson);
            } else {
                top.yourzi.dialog.Dialog.LOGGER.warn("ShowDialogPacket received for id '{}' but dialogJson is empty. Client will not show dialog via this packet.", message.dialogId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

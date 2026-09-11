package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3").executesOn(HandlerThread.NETWORK);
        registrar.playToServer(DialogActionPacket.TYPE, DialogActionPacket.STREAM_CODEC, DialogActionPacket::handleServer);
        registrar.playToClient(DialogFramePacket.TYPE, DialogFramePacket.STREAM_CODEC, DialogFramePacket::handleClient);
    }

    public static void sendAction(DialogActionPacket action) {
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(action);
    }
}

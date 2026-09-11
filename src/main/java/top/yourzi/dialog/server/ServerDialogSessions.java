package top.yourzi.dialog.server;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.network.DialogActionPacket;
import top.yourzi.dialog.network.DialogStatePacket;
import top.yourzi.dialog.network.ShowDialogPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Accessed only on the logical server thread. Sessions are deliberately not persistent yet. */
public final class ServerDialogSessions {
    private static final Map<UUID, DialogSession> SESSIONS = new HashMap<>();

    private ServerDialogSessions() {}

    public static boolean open(ServerPlayer player, DialogSequence sequence) {
        final DialogSession session;
        try {
            session = new DialogSession(player.getUUID(), sequence);
        } catch (IllegalArgumentException e) {
            Dialog.LOGGER.warn("Cannot open dialog {}: {}", sequence.getId(), e.getMessage());
            return false;
        }
        SESSIONS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player,
                new ShowDialogPacket(session.id(), sequence.getId(), DialogManager.GSON.toJson(sequence)));
        return true;
    }

    public static void handle(ServerPlayer player, DialogActionPacket request) {
        var server = player.getServer();
        if (server == null || player.hasDisconnected()) return;
        DialogSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id().equals(request.sessionId())) return;
        DialogSession.Transition transition = session.transition(player.getUUID(), request.sessionId(),
                request.revision(), request.entryId(), request.optionIndex(), command -> isVisible(player, command));
        if (transition == null) {
            PacketDistributor.sendToPlayer(player,
                    new DialogStatePacket(session.id(), session.revision(), session.entryId(), DialogSession.ADVANCE));
            return;
        }
        var source = player.createCommandSourceStack().withPermission(Commands.LEVEL_GAMEMASTERS).withSuppressedOutput();
        for (String command : transition.commands()) {
            if (player.hasDisconnected()) break;
            try {
                server.getCommands().performPrefixedCommand(source, command);
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to execute configured dialog action", e);
            }
        }
        // Commands can open another dialog or disconnect the player. Do not overwrite that session.
        if (SESSIONS.get(player.getUUID()) != session) return;
        if (transition.entryId().isEmpty()) SESSIONS.remove(player.getUUID());
        if (!player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, new DialogStatePacket(session.id(), transition.revision(),
                    transition.entryId(), transition.optionIndex()));
        }
    }

    private static boolean isVisible(ServerPlayer player, String command) {
        try {
            var server = player.getServer();
            if (server == null) return false;
            var source = player.createCommandSourceStack()
                    .withPermission(server.getOperatorUserPermissionLevel()).withSuppressedOutput();
            return server.getCommands().getDispatcher().execute(command, source) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public static void remove(UUID playerId) { SESSIONS.remove(playerId); }
    public static void clear() { SESSIONS.clear(); }
}

package top.yourzi.dialog.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.core.DialogContext;
import top.yourzi.dialog.core.DialogDefinition;
import top.yourzi.dialog.core.DialogSession;
import top.yourzi.dialog.network.DialogActionPacket;
import top.yourzi.dialog.network.DialogFramePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;

public final class ServerDialogSessions {
    private static final ServerDialogSessions INSTANCE = new ServerDialogSessions();
    private final Map<UUID, DialogSession> sessions = new HashMap<>();
    private final DialogCatalog catalog = new DialogCatalog();
    private final Function<ServerPlayer, DialogContext> contexts;
    private final BiConsumer<ServerPlayer, DialogFramePacket> sender;
    private BukkitIntegrations integrations;

    public ServerDialogSessions() {
        contexts = player -> {
            if (integrations == null) integrations = new BukkitIntegrations(player.server.getClass().getClassLoader());
            return new ServerDialogContext(player, integrations);
        };
        sender = (player, frame) -> PacketDistributor.sendToPlayer(player, frame);
    }

    public ServerDialogSessions(Function<ServerPlayer, DialogContext> contexts, BiConsumer<ServerPlayer, DialogFramePacket> sender) {
        this.contexts = contexts;
        this.sender = sender;
    }

    public static ServerDialogSessions get() { return INSTANCE; }
    public DialogCatalog catalog() { return catalog; }

    public void initializeIntegrations(ClassLoader loader) {
        integrations = new BukkitIntegrations(loader);
        Dialog.LOGGER.info("VeloVNDialog integrations: Bukkit permissions={}, PlaceholderAPI={}",
                integrations.permissionsAvailable(), integrations.papiAvailable());
    }

    public boolean open(ServerPlayer player, String dialogId) {
        DialogDefinition definition = catalog.get(dialogId);
        return definition != null && open(player, definition);
    }

    public boolean open(ServerPlayer player, DialogDefinition definition) {
        try {
            DialogSession session = new DialogSession(player.getUUID(), definition, contexts.apply(player), RandomGenerator.getDefault());
            if (session.closed()) return false;
            sessions.put(player.getUUID(), session);
            sender.accept(player, new DialogFramePacket(session.frame(), true));
            return true;
        } catch (RuntimeException e) {
            Dialog.LOGGER.error("Cannot open dialog {}", definition.id(), e);
            return false;
        }
    }

    public void handle(ServerPlayer player, DialogActionPacket request) {
        if (player.hasDisconnected()) return;
        DialogSession session = sessions.get(player.getUUID());
        if (session == null || !session.id().equals(request.sessionId())) return;
        DialogSession.Frame frame;
        try {
            frame = session.act(player.getUUID(), request.sessionId(), request.revision(),
                    request.action(), request.optionId(), contexts.apply(player));
            if (frame == null) return; // Forged/stale actions neither run callbacks nor reroll text.
        } catch (RuntimeException e) {
            Dialog.LOGGER.error("Dialog action failed for {}", session.definition().id(), e);
            frame = session.abort();
        }
        // Configured commands may have opened another dialog or disconnected the player.
        if (sessions.get(player.getUUID()) != session) return;
        if (session.closed()) sessions.remove(player.getUUID());
        if (!player.hasDisconnected()) sender.accept(player, new DialogFramePacket(frame, false));
    }

    public void refresh(ServerPlayer player) {
        DialogSession session = sessions.get(player.getUUID());
        if (session == null) return;
        try {
            sender.accept(player, new DialogFramePacket(session.refresh(contexts.apply(player)), false));
        } catch (RuntimeException e) {
            Dialog.LOGGER.error("Dialog refresh failed", e);
            sender.accept(player, new DialogFramePacket(session.abort(), false));
        }
        if (session.closed()) sessions.remove(player.getUUID(), session);
    }

    public void remove(UUID playerId) { sessions.remove(playerId); }
    public void clear() { sessions.clear(); catalog.publish(Map.of()); integrations = null; }
}

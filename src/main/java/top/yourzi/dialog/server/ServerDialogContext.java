package top.yourzi.dialog.server;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import top.yourzi.dialog.core.DialogContext;
import top.yourzi.dialog.core.PlayerVariables;

public final class ServerDialogContext implements DialogContext {
    private final ServerPlayer player;
    private final BukkitIntegrations integrations;
    private final PlayerVariables variables;

    public ServerDialogContext(ServerPlayer player, BukkitIntegrations integrations) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Dialog context requires the server thread");
        this.player = player;
        this.integrations = integrations;
        variables = SavedDialogVariables.get(player.server).player(player.getUUID());
    }

    @Override public PlayerVariables variables() { return variables; }
    @Override public String playerName() { return player.getGameProfile().getName(); }
    @Override public boolean hasPermission(String permission) { return integrations.permission(player.getUUID(), permission); }
    @Override public boolean hasTag(String tag) { return player.getTags().contains(tag); }
    @Override public String placeholders(String text) { return integrations.expand(player.getUUID(), text); }
    @Override public boolean legacyCondition(String command) {
        var source = player.createCommandSourceStack().withPermission(player.server.getOperatorUserPermissionLevel())
                .withSuppressedOutput();
        try { return player.server.getCommands().getDispatcher().execute(command, source) == 1; }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { return false; }
    }
    @Override public void executeCommand(String command) {
        var source = player.createCommandSourceStack().withPermission(Commands.LEVEL_GAMEMASTERS).withSuppressedOutput();
        player.server.getCommands().performPrefixedCommand(source, command);
    }
}

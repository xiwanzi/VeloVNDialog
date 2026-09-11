package top.yourzi.dialog.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.server.SavedDialogVariables;
import top.yourzi.dialog.server.ServerDialogSessions;

@EventBusSubscriber(modid = Dialog.MODID)
public final class DialogCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dialog").requires(source -> source.hasPermission(2))
            .executes(ctx -> help(ctx.getSource()))
            .then(Commands.literal("show").then(Commands.argument("id", StringArgumentType.string())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ServerDialogSessions.get().catalog().snapshot().keySet(), builder))
                .executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player"))))))
            .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
            .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
            .then(Commands.literal("refresh")
                .executes(ctx -> refresh(ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> refresh(EntityArgument.getPlayer(ctx, "player")))))
            .then(Commands.literal("var").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("key", StringArgumentType.string())
                    .executes(ctx -> variable(ctx, false))
                    .then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> variable(ctx, true)))))));
    }

    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String id = StringArgumentType.getString(context, "id");
        if (ServerDialogSessions.get().open(player, id)) return 1;
        context.getSource().sendFailure(Component.translatable("dialog.error.unavailable", id));
        if (context.getSource().getEntity() != player) player.sendSystemMessage(Component.translatable("dialog.unavailable"));
        return 0;
    }

    private static int reload(CommandSourceStack source) {
        try {
            var catalog = ServerDialogSessions.get().catalog();
            var prepared = catalog.prepare(source.getServer().getResourceManager(), FMLPaths.CONFIGDIR.get().resolve("velovn/dialogs"));
            catalog.publish(prepared);
            source.sendSuccess(() -> Component.translatable("dialog.reload.success", prepared.size()), true);
            return prepared.size();
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("dialog.reload.failed", e.getMessage()));
            return 0;
        }
    }

    private static int list(CommandSourceStack source) {
        var definitions = ServerDialogSessions.get().catalog().snapshot();
        definitions.values().stream().sorted(java.util.Comparator.comparing(top.yourzi.dialog.core.DialogDefinition::id))
                .forEach(def -> source.sendSuccess(() -> Component.literal(def.id() + " — " + def.title()), false));
        return definitions.size();
    }

    private static int variable(CommandContext<CommandSourceStack> context, boolean write) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String key = StringArgumentType.getString(context, "key");
        var variables = SavedDialogVariables.get(context.getSource().getServer()).player(player.getUUID());
        try {
            if (write) {
                String input = StringArgumentType.getString(context, "value");
                JsonElement value;
                try { value = JsonParser.parseString(input); } catch (RuntimeException e) { value = new JsonPrimitive(input); }
                variables.set(key, value);
                ServerDialogSessions.get().refresh(player);
            }
            context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " / " + key + " = " + variables.get(key)), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int refresh(ServerPlayer player) { ServerDialogSessions.get().refresh(player); return 1; }
    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("/dialog show <id> [player] | list | reload | refresh [player] | var <player> <key> [value]"), false);
        return 1;
    }
}

package top.yourzi.dialog.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.server.ServerDialogSessions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Dialog.MODID)
public final class PlayerEventHandler {
    @SubscribeEvent public static void onStarted(ServerStartedEvent event) {
        ServerDialogSessions.get().initializeIntegrations(event.getServer().getClass().getClassLoader());
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ServerDialogSessions.get().remove(player.getUUID());
    }

    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { ServerDialogSessions.get().clear(); }

    @SubscribeEvent public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparationProfiler, ProfilerFiller applyProfiler, Executor backgroundExecutor, Executor gameExecutor) {
                var catalog = ServerDialogSessions.get().catalog();
                return CompletableFuture.supplyAsync(() -> {
                    try { return catalog.prepare(resources, FMLPaths.CONFIGDIR.get().resolve("velovn/dialogs")); }
                    catch (RuntimeException e) {
                        Dialog.LOGGER.error("Dialog reload rejected; keeping the previous catalog", e);
                        return null;
                    }
                }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync(prepared -> {
                    if (prepared != null) catalog.publish(prepared);
                }, gameExecutor);
            }
        });
    }
}

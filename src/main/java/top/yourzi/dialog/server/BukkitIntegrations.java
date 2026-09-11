package top.yourzi.dialog.server;

import top.yourzi.dialog.Dialog;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Optional Youer adapter. Bukkit/PAPI classes are never linked into the client or pure NeoForge path. */
public final class BukkitIntegrations {
    private final Method getPlayer, hasPermission, getPluginManager, getPlugin, isEnabled;
    private final Class<?> offlinePlayer;
    private final Set<String> warned = new HashSet<>();
    private Object papiPlugin;
    private Method expand;

    public BukkitIntegrations(ClassLoader loader) {
        Method player = null, permission = null, manager = null, plugin = null, enabled = null;
        Class<?> offline = null;
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, loader);
            Class<?> permissible = Class.forName("org.bukkit.permissions.Permissible", false, loader);
            Class<?> pluginManager = Class.forName("org.bukkit.plugin.PluginManager", false, loader);
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, loader);
            offline = Class.forName("org.bukkit.OfflinePlayer", false, loader);
            player = bukkit.getMethod("getPlayer", UUID.class);
            permission = permissible.getMethod("hasPermission", String.class);
            manager = bukkit.getMethod("getPluginManager");
            plugin = pluginManager.getMethod("getPlugin", String.class);
            enabled = pluginType.getMethod("isEnabled");
        } catch (ReflectiveOperationException | LinkageError e) {
            Dialog.LOGGER.info("Bukkit integration unavailable; permission conditions fail closed and PAPI text remains unchanged.");
        }
        getPlayer = player; hasPermission = permission; getPluginManager = manager; getPlugin = plugin; isEnabled = enabled; offlinePlayer = offline;
    }

    public boolean permission(UUID id, String permission) {
        if (hasPermission == null) throw new IllegalStateException("Bukkit permission provider unavailable");
        try {
            Object player = getPlayer.invoke(null, id);
            return player != null && (boolean) hasPermission.invoke(player, permission);
        } catch (ReflectiveOperationException | LinkageError e) {
            warn("permission", e);
            throw new IllegalStateException("Permission lookup failed", e);
        }
    }

    public String expand(UUID id, String text) {
        if (!text.contains("%")) return text;
        if (!papiAvailable()) { warn("PAPI unavailable", null); return text; }
        try {
            Object player = getPlayer.invoke(null, id);
            if (player == null) return text;
            Object value = expand.invoke(null, player, text);
            return value instanceof String result ? result : text;
        } catch (ReflectiveOperationException | LinkageError e) {
            warn("PAPI expansion failed", e);
            return text;
        }
    }

    public boolean permissionsAvailable() { return hasPermission != null; }

    public boolean papiAvailable() {
        if (getPluginManager == null) return false;
        try {
            Object plugin = getPlugin.invoke(getPluginManager.invoke(null), "PlaceholderAPI");
            if (plugin == null || !(boolean) isEnabled.invoke(plugin)) { papiPlugin = null; expand = null; return false; }
            if (plugin != papiPlugin) {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, plugin.getClass().getClassLoader());
                expand = api.getMethod("setPlaceholders", offlinePlayer, String.class);
                papiPlugin = plugin;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError e) { warn("PAPI lookup failed", e); return false; }
    }

    private void warn(String operation, Throwable error) {
        if (warned.add(operation)) Dialog.LOGGER.warn("Dialog integration: {}", operation, error);
    }
}

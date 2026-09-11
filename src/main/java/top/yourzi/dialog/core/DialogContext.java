package top.yourzi.dialog.core;

/** The interpreter has no dependency on Minecraft, Bukkit, or a network connection. */
public interface DialogContext {
    PlayerVariables variables();
    String playerName();
    boolean hasPermission(String permission);
    boolean hasTag(String tag);
    boolean legacyCondition(String command);
    String placeholders(String text);
    void executeCommand(String command);
}

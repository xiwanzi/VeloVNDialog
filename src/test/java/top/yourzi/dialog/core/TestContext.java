package top.yourzi.dialog.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class TestContext implements DialogContext {
    public final PlayerVariables state = new PlayerVariables();
    public final Set<String> permissions = new HashSet<>(), tags = new HashSet<>();
    public final List<String> commands = new ArrayList<>(), conditions = new ArrayList<>();
    public String name = "Player";
    public UnaryOperator<String> papi = text -> text;
    public Consumer<String> commandCallback = command -> {};
    @Override public PlayerVariables variables() { return state; }
    @Override public String playerName() { return name; }
    @Override public boolean hasPermission(String permission) { return permissions.contains(permission); }
    @Override public boolean hasTag(String tag) { return tags.contains(tag); }
    @Override public boolean legacyCondition(String command) { conditions.add(command); return tags.contains(command); }
    @Override public String placeholders(String text) { return papi.apply(text); }
    @Override public void executeCommand(String command) { commands.add(command); commandCallback.accept(command); }
}

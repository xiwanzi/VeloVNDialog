package top.yourzi.dialog.core;

import java.util.List;
import java.util.Map;

/** Immutable compiled graph. A session retains its definition across content reloads. */
public record DialogDefinition(String id, String title, String version, boolean allowClose,
                               Condition condition, List<Start> starts, Map<String, Node> nodes) {
    public DialogDefinition {
        starts = List.copyOf(starts);
        nodes = Map.copyOf(nodes);
    }

    public String start(DialogContext context) {
        if (!condition.test(context)) return null;
        return starts.stream().filter(start -> start.condition().test(context)).map(Start::target).findFirst().orElse(null);
    }

    public record Start(Condition condition, String target) {}
    public record Node(String id, String next, Condition condition, Actions actions, List<Option> options, String presentation) {
        public Node { options = List.copyOf(options); }
    }
    public record Option(String id, String target, Condition condition, Actions actions, String presentation) {}
    public record Actions(List<PlayerVariables.Mutation> mutations, List<String> commands) {
        public Actions { mutations = List.copyOf(mutations); commands = List.copyOf(commands); }
    }
}

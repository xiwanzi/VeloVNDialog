package top.yourzi.dialog.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Server-authoritative interpreter; randomness and presentation are frozen per visit. */
public final class DialogSession {
    public static final int ADVANCE = 0, CHOOSE = 1, CANCEL = 2;
    public static final int MAX_FRAME_CHARS = 262144;
    private final UUID owner;
    private final UUID id = UUID.randomUUID();
    private final DialogDefinition definition;
    private final RandomGenerator random;
    private final Map<String, JsonObject> chosenOptions = new HashMap<>();
    private Set<String> offered = Set.of();
    private DialogDefinition.Node current;
    private JsonObject chosen;
    private int revision, visit;
    private boolean pending;
    private Frame frame;

    public DialogSession(UUID owner, DialogDefinition definition, DialogContext context, RandomGenerator random) {
        this.owner = owner;
        this.definition = definition;
        this.random = random;
        enter(definition.start(context), context, "open", "");
    }

    public UUID id() { return id; }
    public Frame frame() { return frame; }
    public boolean closed() { return current == null; }
    public DialogDefinition definition() { return definition; }
    public Frame abort() { revision++; return close("error"); }

    public Frame act(UUID player, UUID session, int expectedRevision, int action, String optionId, DialogContext context) {
        if (!owner.equals(player) || !id.equals(session) || closed() || pending) return null;
        if (action == CANCEL) {
            if (!definition.allowClose()) return null;
            revision++;
            return close("cancelled");
        }
        if (expectedRevision != revision) return null;
        DialogDefinition.Option selected = null;
        if (action == CHOOSE) {
            if (!offered.contains(optionId)) return null;
            selected = current.options().stream().filter(option -> option.id().equals(optionId)).findFirst().orElse(null);
            if (selected == null) return null;
        } else if (action != ADVANCE || !current.options().isEmpty()) return null;
        if (!definition.condition().test(context)) { revision++; return close("unavailable"); }
        if (!current.condition().test(context) || (selected != null && !selected.condition().test(context))) return refresh(context);

        List<PlayerVariables.Mutation> mutations = new ArrayList<>(current.actions().mutations());
        List<String> commands = new ArrayList<>(current.actions().commands());
        if (selected != null) { mutations.addAll(selected.actions().mutations()); commands.addAll(selected.actions().commands()); }
        Map<String, JsonElement> prepared = mutations.isEmpty() ? null : context.variables().prepare(mutations);
        String next = selected == null ? current.next() : selected.target();
        String choice = selected == null ? "" : selected.id();
        revision++;
        pending = true; // Consume the request before callbacks, variable changes, or command execution.
        try {
            if (prepared != null) context.variables().commit(prepared);
            for (String command : commands) context.executeCommand(command);
            visit++;
            return enter(next, context, "advanced", choice);
        } catch (RuntimeException e) {
            close("error");
            throw e;
        } finally { pending = false; }
    }

    public Frame refresh(DialogContext context) {
        if (closed() || pending) return frame;
        revision++;
        if (!definition.condition().test(context)) return close("unavailable");
        if (!current.condition().test(context)) {
            visit++;
            return enter(current.next(), context, "condition_changed", "");
        }
        return render(context, "refreshed", "");
    }

    private Frame enter(String target, DialogContext context, String reason, String selected) {
        Set<String> skipped = new HashSet<>();
        while (target != null) {
            if (!skipped.add(target)) throw new IllegalStateException("Cycle contains no visible node in " + definition.id());
            current = definition.nodes().get(target);
            if (current == null) throw new IllegalStateException("Compiled target is missing: " + target);
            if (current.condition().test(context)) {
                chosen = materialize(current.presentation());
                chosenOptions.clear();
                return render(context, reason, selected);
            }
            target = current.next();
        }
        return close(reason.equals("open") ? "unavailable" : "complete", selected);
    }

    private JsonObject materialize(String presentation) {
        JsonObject json = JsonParser.parseString(presentation).getAsJsonObject();
        json.add("text", TextTemplate.choose(json.get("text"), random));
        if (json.has("speaker")) json.add("speaker", TextTemplate.choose(json.get("speaker"), random));
        return json;
    }

    private JsonObject resolve(JsonObject json, DialogContext context) {
        JsonObject result = json.deepCopy();
        result.add("text", TextTemplate.resolve(result.get("text"), context));
        if (result.has("speaker")) result.add("speaker", TextTemplate.resolve(result.get("speaker"), context));
        return result;
    }

    private Frame render(DialogContext context, String reason, String selected) {
        JsonObject view = resolve(chosen, context);
        view.addProperty("id", current.id());
        view.addProperty("allowClose", definition.allowClose());
        view.addProperty("requiresChoice", !current.options().isEmpty());
        JsonArray options = new JsonArray();
        Set<String> visible = new HashSet<>();
        for (var option : current.options()) {
            if (!option.condition().test(context)) continue;
            JsonObject prepared = chosenOptions.computeIfAbsent(option.id(), ignored -> materialize(option.presentation()));
            JsonObject projected = resolve(prepared, context);
            projected.addProperty("id", option.id());
            options.add(projected);
            visible.add(option.id());
        }
        view.add("options", options);
        String json = view.toString();
        if (json.length() > MAX_FRAME_CHARS) throw new IllegalArgumentException("Rendered node exceeds frame limit: " + current.id());
        offered = Set.copyOf(visible);
        return frame = new Frame(id, revision, visit, definition.id(), json, selected, reason);
    }

    private Frame close(String reason) { return close(reason, ""); }
    private Frame close(String reason, String selected) {
        current = null;
        offered = Set.of();
        chosen = null;
        chosenOptions.clear();
        return frame = new Frame(id, revision, visit, definition.id(), "", selected, reason);
    }

    public record Frame(UUID sessionId, int revision, int visit, String dialogId, String entryJson, String selectedOptionId, String reason) {}
}

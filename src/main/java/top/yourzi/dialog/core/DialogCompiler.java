package top.yourzi.dialog.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Decode and validate once, instead of cloning and filtering the graph for every opening. */
public final class DialogCompiler {
    private static final Set<String> CONTROL = Set.of("when", "visibility_command", "set", "add", "command", "commands", "next", "options", "end", "endDialog");

    private DialogCompiler() {}

    public static DialogDefinition compile(String json) {
        JsonObject root = object(JsonParser.parseString(json), "dialog");
        String id = id(root.get("id"), "dialog.id");
        if (!root.has("entries") || !root.get("entries").isJsonArray() || root.getAsJsonArray("entries").isEmpty()) throw error(id, "entries must be a non-empty array");
        var entries = root.getAsJsonArray("entries");
        var nodes = new LinkedHashMap<String, DialogDefinition.Node>();
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = object(entries.get(i), id + ".entries[" + i + "]");
            String nodeId = id(entry.get("id"), id + ".entries[" + i + "].id");
            try {
                String next = optional(entry.get("next")) != null ? id(entry.get("next"), "next")
                        : i + 1 < entries.size() ? id(object(entries.get(i + 1), "entry").get("id"), "next entry.id") : null;
                if (bool(entry, "end", bool(entry, "endDialog", false))) next = null;
                var options = new ArrayList<DialogDefinition.Option>();
                var optionIds = new java.util.HashSet<String>();
                if (entry.has("options")) {
                    if (!entry.get("options").isJsonArray()) throw new IllegalArgumentException("options must be an array");
                    for (JsonElement element : entry.getAsJsonArray("options")) {
                        JsonObject option = object(element, "option");
                        String optionId = option.has("id") ? id(option.get("id"), "option.id") : Integer.toString(options.size());
                        if (!optionIds.add(optionId)) throw new IllegalArgumentException("Duplicate option ID: " + optionId);
                        String target = bool(option, "end", bool(option, "endDialog", false)) ? null : id(option.get("target"), "option.target");
                        options.add(new DialogDefinition.Option(optionId, target,
                                condition(option), actions(option), presentation(option, false)));
                    }
                }
                var node = new DialogDefinition.Node(nodeId, next, condition(entry), actions(entry), options, presentation(entry, true));
                if (nodes.putIfAbsent(nodeId, node) != null) throw new IllegalArgumentException("Duplicate entry ID");
            } catch (RuntimeException e) { throw error(id + "." + nodeId, e.getMessage()); }
        }
        var starts = new ArrayList<DialogDefinition.Start>();
        JsonElement start = root.get("start");
        if (start == null || start.isJsonNull() || (start.isJsonPrimitive() && start.getAsString().isBlank())) {
            starts.add(new DialogDefinition.Start(Condition.ALWAYS, nodes.keySet().iterator().next()));
        } else if (start.isJsonArray()) {
            if (start.getAsJsonArray().isEmpty()) throw error(id, "start routes cannot be empty");
            for (JsonElement route : start.getAsJsonArray()) {
                JsonObject rule = object(route, "start route");
                starts.add(new DialogDefinition.Start(condition(rule), id(rule.get("target"), "start.target")));
            }
        } else starts.add(new DialogDefinition.Start(Condition.ALWAYS, id(start, "start")));
        for (var route : starts) requireTarget(id, route.target(), nodes);
        for (var node : nodes.values()) {
            if (node.next() != null) requireTarget(id + "." + node.id(), node.next(), nodes);
            for (var option : node.options()) if (option.target() != null) requireTarget(id + "." + node.id(), option.target(), nodes);
        }
        return new DialogDefinition(id, root.has("title") ? root.get("title").getAsString() : id,
                hash(root.toString()), bool(root, "allowClose", true), condition(root), starts, nodes);
    }

    private static DialogDefinition.Actions actions(JsonObject object) {
        var mutations = new ArrayList<PlayerVariables.Mutation>();
        for (String field : List.of("set", "add")) {
            if (!object.has(field)) continue;
            for (var variable : object(object.get(field), field).entrySet()) {
                PlayerVariables.validate(variable.getKey(), variable.getValue());
                boolean add = field.equals("add");
                if (add && (!variable.getValue().isJsonPrimitive() || !variable.getValue().getAsJsonPrimitive().isNumber())) throw new IllegalArgumentException("add requires a number");
                mutations.add(new PlayerVariables.Mutation(variable.getKey(), variable.getValue().deepCopy(), add));
            }
        }
        var commands = new ArrayList<String>();
        if (object.has("command") && object.has("commands")) throw new IllegalArgumentException("Use command or commands, not both");
        JsonElement value = object.has("commands") ? object.get("commands") : object.get("command");
        if (value != null && !value.isJsonNull()) {
            if (value.isJsonArray()) {
                for (JsonElement command : value.getAsJsonArray()) if (optional(command) != null) commands.add(string(command, "command"));
            } else if (optional(value) != null) commands.add(string(value, "command"));
        }
        return new DialogDefinition.Actions(mutations, commands);
    }

    private static Condition condition(JsonObject object) {
        return Condition.withLegacy(Condition.compile(object.get("when")), optional(object.get("visibility_command")));
    }

    private static String presentation(JsonObject source, boolean node) {
        JsonObject copy = source.deepCopy();
        for (String field : CONTROL) copy.remove(field);
        copy.remove("target");
        TextTemplate.validate(copy.get("text"));
        if (node) {
            TextTemplate.validate(copy.get("speaker")); bool(copy, "allowSkip", true);
            validateList(copy, "portraits", "path");
            validateList(copy, "display_items", "item");
            if (copy.has("background_image") && !copy.get("background_image").isJsonNull()) {
                string(object(copy.get("background_image"), "background_image").get("path"), "background_image.path");
            }
        }
        return copy.toString();
    }

    private static void validateList(JsonObject object, String field, String required) {
        if (!object.has(field) || object.get(field).isJsonNull()) return;
        if (!object.get(field).isJsonArray()) throw error(field, "Expected an array");
        for (JsonElement entry : object.getAsJsonArray(field)) string(object(entry, field).get(required), field + "." + required);
    }

    private static String optional(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected a string");
        return value.getAsString().isBlank() ? null : value.getAsString();
    }

    private static void requireTarget(String location, String target, java.util.Map<String, ?> nodes) {
        if (!nodes.containsKey(target)) throw error(location, "Unknown target: " + target);
    }

    private static String id(JsonElement value, String location) {
        String result = string(value, location);
        if (result.length() > 256) throw error(location, "ID exceeds 256 characters");
        return result;
    }

    private static String string(JsonElement value, String location) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) throw error(location, "Expected a non-empty string");
        return value.getAsString();
    }

    private static JsonObject object(JsonElement value, String location) {
        if (value == null || !value.isJsonObject()) throw error(location, "Expected an object");
        return value.getAsJsonObject();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw error(key, "Expected a boolean");
        return value.getAsBoolean();
    }

    private static IllegalArgumentException error(String location, String detail) {
        return new IllegalArgumentException(location + ": " + detail);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

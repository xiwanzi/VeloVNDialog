package top.yourzi.dialog.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@FunctionalInterface
public interface Condition {
    Condition ALWAYS = context -> true;
    boolean test(DialogContext context);

    static Condition compile(JsonElement json) { return compile(json, 0); }

    private static Condition compile(JsonElement json, int depth) {
        if (json == null || json.isJsonNull()) return ALWAYS;
        if (depth > 32 || !json.isJsonObject()) throw new IllegalArgumentException("when must be an object (maximum nesting: 32)");
        List<Condition> terms = new ArrayList<>();
        for (var field : json.getAsJsonObject().entrySet()) {
            JsonElement value = field.getValue();
            switch (field.getKey()) {
                case "permission" -> {
                    List<String> nodes = strings(value, "permission");
                    terms.add(context -> nodes.stream().allMatch(context::hasPermission));
                }
                case "tag" -> {
                    List<String> tags = strings(value, "tag");
                    terms.add(context -> tags.stream().allMatch(context::hasTag));
                }
                case "var" -> {
                    if (!value.isJsonObject()) throw new IllegalArgumentException("when.var must be an object");
                    for (var variable : value.getAsJsonObject().entrySet()) {
                        String key = variable.getKey();
                        JsonElement expected = variable.getValue().deepCopy();
                        PlayerVariables.validate(key, com.google.gson.JsonNull.INSTANCE);
                        validateComparison(expected);
                        terms.add(context -> matches(context.variables().get(key), expected));
                    }
                }
                case "all", "any" -> {
                    if (!value.isJsonArray() || value.getAsJsonArray().isEmpty()) throw new IllegalArgumentException(field.getKey() + " must be a non-empty array");
                    List<Condition> children = new ArrayList<>();
                    for (JsonElement child : value.getAsJsonArray()) {
                        if (!child.isJsonObject()) throw new IllegalArgumentException("all/any children must be condition objects");
                        children.add(compile(child, depth + 1));
                    }
                    boolean all = field.getKey().equals("all");
                    terms.add(context -> all ? children.stream().allMatch(c -> c.test(context)) : children.stream().anyMatch(c -> c.test(context)));
                }
                case "not" -> {
                    if (!value.isJsonObject()) throw new IllegalArgumentException("not requires a condition object");
                    Condition child = compile(value, depth + 1);
                    terms.add(context -> !child.test(context));
                }
                default -> throw new IllegalArgumentException("Unknown when key: " + field.getKey());
            }
        }
        return context -> terms.stream().allMatch(term -> term.test(context));
    }

    static Condition withLegacy(Condition condition, String command) {
        return command == null || command.isBlank() ? condition : context -> condition.test(context) && context.legacyCondition(command);
    }

    private static List<String> strings(JsonElement value, String name) {
        var result = new ArrayList<String>();
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) result.add(string(item, name));
        } else result.add(string(value, name));
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return List.copyOf(result);
    }

    private static String string(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) throw new IllegalArgumentException(name + " requires a non-empty string");
        return value.getAsString();
    }

    private static void validateComparison(JsonElement value) {
        if (value.isJsonNull() || value.isJsonPrimitive()) return;
        if (!value.isJsonObject() || value.getAsJsonObject().isEmpty()) throw new IllegalArgumentException("Variable comparison must be a scalar or comparator object");
        for (var op : value.getAsJsonObject().entrySet()) {
            if (!Set.of("eq", "ne", "gt", "gte", "lt", "lte", "exists").contains(op.getKey())) throw new IllegalArgumentException("Unknown comparison: " + op.getKey());
            JsonElement operand = op.getValue();
            if (!operand.isJsonNull() && !operand.isJsonPrimitive()) throw new IllegalArgumentException("Comparison operand must be scalar");
            if (op.getKey().equals("exists") && (!operand.isJsonPrimitive() || !operand.getAsJsonPrimitive().isBoolean())) throw new IllegalArgumentException("exists requires a boolean");
            if (Set.of("gt", "gte", "lt", "lte").contains(op.getKey()) && !number(operand)) throw new IllegalArgumentException(op.getKey() + " requires a number");
        }
    }

    private static boolean matches(JsonElement actual, JsonElement expected) {
        if (!expected.isJsonObject()) return equal(actual, expected);
        for (var op : expected.getAsJsonObject().entrySet()) {
            JsonElement operand = op.getValue();
            boolean matches = switch (op.getKey()) {
                case "eq" -> equal(actual, operand);
                case "ne" -> !equal(actual, operand);
                case "exists" -> !actual.isJsonNull() == operand.getAsBoolean();
                case "gt" -> number(actual) && actual.getAsBigDecimal().compareTo(operand.getAsBigDecimal()) > 0;
                case "gte" -> number(actual) && actual.getAsBigDecimal().compareTo(operand.getAsBigDecimal()) >= 0;
                case "lt" -> number(actual) && actual.getAsBigDecimal().compareTo(operand.getAsBigDecimal()) < 0;
                case "lte" -> number(actual) && actual.getAsBigDecimal().compareTo(operand.getAsBigDecimal()) <= 0;
                default -> false;
            };
            if (!matches) return false;
        }
        return true;
    }

    private static boolean equal(JsonElement a, JsonElement b) {
        return number(a) && number(b) ? a.getAsBigDecimal().compareTo(b.getAsBigDecimal()) == 0 : a.equals(b);
    }

    private static boolean number(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }
}

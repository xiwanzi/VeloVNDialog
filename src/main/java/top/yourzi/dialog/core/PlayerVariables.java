package top.yourzi.dialog.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scalar story state, independent of entity tags and entity lifetimes. */
public final class PlayerVariables {
    private Map<String, JsonElement> values;
    private final Runnable dirty;

    public PlayerVariables() { this(Map.of(), () -> {}); }

    public PlayerVariables(Map<String, JsonElement> initial, Runnable dirty) {
        values = new LinkedHashMap<>();
        initial.forEach((key, value) -> { validate(key, value); if (!value.isJsonNull()) values.put(key, value); });
        this.dirty = dirty;
    }

    public JsonElement get(String key) { return values.getOrDefault(key, JsonNull.INSTANCE); }
    public Map<String, JsonElement> snapshot() { return Map.copyOf(values); }

    public Map<String, JsonElement> prepare(List<Mutation> mutations) {
        var next = new LinkedHashMap<>(values);
        for (Mutation mutation : mutations) {
            validate(mutation.key(), mutation.value());
            JsonElement value = mutation.value();
            if (mutation.add()) {
                JsonElement old = next.getOrDefault(mutation.key(), new JsonPrimitive(0));
                if (!isNumber(old) || !isNumber(value)) throw new IllegalArgumentException("add requires numeric variable: " + mutation.key());
                value = new JsonPrimitive(old.getAsBigDecimal().add(value.getAsBigDecimal()));
            }
            if (value.isJsonNull()) next.remove(mutation.key()); else next.put(mutation.key(), value);
        }
        return next;
    }

    public void commit(Map<String, JsonElement> prepared) {
        prepared.forEach(PlayerVariables::validate);
        if (!values.equals(prepared)) {
            values = new LinkedHashMap<>(prepared);
            dirty.run();
        }
    }

    public void set(String key, JsonElement value) {
        commit(prepare(List.of(new Mutation(key, value, false))));
    }

    public static void validate(String key, JsonElement value) {
        if (key == null || !key.matches("[\\p{L}\\p{N}_.:-]{1,128}")) throw new IllegalArgumentException("Invalid variable name: " + key);
        if (value == null || (!value.isJsonPrimitive() && !value.isJsonNull())) throw new IllegalArgumentException("Variable must be a string, number, boolean, or null: " + key);
        if (value.toString().length() > 16384) throw new IllegalArgumentException("Variable value exceeds 16384 characters: " + key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) new BigDecimal(value.getAsString());
    }

    private static boolean isNumber(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    public record Mutation(String key, JsonElement value, boolean add) {}
}

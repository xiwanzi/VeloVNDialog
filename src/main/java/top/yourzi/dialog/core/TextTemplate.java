package top.yourzi.dialog.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit random pools preserve legacy text-component arrays as concatenation. */
public final class TextTemplate {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([\\p{L}\\p{N}_.:-]{1,128})}");
    private static final Set<String> TEXT_FIELDS = Set.of("text", "extra", "with", "fallback");

    private TextTemplate() {}

    public static void validate(JsonElement text) { validate(text, 0); }

    private static void validate(JsonElement text, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Text nesting exceeds 32");
        if (text == null || text.isJsonNull()) return;
        if (text.isJsonPrimitive()) {
            if (!text.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Text must be a string or component");
        } else if (text.isJsonArray()) {
            for (JsonElement part : text.getAsJsonArray()) validate(part, depth + 1);
        } else {
            JsonObject object = text.getAsJsonObject();
            if (object.has("random")) {
                JsonElement random = object.get("random");
                if (object.size() != 1 || !random.isJsonArray() || random.getAsJsonArray().isEmpty()) throw new IllegalArgumentException("text.random must be a non-empty array in its own object");
                for (JsonElement choice : random.getAsJsonArray()) validate(choice, depth + 1);
            } else {
                for (String field : TEXT_FIELDS) {
                    if (!object.has(field)) continue;
                    if (field.equals("with") && object.get(field).isJsonArray()) {
                        for (JsonElement argument : object.getAsJsonArray(field)) {
                            if (!argument.isJsonPrimitive()) validate(argument, depth + 1);
                        }
                    } else validate(object.get(field), depth + 1);
                }
            }
        }
    }

    public static JsonElement choose(JsonElement template, RandomGenerator random) {
        if (template == null || template.isJsonNull()) return new JsonPrimitive("");
        if (template.isJsonObject() && template.getAsJsonObject().has("random")) {
            JsonArray choices = template.getAsJsonObject().getAsJsonArray("random");
            return choose(choices.get(random.nextInt(choices.size())), random);
        }
        return transform(template, value -> value, random);
    }

    public static JsonElement resolve(JsonElement chosen, DialogContext context) {
        return transform(chosen, text -> {
            Matcher matcher = VARIABLE.matcher(text.replace("@i", context.playerName()));
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                JsonElement value = context.variables().get(matcher.group(1));
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(value.isJsonNull() ? matcher.group() : value.getAsString()));
            }
            matcher.appendTail(buffer);
            return context.placeholders(buffer.toString());
        }, null);
    }

    private static JsonElement transform(JsonElement value, UnaryOperator<String> strings, RandomGenerator random) {
        if (value.isJsonPrimitive()) return value.getAsJsonPrimitive().isString() ? new JsonPrimitive(strings.apply(value.getAsString())) : value;
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) result.add(random == null ? transform(child, strings, null) : choose(child, random));
            return result;
        }
        if (value.isJsonNull()) return value;
        JsonObject result = value.getAsJsonObject().deepCopy();
        for (String field : TEXT_FIELDS) {
            if (result.has(field)) result.add(field, random == null ? transform(result.get(field), strings, null) : choose(result.get(field), random));
        }
        return result;
    }
}

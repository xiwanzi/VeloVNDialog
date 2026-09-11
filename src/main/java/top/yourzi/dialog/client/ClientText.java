package top.yourzi.dialog.client;

import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.Dialog;

/** Converts an already resolved view into Minecraft text, once per received node. */
public final class ClientText {
    private ClientText() {}
    public static Component read(JsonElement json, HolderLookup.Provider registries) {
        if (json == null || json.isJsonNull()) return Component.empty();
        if (json.isJsonPrimitive()) return Component.literal(json.getAsString());
        if (json.isJsonArray()) {
            var combined = Component.empty();
            for (JsonElement part : json.getAsJsonArray()) combined.append(read(part, registries));
            return combined;
        }
        try {
            Component component = Component.Serializer.fromJson(json, registries);
            return component == null ? Component.empty() : component;
        } catch (RuntimeException e) {
            Dialog.LOGGER.warn("Invalid dialog display component", e);
            return Component.literal(json.isJsonPrimitive() ? json.getAsString() : "");
        }
    }
}

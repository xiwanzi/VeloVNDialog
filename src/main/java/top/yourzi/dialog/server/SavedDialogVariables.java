package top.yourzi.dialog.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.core.PlayerVariables;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-save persistence. Cross-server replication is intentionally a separate storage concern. */
public final class SavedDialogVariables extends SavedData {
    private static final Factory<SavedDialogVariables> FACTORY = new Factory<>(SavedDialogVariables::new, SavedDialogVariables::load);
    private final Map<UUID, PlayerVariables> players = new HashMap<>();

    public static SavedDialogVariables get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "velovn_variables");
    }

    public PlayerVariables player(UUID id) {
        return players.computeIfAbsent(id, ignored -> new PlayerVariables(Map.of(), this::setDirty));
    }

    public static SavedDialogVariables load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new SavedDialogVariables();
        CompoundTag entries = root.getCompound("players");
        for (String id : entries.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(id);
                CompoundTag vars = entries.getCompound(id);
                Map<String, JsonElement> values = new HashMap<>();
                for (String key : vars.getAllKeys()) {
                    try {
                        JsonElement value = JsonParser.parseString(vars.getString(key));
                        PlayerVariables.validate(key, value);
                        values.put(key, value);
                    } catch (RuntimeException e) { Dialog.LOGGER.error("Invalid saved dialog variable {} for {}", key, id, e); }
                }
                data.players.put(playerId, new PlayerVariables(values, data::setDirty));
            } catch (IllegalArgumentException e) { Dialog.LOGGER.error("Invalid dialog player UUID in save: {}", id); }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        players.forEach((id, state) -> {
            if (state.snapshot().isEmpty()) return;
            CompoundTag vars = new CompoundTag();
            state.snapshot().forEach((key, value) -> vars.putString(key, value.toString()));
            entries.put(id.toString(), vars);
        });
        tag.putInt("schema", 1);
        tag.put("players", entries);
        return tag;
    }
}

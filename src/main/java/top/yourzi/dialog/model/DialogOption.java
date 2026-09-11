package top.yourzi.dialog.model;

import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.client.ClientText;

public final class DialogOption {
    private String id;
    private JsonElement text;
    private transient Component cachedText;
    public String getId() { return id; }
    public Component getText(HolderLookup.Provider registries) {
        if (cachedText == null) cachedText = ClientText.read(text, registries);
        return cachedText;
    }
}

package top.yourzi.dialog.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.client.ClientText;

import java.util.List;

/** Client presentation only: no conditions, commands, or story variables. */
public final class DialogEntry {
    private String id;
    private JsonElement text, speaker;
    private List<PortraitInfo> portraits;
    private DialogOption[] options;
    @SerializedName("display_items") private List<DisplayItemInfo> displayItems;
    @SerializedName("background_image") private BackgroundImageInfo backgroundImage;
    private boolean requiresChoice;
    private boolean allowSkip = true;
    private boolean allowClose = true;
    private transient String selectedOptionText;
    private transient Component cachedText, cachedSpeaker;

    public String getId() { return id; }
    public List<PortraitInfo> getPortraits() { return portraits; }
    public List<DisplayItemInfo> getDisplayItems() { return displayItems; }
    public BackgroundImageInfo getBackgroundImage() { return backgroundImage; }
    public DialogOption[] getOptions() { return options == null ? new DialogOption[0] : options; }
    public boolean hasOptions() { return requiresChoice || getOptions().length != 0; }
    public boolean isSkipAllowed() { return allowSkip; }
    public boolean isCloseAllowed() { return allowClose; }
    public String getSelectedOptionText() { return selectedOptionText; }
    public void setSelectedOptionText(String text) { selectedOptionText = text; }
    public Component getText(HolderLookup.Provider registries) {
        if (cachedText == null) cachedText = ClientText.read(text, registries);
        return cachedText;
    }
    public Component getSpeaker(HolderLookup.Provider registries) {
        if (cachedSpeaker == null) cachedSpeaker = ClientText.read(speaker, registries);
        return cachedSpeaker;
    }
}

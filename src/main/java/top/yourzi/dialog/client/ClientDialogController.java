package top.yourzi.dialog.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.core.DialogSession;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.network.DialogActionPacket;
import top.yourzi.dialog.network.DialogFramePacket;
import top.yourzi.dialog.network.NetworkHandler;
import top.yourzi.dialog.ui.DialogScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientDialogController {
    private static final ClientDialogController INSTANCE = new ClientDialogController();
    private static final Gson GSON = new Gson();
    private static boolean autoPlaying, fastForwarding;
    private final List<DialogEntry> history = new ArrayList<>();
    private UUID sessionId;
    private int revision, visit;
    private DialogEntry current;
    private boolean pending;

    public static ClientDialogController getInstance() { return INSTANCE; }
    public static boolean isAutoPlaying() { return autoPlaying; }
    public static void setAutoPlaying(boolean value) { autoPlaying = value; }
    public static void stopAutoPlay() { autoPlaying = false; }
    public static boolean isFastForwardingNext() { return fastForwarding; }
    public static void setFastForwardingNext(boolean value) { fastForwarding = value; }
    public boolean isActionPending() { return pending; }
    public List<DialogEntry> getDialogHistory() { return List.copyOf(history); }

    public void clearAllDialogsOnClient() {
        sessionId = null; current = null; pending = false;
        history.clear(); autoPlaying = false; fastForwarding = false;
    }

    public void receive(DialogFramePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var frame = packet.frame();
        if (packet.opening()) {
            clearAllDialogsOnClient();
            sessionId = frame.sessionId();
            visit = -1; revision = -1;
        } else if (!frame.sessionId().equals(sessionId)) return;
        if (frame.revision() <= revision) return;
        pending = false;
        if (current != null && !frame.selectedOptionId().isEmpty()) {
            for (var option : current.getOptions()) {
                if (option.getId().equals(frame.selectedOptionId())) {
                    current.setSelectedOptionText(option.getText(minecraft.level.registryAccess()).getString());
                    break;
                }
            }
        }
        revision = frame.revision();
        if (frame.entryJson().isEmpty()) {
            sessionId = null; current = null; autoPlaying = false; fastForwarding = false;
            if (minecraft.screen instanceof DialogScreen) minecraft.setScreen(null);
            if (frame.reason().equals("error") && minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatable("dialog.error.runtime"));
            if (frame.reason().equals("unavailable") && minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatable("dialog.unavailable"));
            return;
        }
        current = GSON.fromJson(frame.entryJson(), DialogEntry.class);
        if (visit != frame.visit() || history.isEmpty()) {
            history.add(current);
            if (history.size() > 2000) history.removeFirst();
        } else history.set(history.size() - 1, current);
        visit = frame.visit();
        minecraft.setScreen(new DialogScreen(current));
    }

    public void showNextDialog() { request(DialogSession.ADVANCE, ""); }
    public void chooseOption(String optionId) { request(DialogSession.CHOOSE, optionId); }

    private void request(int action, String optionId) {
        if (sessionId == null || current == null || pending || Minecraft.getInstance().getConnection() == null) return;
        pending = true;
        NetworkHandler.sendAction(new DialogActionPacket(sessionId, revision, action, optionId));
    }

    public void cancelDialog() {
        if (current != null && !current.isCloseAllowed()) return;
        if (sessionId != null && Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.sendAction(new DialogActionPacket(sessionId, revision, DialogSession.CANCEL, ""));
        }
        sessionId = null; current = null; pending = false; autoPlaying = false; fastForwarding = false;
    }
}

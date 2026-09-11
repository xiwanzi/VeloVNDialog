package top.yourzi.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.ui.DialogScreen;
import top.yourzi.dialog.network.*; // Import all packet classes
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.UUID;
import top.yourzi.dialog.server.DialogSession;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class DialogManager {
    public static final Gson GSON = new GsonBuilder().create();
    private static final DialogManager INSTANCE = new DialogManager();

    // Keep server content separate from client caches, including in an integrated server JVM.
    private final Map<String, DialogSequence> dialogSequences = new HashMap<>();
    private final Map<String, DialogSequence> clientDialogSequences = new HashMap<>();
    private UUID currentSessionId;
    private int currentRevision;
    private boolean actionPending;
    // 当前显示的对话序列
    private DialogSequence currentSequence;
    // 当前显示的对话条目
    private DialogEntry currentEntry;
    // 对话历史记录
    private final List<DialogEntry> dialogHistory = new ArrayList<>();
    // 标记下一次对话推进是否由快速跳过触发
    private static boolean isFastForwardingNext = false;
    // 自动播放状态
    private static boolean isAutoPlaying = false;
    // 存储当前对话的玩家名称
    private String currentDialogPlayerName;

    private DialogManager() {}
    
    /**
     * 向玩家发送消息。
     */
    @OnlyIn(Dist.CLIENT)
    private void sendPlayerMessage(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }
    
    public static DialogManager getInstance() {
        return INSTANCE;
    }

    /**
     * 加载所有对话序列 (仅服务端调用)。
     * 此方法从数据包 (data/<modid>/dialogs/) 加载对话。
     * @param resourceManager 资源管理器实例。
     */
    public void loadDialogsFromServer(ResourceManager resourceManager) {
        dialogSequences.clear();

        Map<ResourceLocation, Resource> modSpecificResources = resourceManager.listResources("dialogs", resource -> resource.getPath().endsWith(".json")).entrySet().stream()
            .filter(entry -> entry.getKey().getNamespace().equals(Dialog.MODID))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        modSpecificResources.forEach((resourceLocation, resource) -> {
            try {
                DialogSequence sequence = parseDialogSequenceFromFile(resource); // 解析对话序列
                if (sequence != null && sequence.getId() != null) {
                    dialogSequences.put(sequence.getId(), sequence);
                } else {
                    Dialog.LOGGER.warn("Empty dialog sequence or empty ID. {}", resourceLocation);
                }
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to load dialog file {}: {}", resourceLocation, e.getMessage(), e);
            }
        });

        if (dialogSequences.isEmpty()) {
            Dialog.LOGGER.warn("No dialog sequence was found, please check the 'dialogs' directory ('data/{}/dialogs') or file format in the datapack.", Dialog.MODID);
        }
    }

    /**
     * 解析对话序列JSON文件 (内部使用, 服务端加载时调用)。
     * @param resource 资源文件。
     */
    private DialogSequence parseDialogSequenceFromFile(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            return GSON.fromJson(reader, DialogSequence.class);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            Dialog.LOGGER.error("Failure to read or parse dialog JSON file ({}): {}", resource.sourcePackId(), e.getMessage());
            // 尝试读取内容以进行更详细的调试
            try (BufferedReader contentReader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                StringBuilder jsonContent = new StringBuilder();
                String line;
                while ((line = contentReader.readLine()) != null) {
                    jsonContent.append(line);
                }
                Dialog.LOGGER.debug("JSON: {}", jsonContent.toString());
            } catch (IOException ioe) {
                Dialog.LOGGER.error("Unable to read problematic JSON content for debugging. {}", ioe.getMessage());
            }
            return null;
        }
    }

    /**
     * (客户端) 清空所有已缓存的对话数据。
     * 通常在从服务器断开或服务器重载数据包时调用。
     */
    @OnlyIn(Dist.CLIENT)
    public void clearAllDialogsOnClient() {
        clientDialogSequences.clear();
        clearClientSession();
        clearDialogHistory();
    }

    @OnlyIn(Dist.CLIENT)
    private void clearClientSession() {
        currentSessionId = null;
        currentSequence = null;
        currentEntry = null;
        actionPending = false;
        isFastForwardingNext = false;
        stopAutoPlay();
    }

    /**
     * (客户端) 接收并缓存从服务器同步过来的所有对话数据。
     * @param dialogDataMap 一个映射，键是对话ID，值是对话内容的JSON字符串。
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveAllDialogsFromServer(Map<String, String> dialogDataMap) {
        clientDialogSequences.clear(); // Content updates must not invalidate the active session.
        dialogDataMap.forEach((id, json) -> {
            try {
                DialogSequence sequence = GSON.fromJson(json, DialogSequence.class);
                if (sequence != null && sequence.getId() != null) {
                    if (!id.equals(sequence.getId())) {
                        Dialog.LOGGER.warn("Dialog ID mismatch! Expected ID: {}, ID in JSON: {}. Will use expected ID.", id, sequence.getId());
                    }
                    clientDialogSequences.put(id, sequence); // 使用map的key作为权威ID
                    Dialog.LOGGER.debug("Client Successfully Cached Conversation. {}", id);
                } else {
                    Dialog.LOGGER.warn("Parsing of the dialog data received from the server failed or the ID is null. ID: {}, JSON: {}", id, json);
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                Dialog.LOGGER.error("Failed to parse the dialog JSON received from the server. ID: {}, 错误: {}", id, e.getMessage());
                Dialog.LOGGER.debug("(ID: {}): {}", id, json, e);
            }
        });
        if (clientDialogSequences.isEmpty() && !dialogDataMap.isEmpty()) {
            Dialog.LOGGER.warn("Dialog data has been received but the cache is empty after parsing, please check the JSON format and content.");
        }
    }

    /**
     * 将对话条目添加到历史记录。
     */
    @OnlyIn(Dist.CLIENT)
    private void addDialogToHistory(DialogEntry entry) {
        if (entry != null) {
            dialogHistory.add(entry);
        }
    }

    /**
     * 获取对话历史记录。
     */
    @OnlyIn(Dist.CLIENT)
    public List<DialogEntry> getDialogHistory() {
        if (Minecraft.getInstance().level == null || !Minecraft.getInstance().level.isClientSide) return Collections.emptyList();
        return new ArrayList<>(dialogHistory);
    }

    /**
     * 清空对话历史记录。
     */
    @OnlyIn(Dist.CLIENT)
    private void clearDialogHistory() {
        dialogHistory.clear();
    }

    /**
     * 记录玩家在当前对话中选择的选项。
     * @param optionText 所选选项的文本。
     */
    @OnlyIn(Dist.CLIENT)
    public void recordChoiceForCurrentDialog(String optionText) {
        if (Minecraft.getInstance().level == null || !Minecraft.getInstance().level.isClientSide) return;
        if (currentEntry != null) {
            currentEntry.setSelectedOptionText(optionText);
            // 更新历史记录中最新的对应条目
            if (!dialogHistory.isEmpty()) {
                DialogEntry lastHistoryEntry = dialogHistory.get(dialogHistory.size() - 1);
                // 确保更新的是同一个对话条目（理论上应该是同一个）
                if (lastHistoryEntry == currentEntry) {
                    lastHistoryEntry.setSelectedOptionText(optionText);
                } else {
                    // 如果不是同一个条目，可能存在逻辑错误，或者 currentEntry 在添加到历史记录后被更改。
                    // 尝试通过ID查找并更新。
                    for (int i = dialogHistory.size() - 1; i >= 0; i--) {
                        if (dialogHistory.get(i).getId() != null && dialogHistory.get(i).getId().equals(currentEntry.getId())) {
                            dialogHistory.get(i).setSelectedOptionText(optionText);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * (服务端) 获取所有对话序列的JSON表示，用于发送给客户端。
     */
    public Map<String, String> getAllDialogJsonsForSync() {
        Map<String, String> dialogJsons = new HashMap<>();
        dialogSequences.forEach((id, sequence) -> {
            dialogJsons.put(id, GSON.toJson(sequence));
        });
        return dialogJsons;
    }

    /**
     * 根据ID获取对话序列。
     * 服务端：从加载的对话中获取。
     * 客户端：从缓存的对话中获取。
     */
    public DialogSequence getDialogSequence(String id) {
        DialogSequence original = dialogSequences.get(id);
        if (original != null) {
            return GSON.fromJson(GSON.toJson(original), DialogSequence.class);
        }
        return null;
    }
    
    /**
     * 获取所有对话序列。
     */
    public Map<String, DialogSequence> getAllDialogSequences() {
        return new HashMap<>(dialogSequences);
    }

    /**
     * (服务端) 为特定玩家创建一个对话序列的副本，并根据玩家权限和visibility_command过滤选项。
     * @param originalSequence 原始对话序列。
     * @param player 执行命令的玩家。
     * @param server Minecraft服务器实例。
     * @return 经过选项过滤的对话序列副本；如果原始序列为null，则返回null。
     */
    public DialogSequence createPlayerSpecificSequence(DialogSequence originalSequence, ServerPlayer player, MinecraftServer server) {
        if (originalSequence == null) {
            Dialog.LOGGER.warn("Attempted to create player-specific sequence from null originalSequence.");
            return null;
        }


        DialogSequence playerSpecificSequence = GSON.fromJson(GSON.toJson(originalSequence), DialogSequence.class);

        if (playerSpecificSequence == null) {
            Dialog.LOGGER.error("Failed to deep copy originalSequence for ID: {}. No player-specific sequence will be generated.", originalSequence.getId());
            return null;
        }
        
        if (playerSpecificSequence.getEntries() == null) {
            return playerSpecificSequence;
        }

        List<DialogEntry> visibleEntries = new ArrayList<>();
        CommandSourceStack commandSource = player.createCommandSourceStack()
            .withPermission(server.getOperatorUserPermissionLevel())
            .withSuppressedOutput();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        for (DialogEntry entry : playerSpecificSequence.getEntries()) {
            if (entry == null) {
                continue;
            }

            // 检查条目本身的可见性命令
            String entryVisibilityCommand = entry.getVisibilityCommand();
            if (entryVisibilityCommand != null && !entryVisibilityCommand.isEmpty()) {
                try {
                    int result = dispatcher.execute(dispatcher.parse(entryVisibilityCommand, commandSource));
                    if (result != 1) {
                        Dialog.LOGGER.debug("Visibility command '{}' for entry '{}' (dialog '{}') for player {} returned {}, entry hidden.",
                                           entryVisibilityCommand, entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), result);
                        continue; // 跳过此条目，不添加到 visibleEntries
                    }
                } catch (CommandSyntaxException e) {
                    Dialog.LOGGER.warn("Syntax error in visibility command '{}' for entry '{}' (dialog '{}') for player {}: {}. Entry hidden.",
                                       entryVisibilityCommand, entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                    continue; // 命令语法错误则隐藏条目
                } catch (Exception e) {
                    Dialog.LOGGER.warn("Error executing visibility command '{}' for entry '{}' (dialog '{}') for player {}: {}. Entry hidden.",
                                       entryVisibilityCommand, entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                    continue; // 其他执行错误则隐藏条目
                }
            }
/*
            //解析条目文本和说话者中的选择器
            // commandSource 和 player 来自方法参数，在此作用域内可用
            if (entry.getText() != null) { // 假设 entry.getText() 返回 JsonElement
                try {
                    Component textComponent = Component.Serializer.fromJson(entry.getText(), null);
                    if (textComponent != null) {
                        Component resolvedTextComponent = ComponentUtils.updateForEntity(commandSource, textComponent, player, 0);
                        entry.setText(Component.Serializer.toJsonTree(resolvedTextComponent));
                    }
                } catch (JsonSyntaxException e) {
                    Dialog.LOGGER.warn("Failed to parse text component JSON for entry '{}' (dialog '{}') for player {}: {}. Skipping text update.",
                                       entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                } catch (Exception e) {
                    Dialog.LOGGER.error("Unexpected error processing text component for entry '{}' (dialog '{}') for player {}: {}. Skipping text update.",
                                       entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage(), e);
                }
            }

            if (entry.getSpeaker() != null) { // 假设 entry.getSpeaker() 返回 JsonElement
                try {
                    Component speakerComponent = Component.Serializer.fromJson(entry.getSpeaker(), null);
                    if (speakerComponent != null) {
                        Component resolvedSpeakerComponent = ComponentUtils.updateForEntity(commandSource, speakerComponent, player, 0);
                        entry.setSpeaker(Component.Serializer.toJsonTree(resolvedSpeakerComponent));
                    }
                } catch (JsonSyntaxException e) {
                    Dialog.LOGGER.warn("Failed to parse speaker component JSON for entry '{}' (dialog '{}') for player {}: {}. Skipping speaker update.",
                                       entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                } catch (Exception e) {
                    Dialog.LOGGER.error("Unexpected error processing speaker component for entry '{}' (dialog '{}') for player {}: {}. Skipping speaker update.",
                                       entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage(), e);
                }
            }
*/
            // 如果条目可见，再处理其选项的可见性
            if (entry.hasOptions()) {
                List<DialogOption> visibleOptions = new ArrayList<>();
                for (DialogOption option : entry.getOptions()) {
                    String optionVisibilityCommand = option.getVisibilityCommand();
                    if (optionVisibilityCommand == null || optionVisibilityCommand.isEmpty()) {
                        visibleOptions.add(option);
                        continue;
                    }

                    try {
                        int result = dispatcher.execute(dispatcher.parse(optionVisibilityCommand, commandSource));
                        if (result == 1) {
                            visibleOptions.add(option);
                        } else {
                            Dialog.LOGGER.debug("Visibility command '{}' for option '{}' (dialog '{}', entry '{}') for player {} returned {}, option hidden.",
                                               optionVisibilityCommand, option.getText(player.level().registryAccess(), player.getName().getString()) != null ? option.getText(player.level().registryAccess(), player.getName().getString()).getString() : "<no text>", playerSpecificSequence.getId(), entry.getId(), player.getName().getString(), result);
                        }
                    } catch (CommandSyntaxException e) {
                        Dialog.LOGGER.warn("Syntax error in visibility command '{}' for option '{}' (dialog '{}', entry '{}') for player {}: {}. Option hidden.",
                                           optionVisibilityCommand, option.getText(player.level().registryAccess(), player.getName().getString()) != null ? option.getText(player.level().registryAccess(), player.getName().getString()).getString() : "<no text>", playerSpecificSequence.getId(), entry.getId(), player.getName().getString(), e.getMessage());
                    } catch (Exception e) {
                        Dialog.LOGGER.warn("Error executing visibility command '{}' for option '{}' (dialog '{}', entry '{}') for player {}: {}. Option hidden.",
                                           optionVisibilityCommand, option.getText(player.level().registryAccess(), player.getName().getString()) != null ? option.getText(player.level().registryAccess(), player.getName().getString()).getString() : "<no text>", playerSpecificSequence.getId(), entry.getId(), player.getName().getString(), e.getMessage());
                    }
                }
                entry.setOptions(visibleOptions.toArray(new DialogOption[0]));
            }
            visibleEntries.add(entry); // 将可见的条目（及其处理过的选项）添加到列表
        }
        playerSpecificSequence.setEntries(visibleEntries.toArray(new DialogEntry[0]));
        return playerSpecificSequence;
    }

    /**
     * (客户端) 接收并缓存单个对话数据。
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveDialogData(String dialogId, String dialogJson) {
        if (Minecraft.getInstance().level == null || !Minecraft.getInstance().level.isClientSide) return;
        try {
            DialogSequence sequence = GSON.fromJson(dialogJson, DialogSequence.class);
            if (sequence != null && sequence.getId() != null) {
                clientDialogSequences.put(sequence.getId(), sequence);
                // Cached content grants no authority to start a server session.
            } else {
                Dialog.LOGGER.warn("Failed to parse the dialog data received from the server or the ID is null: {}", dialogId);
                sendPlayerMessage(Component.translatable("dialog.manager.received_sequence_empty", dialogId));
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to parse dialog '{}' JSON received from server", dialogId);
            sendPlayerMessage(Component.translatable("dialog.manager.received_parse_failed", dialogId, e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * 显示指定ID的对话序列。
     */
    @OnlyIn(Dist.CLIENT)
    public void showDialog(String dialogId) {
        // The server applies the same permission check as /dialog show.
        NetworkHandler.sendRequestDialogToServer(dialogId);
    }

        /**
     * (客户端) 接收从服务端发送过来的、已经为当前玩家过滤好选项的完整对话序列，并显示它。
     * @param dialogId 对话的ID (主要用于日志和潜在的映射键)。
     * @param sequenceJson 包含完整对话序列（已过滤选项）的JSON字符串。
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveAndShowPlayerSpecificDialog(UUID sessionId, String dialogId, String sequenceJson) {
        if (Minecraft.getInstance().level == null || !Minecraft.getInstance().level.isClientSide) return;
        
        stopAutoPlay(); // Reset auto-play

        DialogSequence playerSequence;
        try {
            playerSequence = GSON.fromJson(sequenceJson, DialogSequence.class);
        } catch (JsonSyntaxException e) {
            Dialog.LOGGER.error("Failed to parse player-specific dialog sequence JSON for ID {}: {}", dialogId, e.getMessage());
            sendPlayerMessage(Component.translatable("dialog.manager.received_sequence_parse_failed", dialogId, e.getMessage()));
            return;
        }

        if (playerSequence == null || playerSequence.getId() == null) {
            Dialog.LOGGER.warn("Parsed player-specific dialog sequence is null or has no ID. Original ID: {}", dialogId);
            sendPlayerMessage(Component.translatable("dialog.manager.received_sequence_empty", dialogId));
            return;
        }

        if (!dialogId.equals(playerSequence.getId())) {
            Dialog.LOGGER.warn("Dialog ID mismatch! Expected (from packet): {}, ID in parsed sequence: {}. Using ID from sequence.", dialogId, playerSequence.getId());
        }
        
        clearDialogHistory();
        currentSessionId = sessionId;
        currentRevision = 0;
        actionPending = false;
        currentSequence = playerSequence;
        currentEntry = playerSequence.getFirstEntry();
        
        if (currentEntry == null) {
            Dialog.LOGGER.error("No entries found in player-specific dialog sequence: {}", playerSequence.getId());
            sendPlayerMessage(Component.translatable("dialog.manager.no_entries", playerSequence.getId()));
            currentSequence = null; 
            return;
        }

        addDialogToHistory(currentEntry);

        String playerName = "";
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getGameProfile() != null) {
            playerName = Minecraft.getInstance().player.getGameProfile().getName();
        }
        this.currentDialogPlayerName = playerName;

        Minecraft.getInstance().setScreen(new DialogScreen(currentSequence, currentEntry, this.currentDialogPlayerName));
    }

    /**
     * 获取快速跳过标记。
     */
    public static boolean isFastForwardingNext() {
        return isFastForwardingNext;
    }

    /**
     * 设置快速跳过标记。
     */
    public static void setFastForwardingNext(boolean fastForwardingNext) {
        isFastForwardingNext = fastForwardingNext;
    }

    /**
     * 获取自动播放状态。
     */
    public static boolean isAutoPlaying() {
        return isAutoPlaying;
    }

    /**
     * 设置自动播放状态。
     */
    public static void setAutoPlaying(boolean autoPlaying) {
        isAutoPlaying = autoPlaying;
    }

    /**
     * 停止自动播放。
     */
    public static void stopAutoPlay() {
        isAutoPlaying = false;
    }
    
    /**
     * 显示对话序列中的下一条对话。
     */
    @OnlyIn(Dist.CLIENT)
    public void showNextDialog() {
        requestAction(DialogSession.ADVANCE);
    }

    @OnlyIn(Dist.CLIENT)
    public void chooseOption(int index) {
        requestAction(index);
    }

    @OnlyIn(Dist.CLIENT)
    private void requestAction(int optionIndex) {
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().getConnection() == null
                || currentSessionId == null || currentEntry == null || actionPending) return;
        actionPending = true;
        NetworkHandler.sendDialogActionToServer(new DialogActionPacket(
                currentSessionId, currentRevision, currentEntry.getId(), optionIndex));
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isActionPending() {
        return actionPending;
    }

    @OnlyIn(Dist.CLIENT)
    public void cancelDialog() {
        if (currentSessionId != null && currentEntry != null && Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.sendDialogActionToServer(new DialogActionPacket(
                    currentSessionId, currentRevision, currentEntry.getId(), DialogSession.CANCEL));
        }
        clearClientSession();
    }

    @OnlyIn(Dist.CLIENT)
    public void receiveDialogState(DialogStatePacket state) {
        if (Minecraft.getInstance().level == null || !state.sessionId().equals(currentSessionId)
                || state.revision() < currentRevision) return;
        actionPending = false;
        if (state.revision() == currentRevision) return; // Rejected action; keep the current node.
        if (state.optionIndex() >= 0 && currentEntry != null && currentEntry.hasOptions()
                && state.optionIndex() < currentEntry.getOptions().length) {
            recordChoiceForCurrentDialog(currentEntry.getOptions()[state.optionIndex()]
                    .getText(Minecraft.getInstance().level.registryAccess(), currentDialogPlayerName).getString());
        }
        currentRevision = state.revision();
        DialogEntry next = state.entryId().isEmpty() ? null : currentSequence.findEntryById(state.entryId());
        if (next == null) {
            clearClientSession();
            if (Minecraft.getInstance().screen instanceof DialogScreen) Minecraft.getInstance().setScreen(null);
            return;
        }
        currentEntry = next;
        addDialogToHistory(currentEntry);
        Minecraft.getInstance().setScreen(new DialogScreen(currentSequence, currentEntry, currentDialogPlayerName));
    }
}

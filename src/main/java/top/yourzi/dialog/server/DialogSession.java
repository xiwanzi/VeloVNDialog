package top.yourzi.dialog.server;

import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** A server-owned, per-player cursor. Clients can request transitions, never commands. */
public final class DialogSession {
    public static final int ADVANCE = -1;
    public static final int CANCEL = -2;

    private final UUID playerId;
    private final UUID id = UUID.randomUUID();
    private final DialogSequence sequence;
    private DialogEntry currentEntry;
    private int revision;

    public DialogSession(UUID playerId, DialogSequence sequence) {
        this.playerId = Objects.requireNonNull(playerId);
        this.sequence = Objects.requireNonNull(sequence);
        var ids = new HashSet<String>();
        if (sequence.getEntries() == null) throw new IllegalArgumentException("Dialog has no entries");
        for (DialogEntry entry : sequence.getEntries()) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()
                    || entry.getId().length() > 256 || !ids.add(entry.getId())) {
                throw new IllegalArgumentException("Dialog entry IDs must be unique and 1-256 characters long");
            }
        }
        currentEntry = sequence.getFirstEntry();
        if (currentEntry == null) throw new IllegalArgumentException("Dialog has no visible starting entry");
    }

    public UUID id() { return id; }
    public int revision() { return revision; }
    public String entryId() { return currentEntry == null ? "" : currentEntry.getId(); }

    /** Invalid requests do not consume a revision or execute commands. */
    public Transition transition(UUID playerId, UUID sessionId, int expectedRevision, String entryId,
                                 int optionIndex, Predicate<String> condition) {
        if (!this.playerId.equals(playerId) || !id.equals(sessionId) || currentEntry == null) return null;
        // Closing also works while an earlier advance is in flight. It never executes commands.
        if (optionIndex == CANCEL) {
            currentEntry = null;
            return new Transition(++revision, "", CANCEL, List.of());
        }
        if (revision != expectedRevision || !currentEntry.getId().equals(entryId)) return null;

        DialogOption option = null;
        DialogEntry next;
        if (currentEntry.hasOptions()) {
            if (optionIndex < 0 || optionIndex >= currentEntry.getOptions().length) return null;
            option = currentEntry.getOptions()[optionIndex];
            if (option == null) return null;
            next = sequence.findEntryById(option.getTargetId());
            if (next == null) return null;
        } else {
            if (optionIndex != ADVANCE) return null;
            next = sequence.getNextEntry(currentEntry);
            if (next == null && currentEntry.getNextId() != null && !currentEntry.getNextId().isBlank()) return null;
        }
        if (!visible(currentEntry.getVisibilityCommand(), condition)
                || (option != null && !visible(option.getVisibilityCommand(), condition))) return null;

        var commands = new ArrayList<String>();
        addCommands(commands, currentEntry.getCommand());
        if (option != null) addCommands(commands, option.getCommand());
        // Commit the cursor before the caller executes side effects, including reentrant commands.
        currentEntry = next;
        return new Transition(++revision, entryId(), optionIndex, List.copyOf(commands));
    }

    private static boolean visible(String command, Predicate<String> condition) {
        return command == null || command.isBlank() || condition.test(command);
    }

    private static void addCommands(List<String> result, List<String> commands) {
        if (commands != null) {
            commands.stream().filter(Objects::nonNull).filter(command -> !command.isBlank()).forEach(result::add);
        }
    }

    public record Transition(int revision, String entryId, int optionIndex, List<String> commands) {}
}

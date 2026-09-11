package top.yourzi.dialog.server;

import net.minecraft.server.packs.resources.ResourceManager;
import top.yourzi.dialog.core.DialogCompiler;
import top.yourzi.dialog.core.DialogDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prepare off-thread, then atomically publish a complete catalog on the server thread. */
public final class DialogCatalog {
    private volatile Map<String, DialogDefinition> definitions = Map.of();

    public Map<String, DialogDefinition> snapshot() { return definitions; }
    public DialogDefinition get(String id) { return definitions.get(id); }
    public void publish(Map<String, DialogDefinition> prepared) { definitions = Map.copyOf(prepared); }

    public Map<String, DialogDefinition> prepare(ResourceManager resources, Path configDirectory) {
        Map<String, String> bundled = new LinkedHashMap<>();
        var errors = new ArrayList<String>();
        resources.listResources("dialogs", path -> path.getNamespace().equals("dialog") && path.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    try (var stream = entry.getValue().open()) {
                        bundled.put(entry.getKey().toString(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException e) { errors.add(entry.getKey() + ": " + e.getMessage()); }
                });
        Map<String, String> configured = new LinkedHashMap<>();
        if (Files.isDirectory(configDirectory)) {
            try (var paths = Files.walk(configDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    try { configured.put(path.toString(), Files.readString(path, StandardCharsets.UTF_8)); }
                    catch (IOException e) { errors.add(path + ": " + e.getMessage()); }
                }
            } catch (IOException e) { errors.add(configDirectory + ": " + e.getMessage()); }
        }
        Map<String, DialogDefinition> result = compile(bundled, errors);
        result.putAll(compile(configured, errors)); // An explicit config overrides a datapack definition.
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return Map.copyOf(result);
    }

    public static Map<String, DialogDefinition> compile(Map<String, String> sources) {
        var errors = new ArrayList<String>();
        Map<String, DialogDefinition> result = compile(sources, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return Map.copyOf(result);
    }

    private static Map<String, DialogDefinition> compile(Map<String, String> sources, ArrayList<String> errors) {
        var result = new LinkedHashMap<String, DialogDefinition>();
        sources.forEach((source, json) -> {
            try {
                DialogDefinition definition = DialogCompiler.compile(json);
                if (result.putIfAbsent(definition.id(), definition) != null) errors.add(source + ": Duplicate dialog ID: " + definition.id());
            } catch (RuntimeException e) { errors.add(source + ": " + e.getMessage()); }
        });
        return result;
    }
}

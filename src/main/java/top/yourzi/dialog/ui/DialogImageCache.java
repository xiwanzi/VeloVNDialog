package top.yourzi.dialog.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import top.yourzi.dialog.util.ImageDimensions;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded metadata cache; it never retains decoded pixels or native allocations. */
public final class DialogImageCache {
    private static final int MAX_ENTRIES = 256;
    private static final Map<ResourceLocation, ImageDimensions> DIMENSIONS = new LinkedHashMap<>(16, 0.75f, true);

    private DialogImageCache() {}

    public static synchronized ImageDimensions get(ResourceProvider resources, ResourceLocation location) throws IOException {
        ImageDimensions cached = DIMENSIONS.get(location);
        if (cached != null) return cached;
        try (var input = resources.getResourceOrThrow(location).open()) {
            ImageDimensions dimensions = ImageDimensions.read(input);
            if (DIMENSIONS.size() >= MAX_ENTRIES) DIMENSIONS.remove(DIMENSIONS.keySet().iterator().next());
            DIMENSIONS.put(location, dimensions);
            return dimensions;
        }
    }

    public static synchronized void clear() { DIMENSIONS.clear(); }
}

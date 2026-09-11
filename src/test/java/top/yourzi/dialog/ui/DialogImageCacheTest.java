package top.yourzi.dialog.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.yourzi.dialog.util.ImageDimensions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DialogImageCacheTest {
    private final AtomicInteger opens = new AtomicInteger();
    private final AtomicInteger closes = new AtomicInteger();

    @AfterEach void clear() { DialogImageCache.clear(); }

    private ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("dialog", path); }

    private ResourceProvider provider(byte[] data) {
        return location -> Optional.of(new Resource(null, () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(data) {
                @Override public void close() throws IOException { closes.incrementAndGet(); super.close(); }
            };
        }));
    }

    private byte[] png(int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @Test void dimensionsNeedOnlyThePngHeaderNotDecodedPixels() throws IOException {
        byte[] header = Arrays.copyOf(png(19, 37), 33);
        assertEquals(new ImageDimensions(19, 37), ImageDimensions.read(new ByteArrayInputStream(header)));
    }

    @Test void repeatedPortraitAndBackgroundLoadsUseMetadataAndCloseTheStream() throws IOException {
        var resources = provider(png(32, 48));
        for (int i = 0; i < 400; i++) {
            assertEquals(new ImageDimensions(32, 48), DialogImageCache.get(resources, id("portrait.png")));
        }
        assertEquals(1, opens.get());
        assertEquals(1, closes.get());
    }

    @Test void resourceReloadInvalidatesPreviouslyCachedDimensions() throws IOException {
        assertEquals(new ImageDimensions(8, 16), DialogImageCache.get(provider(png(8, 16)), id("portrait.png")));
        DialogImageCache.clear();
        assertEquals(new ImageDimensions(64, 128), DialogImageCache.get(provider(png(64, 128)), id("portrait.png")));
        assertEquals(opens.get(), closes.get());
    }

    @Test void cacheEvictsOldMetadataInsteadOfGrowingForever() throws IOException {
        var resources = provider(png(2, 3));
        for (int i = 0; i < 257; i++) DialogImageCache.get(resources, id("portrait" + i + ".png"));
        assertEquals(257, opens.get());
        DialogImageCache.get(resources, id("portrait0.png"));
        assertEquals(258, opens.get());
        assertEquals(opens.get(), closes.get());
    }

    @Test void invalidImagesCloseStreamsAndDoNotPoisonTheCache() throws IOException {
        var location = id("broken.png");
        assertThrows(IOException.class, () -> DialogImageCache.get(provider(new byte[] {1, 2, 3}), location));
        assertEquals(1, closes.get());
        assertEquals(new ImageDimensions(3, 4), DialogImageCache.get(provider(png(3, 4)), location));
        assertEquals(2, opens.get());
        assertEquals(2, closes.get());
    }
}

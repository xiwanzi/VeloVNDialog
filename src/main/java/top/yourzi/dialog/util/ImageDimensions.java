package top.yourzi.dialog.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads image headers only. Texture pixels remain owned by Minecraft's texture manager. */
public record ImageDimensions(int width, int height) {
    public static ImageDimensions read(InputStream input) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw new IOException("Invalid image dimensions");
                return new ImageDimensions(width, height);
            } finally {
                reader.dispose();
            }
        }
    }
}

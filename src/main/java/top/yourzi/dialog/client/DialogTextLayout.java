package top.yourzi.dialog.client;

import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Wrapped text is measured once; prefixes preserve styles and complete Unicode code points. */
public final class DialogTextLayout {
    private final List<FormattedCharSequence> lines;
    private final List<Integer> lengths;
    private final int length;

    public DialogTextLayout(List<FormattedCharSequence> lines) {
        this.lines = List.copyOf(lines);
        var counts = new ArrayList<Integer>();
        int total = 0;
        for (var line : lines) {
            int[] count = {0};
            line.accept((index, style, codepoint) -> { count[0]++; return true; });
            counts.add(count[0]); total += count[0];
        }
        lengths = List.copyOf(counts); length = total;
    }

    public int length() { return length; }

    public List<FormattedCharSequence> visible(int glyphs) {
        if (glyphs >= length) return lines;
        var visible = new ArrayList<FormattedCharSequence>();
        for (int i = 0; i < lines.size() && glyphs > 0; i++) {
            FormattedCharSequence line = lines.get(i);
            if (glyphs >= lengths.get(i)) visible.add(line);
            else {
                int limit = glyphs;
                visible.add(sink -> {
                    int[] count = {0};
                    return line.accept((index, style, codepoint) -> count[0]++ < limit && sink.accept(index, style, codepoint));
                });
            }
            glyphs -= lengths.get(i);
        }
        return visible;
    }
}

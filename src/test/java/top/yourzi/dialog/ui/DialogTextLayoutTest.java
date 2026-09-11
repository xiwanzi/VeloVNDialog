package top.yourzi.dialog.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import top.yourzi.dialog.client.DialogTextLayout;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DialogTextLayoutTest {
    @Test void partialTextKeepsColorsAndNeverSplitsEmoji() {
        Style gold = Style.EMPTY.withColor(ChatFormatting.GOLD);
        var layout = new DialogTextLayout(List.of(FormattedCharSequence.forward("A😀中", gold), FormattedCharSequence.forward("B", Style.EMPTY)));
        assertEquals(4, layout.length());
        StringBuilder text = new StringBuilder(); List<Style> styles = new ArrayList<>();
        for (var line : layout.visible(2)) line.accept((index, style, cp) -> { text.appendCodePoint(cp); styles.add(style); return true; });
        assertEquals("A😀", text.toString()); assertEquals(List.of(gold, gold), styles);
        assertTrue(layout.visible(0).isEmpty()); assertEquals(2, layout.visible(4).size());
    }
}

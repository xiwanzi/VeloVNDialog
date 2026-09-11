package top.yourzi.dialog.core;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import top.yourzi.dialog.client.ClientText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Style;
import java.util.ArrayList;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TextTemplateTest {
    @Test void substitutionPreservesTranslationArgumentsStylesAndJsonEscaping() {
        var raw = JsonParser.parseString("{\"translate\":\"test.key\",\"with\":[5,true,\"${faction}\"],\"color\":\"gold\"}");
        TextTemplate.validate(raw);
        var context = new TestContext(); context.state.set("faction", new JsonPrimitive("a\"b\\c"));
        var resolved = TextTemplate.resolve(TextTemplate.choose(raw, new Random()), context).getAsJsonObject();
        assertTrue(resolved.getAsJsonArray("with").get(0).getAsJsonPrimitive().isNumber());
        assertTrue(resolved.getAsJsonArray("with").get(1).getAsJsonPrimitive().isBoolean());
        assertEquals("a\"b\\c", resolved.getAsJsonArray("with").get(2).getAsString());
        assertEquals("test.key", resolved.get("translate").getAsString());
        assertEquals("gold", resolved.get("color").getAsString());
        assertEquals(resolved, JsonParser.parseString(resolved.toString()));
    }
    @Test void legacyTopLevelArraysDoNotInheritTheFirstElementsColor() {
        var component = ClientText.read(JsonParser.parseString("[{\"text\":\"a\",\"color\":\"gold\"},\"b\"]"), RegistryAccess.EMPTY);
        var colors = new ArrayList<Integer>();
        component.visit((style, text) -> { if (!text.isEmpty()) colors.add(style.getColor() == null ? -1 : style.getColor().getValue()); return java.util.Optional.empty(); }, Style.EMPTY);
        assertEquals("ab", component.getString());
        assertEquals(java.util.List.of(ChatFormatting.GOLD.getColor(), -1), colors);
    }
}

package top.yourzi.dialog.server;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SavedDialogVariablesTest {
    @Test void savesTypedStatePerUuidAndRestoresMoreThan1024Variables() {
        var store = new SavedDialogVariables();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        store.player(a).set("faction", new JsonPrimitive("a"));
        store.player(a).set("accepted", new JsonPrimitive(true));
        store.player(a).set("value", new JsonPrimitive(new BigDecimal("12345678901234567890.123456789")));
        store.player(b).set("faction", new JsonPrimitive("b"));
        for (int i = 0; i < 1500; i++) store.player(a).set("quest." + i, new JsonPrimitive(i));
        assertTrue(store.isDirty());
        var restored = SavedDialogVariables.load(store.save(new CompoundTag(), null), null);
        assertEquals("a", restored.player(a).get("faction").getAsString());
        assertEquals("b", restored.player(b).get("faction").getAsString());
        assertEquals(1499, restored.player(a).get("quest.1499").getAsInt());
        assertTrue(restored.player(a).get("accepted").getAsBoolean());
        assertEquals(new BigDecimal("12345678901234567890.123456789"), restored.player(a).get("value").getAsBigDecimal());
        assertFalse(restored.isDirty());
        restored.player(a).set("faction", JsonNull.INSTANCE);
        assertTrue(restored.isDirty());
        assertTrue(restored.player(a).get("faction").isJsonNull());
    }
}

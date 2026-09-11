package top.yourzi.dialog.server;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BukkitIntegrationsTest {
    @Test void missingBukkitKeepsTextAndCannotBecomeGrantedThroughNegation() {
        var bridge = new BukkitIntegrations(new ClassLoader(null) {});
        assertFalse(bridge.permissionsAvailable()); assertFalse(bridge.papiAvailable());
        assertEquals("Hello %xconomy_balance_value%", bridge.expand(UUID.randomUUID(), "Hello %xconomy_balance_value%"));
        assertThrows(IllegalStateException.class, () -> bridge.permission(UUID.randomUUID(), "story.vip"));
    }
}

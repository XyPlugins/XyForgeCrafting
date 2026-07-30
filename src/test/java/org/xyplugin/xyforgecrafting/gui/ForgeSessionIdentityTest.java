package org.xyplugin.xyforgecrafting.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class ForgeSessionIdentityTest {
    @Test
    public void matchesInventoryWrappersByOwnerAndSessionId() {
        UUID owner = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ForgeSession session = new ForgeSession(owner, sessionId, null);

        assertTrue(session.matches(new ForgeHolder(owner, sessionId)));
        assertFalse(session.matches(new ForgeHolder(owner, UUID.randomUUID())));
        assertFalse(session.matches(new ForgeHolder(UUID.randomUUID(), sessionId)));
    }
}

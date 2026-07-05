package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ClickMenuSlot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMenuActionsTest
{
    @Test
    void clickMenuSlotCommand_shouldRejectNegativeSlot()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() -> new ClickMenuSlot.Command("request-1", UUID.randomUUID(), -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slot");
    }
}

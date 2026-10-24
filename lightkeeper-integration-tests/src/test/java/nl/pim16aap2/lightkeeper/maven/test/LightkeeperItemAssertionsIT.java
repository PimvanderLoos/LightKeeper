package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.*;

/** Verifies shared live/frozen menu, inventory and message assertions on Paper and Spigot. */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperItemAssertionsIT
{
    @Test
    void menuAssertions_shouldInspectFrozenItemsAndObserveLiveTransitions(ILightkeeperFramework framework)
    {
        // setup
        final var player = framework.bots().builder()
            .withName("lkitemassert")
            .atSpawn(framework.worlds().main())
            .withPermissions("lightkeeper.testplugin.lktestgui")
            .build();

        // execute
        final var menu = player.executeCommand("lktestgui").andWaitForMenuOpen();
        final var frozen = assertThat(menu).snapshot();

        // verify
        assertThat(menu)
            .isOpen()
            .hasTitle("Main Menu")
            .hasOccupiedSlotCount(2)
            .hasEmptySlot(8);

        frozen
            .itemAt(0)
            .isMaterial("STONE")
            .hasDisplayName("Button 1")
            .lore()
            .isEmpty();

        assertThat(player)
            .inventory()
            .isEmpty();

        // execute
        menu.clickAtIndex(0).andWaitTicks(1);

        // verify
        assertThat(menu)
            .hasTitle("Sub Menu")
            .hasItemAt(0, "BARRIER");

        frozen
            .hasTitle("Main Menu")
            .itemAt(0)
            .isMaterial("stone");

        assertThat(player)
            .receivedMessages()
            .contains("You clicked Button 1");

        // execute
        menu.clickAtIndex(2).andWaitForMenuClose();

        // verify
        assertThat(menu).isClosed();

        assertThatThrownBy(() -> assertThat(menu).hasEmptySlot(8))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("closed");
    }
}

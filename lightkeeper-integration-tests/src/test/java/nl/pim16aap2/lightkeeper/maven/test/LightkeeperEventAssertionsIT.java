package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;

/** Exercises typed capture and cancellation assertions through the real agent on both platforms. */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperEventAssertionsIT
{
    @Test
    void eventAssertions_shouldObserveTypedMessagesReferencesAndCancellation(ILightkeeperFramework framework)
    {
        // setup
        final var player = framework.bots().join("lkeventassert", framework.worlds().main());
        try (var capture = framework.events().capture("org.bukkit.event.player.AsyncPlayerChatEvent"))
        {
            assertThat(capture).hasNoEvents();

            // execute
            capture.cancelNext(1);
            player.chat("cancelled assertion message");
            player.chat("uncancelled assertion message");
            eventually(Duration.ofSeconds(10), () -> assertThat(capture).hasEventCount(2));

            // verify
            assertThat(capture)
                .events()
                .hasSize(2);
            final var events = capture.getCapturedEvents();
            assertThat(events.getFirst())
                .isCancelled()
                .value("getMessage")
                .asString()
                .isEqualTo("cancelled assertion message");
            assertThat(events.get(1))
                .isNotCancelled()
                .value("getMessage")
                .asString()
                .isEqualTo("uncancelled assertion message");

            assertThat(events.getFirst().value("getPlayer"))
                .isInstanceOfSatisfying(
                    IProtocolValue.PRef.class,
                    reference -> assertThat(events.getFirst())
                        .value("getPlayer")
                        .hasReference(reference.className(), player.uniqueId().toString())
                );
        }
    }
}

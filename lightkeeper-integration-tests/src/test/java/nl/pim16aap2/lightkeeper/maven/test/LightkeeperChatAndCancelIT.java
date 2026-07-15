package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperChatAndCancelIT
{
    private static final String CHAT_EVENT = "org.bukkit.event.player.AsyncPlayerChatEvent";

    @Test
    void chat_shouldFireCapturedChatEventWithTickStamp(ILightkeeperFramework framework)
        throws Exception
    {
        // setup
        final var world = framework.mainWorld();
        final var player = framework.createPlayer("lkchat001", world);
        final long tickBefore = framework.currentServerTick();

        try (var chatCapture = framework.captureEvents(CHAT_EVENT))
        {
            // execute
            player.chat("hello lightkeeper");
            eventually(Duration.ofSeconds(10), () ->
                assertThat(chatCapture.getCapturedEvents()).isNotEmpty());

            // verify
            final CapturedEventSnapshot chatEvent = chatCapture.getCapturedEvents().getFirst();
            assertThat(chatEvent.value("getMessage"))
                .isEqualTo(new IProtocolValue.PString("hello lightkeeper"));
            assertThat(chatEvent.tick()).isGreaterThanOrEqualTo(tickBefore);
            assertThat(framework.currentServerTick()).isGreaterThanOrEqualTo(chatEvent.tick());
        }
    }

    @Test
    void cancelNext_shouldCancelExactlyTheNextChatEvent(ILightkeeperFramework framework)
        throws Exception
    {
        // setup
        final var world = framework.mainWorld();
        final var player = framework.createPlayer("lkchat002", world);

        try (var chatCapture = framework.captureEvents(CHAT_EVENT))
        {
            // execute
            chatCapture.cancelNext(1);
            player.chat("this one is cancelled");
            player.chat("this one goes through");
            eventually(Duration.ofSeconds(10), () ->
                assertThat(chatCapture.getCapturedEvents()).hasSize(2));

            // verify — the MONITOR capture observes the final cancelled state set at LOWEST priority
            final var events = chatCapture.getCapturedEvents();
            assertThat(events.get(0).value("isCancelled"))
                .isEqualTo(new IProtocolValue.PBool(true));
            assertThat(events.get(1).value("isCancelled"))
                .isEqualTo(new IProtocolValue.PBool(false));
        }
    }
}

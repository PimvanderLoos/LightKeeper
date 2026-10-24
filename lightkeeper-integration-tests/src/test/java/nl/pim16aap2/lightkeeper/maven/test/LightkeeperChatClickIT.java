package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Optional;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperChatClickIT
{
    @Test
    void chatClick_shouldCaptureExtractAndDispatchRunCommandComponent(ILightkeeperFramework framework)
    {
        // setup
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().join("lkclick01", world);

        // execute — the test plugin sends the bot a clickable run_command chat component
        player.executeCommand("lktestclick");
        framework.waitUntil(
            () -> player
                .chatComponents().stream()
                .anyMatch(component -> component.clickRunCommand().isPresent()),
            Duration.ofSeconds(15)
        );

        final ChatComponentSnapshot clickable = player.chatComponents().stream()
            .filter(component -> component.clickRunCommand().isPresent())
            .reduce((first, second) -> second)
            .orElseThrow();

        // verify — the native codec spells the field click_event (snake_case), never the stale clickEvent
        assertThat(clickable.json())
            .contains("\"click_event\"")
            .doesNotContain("\"clickEvent\"");

        final Optional<String> extracted = clickable.clickRunCommand();
        assertThat(extracted).contains("/lktestclick confirm");
        assertThat(clickable)
            .hasClickableText("LK_CLICK_ME")
            .runsCommand("LK_CLICK_ME", "/lktestclick confirm");

        assertThat(player).hasClickableChatText("LK_CLICK_ME");

        // execute — clicking the component dispatches its run_command as the player
        player.clickChatComponent(clickable);

        // verify — the dispatched command produced its observable confirmation message
        eventually(
            Duration.ofSeconds(15),
            () -> assertThat(player)
                .receivedMessagesText()
                .contains("LK_TEST_CLICK confirmed")
        );
    }

    @Test
    void chatAssertions_shouldAssociateInheritedLiteralTextAndCommand(ILightkeeperFramework framework)
    {
        // setup
        final var player = framework.bots().join("lkinheritclick", framework.worlds().main());
        final String json = """
            {"text":"","click_event":{"action":"run_command","command":"/lktestclick confirm"},
              "extra":[{"text":"Click "},{"text":"me"}]}
            """.replace('\n', ' ');

        // execute
        assertThat(
            framework
                .server()
                .executeCommand(CommandSource.CONSOLE, "minecraft:tellraw " + player.name() + " " + json)
        ).isSuccessful();

        // verify
        eventually(
            Duration.ofSeconds(10),
            () -> assertThat(player)
                .chatComponents()
                .anySatisfy(component -> assertThat(component).runsCommand("Click me", "/lktestclick confirm"))
        );
    }
}

package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies returned outcomes and structured warning assertions on both server platforms. */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperResultAssertionsIT
{
    @Test
    void resultAssertions_shouldObserveDispatchAndFiredInteraction(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final var player = framework.bots().join("lkresultassert", world);
        world.setBlockAt(new BlockPos(1, 100, 1), "stone");

        // execute
        final var success = framework.server().executeCommand(CommandSource.CONSOLE, "time set day");
        final var failure = framework.server().executeCommand(CommandSource.CONSOLE, "lightkeeper_missing_command");
        final var interaction = player.rightClickBlock(new BlockPos(1, 100, 1));

        // verify
        assertThat(success).isSuccessful();
        assertThat(failure).isFailure();
        assertThat(interaction).hasFiredEvent().isNotCancelled();
    }

    @Test
    void errors_shouldDistinguishWarningsWithoutStrengtheningNoErrorCheck(ILightkeeperFramework framework)
    {
        // setup
        final var errors = assertThat(framework).server().errors();

        // execute
        framework.server().executeCommand(CommandSource.CONSOLE, "lktesterror warning");
        eventually(Duration.ofSeconds(10), () -> errors.captured()
            .anyMatch(entry -> entry.message().contains("LK_TEST_ERROR provoked warning")));

        // verify
        errors.hasNoErrors();
        assertThatThrownBy(errors::hasNoWarnings).isInstanceOf(AssertionError.class)
            .hasMessageContaining("LK_TEST_ERROR provoked warning");
    }
}

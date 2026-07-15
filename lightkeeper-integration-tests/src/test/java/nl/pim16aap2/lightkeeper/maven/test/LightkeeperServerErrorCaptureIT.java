package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage for structured server-error capture on a shared server.
 *
 * <p>Methods are ordered because the auto-clear contract is inherently cross-method: the error provoked in an
 * earlier test must be invisible to the next one.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(LightkeeperExtension.class)
class LightkeeperServerErrorCaptureIT
{
    /**
     * Message logged at SEVERE by {@code /lktesterror severe} (see the test plugin's
     * {@code LkTestErrorCommandExecutor}).
     */
    private static final String SEVERE_MARKER = "LK_TEST_ERROR provoked severe";
    /**
     * Message logged at WARNING by {@code /lktesterror warning}.
     */
    private static final String WARNING_MARKER = "LK_TEST_ERROR provoked warning";
    /**
     * Message of the throwable attached to the SEVERE log event.
     */
    private static final String THROWABLE_MARKER = "LK_TEST_ERROR boom";

    private static final Duration CAPTURE_TIMEOUT = Duration.ofSeconds(20);

    @Test
    @Order(1)
    void serverErrors_shouldCaptureProvokedSevereWithThrowableMetadata(ILightkeeperFramework framework)
    {
        // setup
        final ServerErrorsHandle serverErrors = framework.server().errors();

        // execute
        framework.server().executeCommand(CommandSource.CONSOLE, "lktesterror severe");
        framework.waitUntil(() -> containsMessage(serverErrors, SEVERE_MARKER), CAPTURE_TIMEOUT);

        // verify — the entry carries the real throwable, not scraped console text
        final ServerErrorSnapshot error = findByMessage(serverErrors, SEVERE_MARKER);
        assertThat(error.severity()).isEqualTo(ServerErrorSnapshot.Severity.ERROR);
        assertThat(error.throwableClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(error.throwableMessage()).isEqualTo(THROWABLE_MARKER);
        assertThat(error.stackTrace()).isNotEmpty();
        assertThat(error.loggerName()).isNotBlank();
        assertThat(error.threadName()).isNotBlank();
        assertThat(error.timestampMillis()).isPositive();

        // verify — hasNoServerErrors() fails with the structured context, and the allowlist can exempt it
        assertThatThrownBy(() -> assertThat(framework).hasNoServerErrors())
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining(SEVERE_MARKER)
            .hasMessageContaining("java.lang.IllegalStateException");
        assertThat(framework).hasNoServerErrors(candidate -> candidate.message().contains(SEVERE_MARKER));
    }

    @Test
    @Order(2)
    void serverErrors_shouldBeClearedAutomaticallyBetweenTestMethods(ILightkeeperFramework framework)
    {
        // verify — the severe error provoked by the previous test must not leak into this test's window
        assertThat(framework.server().errors().getCaptured())
            .noneSatisfy(error -> assertThat(error.message()).contains(SEVERE_MARKER));
        assertThat(framework).hasNoServerErrors();
    }

    @Test
    @Order(3)
    void serverErrors_shouldBufferWarningsWithoutFailingHealthCheck(ILightkeeperFramework framework)
    {
        // setup
        final ServerErrorsHandle serverErrors = framework.server().errors();

        // execute
        framework.server().executeCommand(CommandSource.CONSOLE, "lktesterror warning");
        framework.waitUntil(() -> containsMessage(serverErrors, WARNING_MARKER), CAPTURE_TIMEOUT);

        // verify — the warning is available for diagnostics but does not gate the health check
        final ServerErrorSnapshot warning = findByMessage(serverErrors, WARNING_MARKER);
        assertThat(warning.severity()).isEqualTo(ServerErrorSnapshot.Severity.WARNING);
        assertThat(warning.throwableClass()).isNull();
        assertThat(framework).hasNoServerErrors();
    }

    @Test
    @Order(4)
    void serverErrors_clearShouldDiscardProvokedError(ILightkeeperFramework framework)
    {
        // setup
        final ServerErrorsHandle serverErrors = framework.server().errors();
        framework.server().executeCommand(CommandSource.CONSOLE, "lktesterror severe");
        framework.waitUntil(() -> containsMessage(serverErrors, SEVERE_MARKER), CAPTURE_TIMEOUT);

        // execute
        serverErrors.clear();

        // verify
        assertThat(serverErrors.getCaptured())
            .noneSatisfy(error -> assertThat(error.message()).contains(SEVERE_MARKER));
        assertThat(framework).hasNoServerErrors();
    }

    private static boolean containsMessage(ServerErrorsHandle serverErrors, String marker)
    {
        return serverErrors.getCaptured().stream().anyMatch(error -> error.message().contains(marker));
    }

    private static ServerErrorSnapshot findByMessage(ServerErrorsHandle serverErrors, String marker)
    {
        return serverErrors.getCaptured().stream()
            .filter(error -> error.message().contains(marker))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No captured server error contains marker '%s'.".formatted(marker)));
    }
}

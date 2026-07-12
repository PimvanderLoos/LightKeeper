package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ClearServerErrors;
import nl.pim16aap2.lightkeeper.protocol.GetServerErrors;

import java.util.Objects;

/**
 * Protocol action handler for structured server-error capture.
 *
 * <p>Handles {@code GET_SERVER_ERRORS} and {@code CLEAR_SERVER_ERRORS} actions by delegating to
 * {@link AgentServerErrorCapture}.
 */
final class AgentServerErrorActions
{
    /**
     * Structured server-error store.
     */
    private final AgentServerErrorCapture serverErrorCapture;

    /**
     * @param serverErrorCapture
     *     Structured server-error capture facade.
     */
    AgentServerErrorActions(AgentServerErrorCapture serverErrorCapture)
    {
        this.serverErrorCapture = Objects.requireNonNull(serverErrorCapture, "serverErrorCapture");
    }

    /**
     * Handles {@code GET_SERVER_ERRORS} by returning all entries captured since agent load or the last clear.
     *
     * @param command
     *     Typed get-server-errors command.
     * @return
     *     Success response with the captured entries, dropped-entry count, and capture-active flag.
     */
    GetServerErrors.Response handleGetServerErrors(GetServerErrors.Command command)
    {
        return new GetServerErrors.Response(
            serverErrorCapture.snapshot(),
            serverErrorCapture.droppedCount(),
            serverErrorCapture.active()
        );
    }

    /**
     * Handles {@code CLEAR_SERVER_ERRORS} by discarding captured entries and resetting the dropped counter.
     *
     * @param command
     *     Typed clear-server-errors command.
     * @return
     *     Success response.
     */
    ClearServerErrors.Response handleClearServerErrors(ClearServerErrors.Command command)
    {
        serverErrorCapture.clear();
        return new ClearServerErrors.Response();
    }
}

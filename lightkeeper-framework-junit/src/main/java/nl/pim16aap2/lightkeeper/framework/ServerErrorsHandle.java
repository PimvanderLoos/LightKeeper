package nl.pim16aap2.lightkeeper.framework;

import java.util.List;
import java.util.Objects;

/**
 * Handle for the always-on server-error capture.
 *
 * <p>Unlike {@link EventCaptureHandle} there is nothing to register or close: capture starts when the agent
 * plugin loads and stays active for the server's lifetime. This handle is a view over that capture buffer.
 *
 * <p>In shared-server mode the extension clears the buffer at the <em>end</em> of every test method, so each
 * test observes the errors of its own window (the first test's window also covers server boot).
 */
public final class ServerErrorsHandle
{
    private final IFrameworkGatewayView frameworkGateway;

    ServerErrorsHandle(IFrameworkGatewayView frameworkGateway)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway");
    }

    /**
     * Gets all server errors and warnings captured since server boot or the last clear.
     *
     * @return List of captured server error snapshots, structured log events first, then raw stderr detections.
     */
    public List<ServerErrorSnapshot> getCaptured()
    {
        return frameworkGateway.capturedServerErrors();
    }

    /**
     * Clears all captured server errors.
     *
     * @return This handle for fluent chaining.
     */
    public ServerErrorsHandle clear()
    {
        frameworkGateway.clearServerErrors();
        return this;
    }
}

package nl.pim16aap2.lightkeeper.protocol;

/**
 * Waits for the given number of server ticks before responding.
 */
public final class WaitTicks
{
    private WaitTicks()
    {
    }

    /**
     * Command record for {@code WAIT_TICKS}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param ticks
     *     Number of ticks to wait (1 tick ≈ 50 ms).
     */
    public record Command(
        String requestId,
        int ticks
    ) implements IAgentCommand<Response>
    {
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            if (ticks < 0)
                throw new IllegalArgumentException("'ticks' must be >= 0, got: " + ticks);
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code WAIT_TICKS}.
     *
     * @param requestId
     *     Correlated request id.
     * @param startTick
     *     Server tick value at the start of the wait.
     * @param endTick
     *     Server tick value when the wait completed.
     */
    public record Response(
        String requestId,
        long startTick,
        long endTick
    ) implements IAgentResponse
    {
    }
}

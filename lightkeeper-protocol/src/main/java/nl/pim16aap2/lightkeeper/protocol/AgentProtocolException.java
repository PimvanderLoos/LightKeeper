package nl.pim16aap2.lightkeeper.protocol;

/**
 * Thrown by agent request handlers to signal a structured protocol-level failure.
 *
 * <p>The dispatcher catches this exception and serializes it as an error response using
 * {@link AgentErrorCode#wireCode()} rather than the generic {@code REQUEST_FAILED} code.
 *
 * <p>Example:
 * <pre>{@code
 * throw new AgentProtocolException(AgentErrorCode.AUTH_FAILED, "Auth token mismatch.");
 * }</pre>
 */
public final class AgentProtocolException extends RuntimeException
{
    /**
     * Stable machine-readable error code.
     */
    private final AgentErrorCode errorCode;

    /**
     * @param errorCode
     *     Stable machine-readable error code for this failure.
     * @param message
     *     Human-readable failure detail.
     */
    public AgentProtocolException(AgentErrorCode errorCode, String message)
    {
        super(message);
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * @param errorCode
     *     Stable machine-readable error code for this failure.
     * @param message
     *     Human-readable failure detail.
     * @param cause
     *     Underlying exception that triggered this failure.
     */
    public AgentProtocolException(AgentErrorCode errorCode, String message, Throwable cause)
    {
        super(message, cause);
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * Returns the error code that the dispatcher should include in the wire response.
     *
     * @return Non-null error code.
     */
    public AgentErrorCode errorCode()
    {
        return errorCode;
    }
}

package nl.pim16aap2.lightkeeper.runtime.agent;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Canonical agent protocol error codes.
 */
public enum AgentErrorCode
{
    INVALID_REQUEST,
    HANDSHAKE_REQUIRED,
    REQUEST_FAILED,
    AUTH_FAILED,
    PROTOCOL_MISMATCH,
    AGENT_SHA_MISMATCH,
    INVALID_ARGUMENT,
    UNSUPPORTED_SOURCE,
    TIMEOUT,
    INTERRUPTED,
    UNKNOWN;

    private static final Map<String, AgentErrorCode> BY_WIRE_CODE = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(AgentErrorCode::wireCode, Function.identity()));

    /**
     * Returns the wire-format representation of this error code.
     *
     * @return Stable wire-format code.
     */
    public String wireCode()
    {
        return name();
    }

    /**
     * Parses an error code from wire format.
     *
     * @param wireCode
     *     Wire-format value.
     * @return Parsed error code when known.
     */
    public static Optional<AgentErrorCode> fromWireCode(@Nullable String wireCode)
    {
        if (wireCode == null)
            return Optional.empty();
        return Optional.ofNullable(BY_WIRE_CODE.get(wireCode.trim().toUpperCase(java.util.Locale.ROOT)));
    }
}

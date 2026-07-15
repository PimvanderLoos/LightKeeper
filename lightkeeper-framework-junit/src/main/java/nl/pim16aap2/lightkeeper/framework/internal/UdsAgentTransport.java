package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.BotJoinDeniedException;
import nl.pim16aap2.lightkeeper.framework.BotJoinTimeoutException;
import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolMapper;
import nl.pim16aap2.lightkeeper.protocol.IAgentCommand;
import nl.pim16aap2.lightkeeper.protocol.IAgentResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Connection and wire-transport owner for typed LightKeeper agent requests over a Unix Domain Socket.
 *
 * <p>The transport owns connection retries, request/response serialization, correlation validation, response
 * timeouts, and connection cleanup. Higher-level RPC methods remain in {@link UdsAgentClient}.
 */
final class UdsAgentTransport implements AutoCloseable
{
    private static final System.Logger LOG = System.getLogger(UdsAgentTransport.class.getName());

    private final ObjectMapper objectMapper = AgentProtocolMapper.create();
    private final Path socketPath;
    private final long sendTimeoutMillis;
    private @Nullable ScheduledExecutorService readTimeoutWatchdog;
    private @Nullable SocketChannel socketChannel;
    private @Nullable BufferedReader reader;
    private @Nullable BufferedWriter writer;
    private @Nullable String closedReason;

    /**
     * Creates and connects a transport.
     *
     * @param socketPath
     *     Path of the Unix domain socket to connect to.
     * @param connectTimeout
     *     How long to keep retrying the initial connection.
     * @param sendTimeoutMillis
     *     Maximum time to wait for one response before closing the channel.
     */
    UdsAgentTransport(Path socketPath, Duration connectTimeout, long sendTimeoutMillis)
    {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath may not be null.");
        if (sendTimeoutMillis <= 0L)
            throw new IllegalArgumentException("sendTimeoutMillis must be > 0 but was " + sendTimeoutMillis + ".");
        this.sendTimeoutMillis = sendTimeoutMillis;
        connect(Objects.requireNonNull(connectTimeout, "connectTimeout may not be null."));
    }

    synchronized <R extends IAgentResponse> R send(IAgentCommand<R> command)
    {
        if (command.requestId() == null || command.requestId().isBlank())
            throw new IllegalArgumentException("'requestId' must be non-blank.");
        final BufferedWriter out = requireConnected(writer);
        final BufferedReader in = requireConnected(reader);
        final ScheduledExecutorService watchdogExecutor = requireConnected(readTimeoutWatchdog);

        final SocketChannel channelSnapshot = this.socketChannel;
        final ScheduledFuture<?> watchdog = watchdogExecutor.schedule(() ->
        {
            if (channelSnapshot != null)
            {
                try
                {
                    channelSnapshot.close();
                }
                catch (IOException ignored)
                {
                    // Best-effort: the channel may already be closed.
                }
            }
        }, sendTimeoutMillis, TimeUnit.MILLISECONDS);

        try
        {
            out.write(objectMapper.writeValueAsString(command));
            out.newLine();
            out.flush();

            final String responseLine = in.readLine();
            if (responseLine == null)
                throw new IllegalStateException(
                    "Agent connection closed unexpectedly while awaiting a response to action '%s'. The agent "
                        .formatted(command.getClass().getSimpleName())
                        + "process likely crashed — check the captured server output and the LightKeeper "
                        + "diagnostics bundle under the server work directory.");

            final JsonNode root = objectMapper.readTree(responseLine);
            validateResponseCorrelation(root, command);
            throwWhenRequestFailed(root);
            return objectMapper.treeToValue(root, command.responseType());
        }
        catch (AsynchronousCloseException exception)
        {
            this.closedReason = "a previous request timed out after %d ms".formatted(sendTimeoutMillis);
            close();
            throw new IllegalStateException(
                "Agent did not respond within %d ms for action '%s' via socket '%s'."
                    .formatted(sendTimeoutMillis, command.getClass().getSimpleName(), socketPath),
                exception
            );
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Failed to communicate with agent via socket '%s'.".formatted(socketPath),
                exception
            );
        }
        finally
        {
            watchdog.cancel(false);
        }
    }

    private static void validateResponseCorrelation(JsonNode root, IAgentCommand<?> command)
    {
        final String responseRequestId = root.path("requestId").asString("unknown");
        final String requestId = command.requestId();
        if (requestId.equals(responseRequestId))
            return;

        if (!root.path("success").asBoolean())
            throw new IllegalStateException(
                "Agent rejected request '%s' before correlation (response id '%s'): code=%s message=%s"
                    .formatted(
                        requestId,
                        responseRequestId,
                        root.path("errorCode").asString("UNKNOWN"),
                        root.path("errorMessage").asString("")));

        throw new IllegalStateException(
            "Unexpected response id '%s' for request '%s'.".formatted(responseRequestId, requestId));
    }

    private static void throwWhenRequestFailed(JsonNode root)
    {
        if (root.path("success").asBoolean())
            return;

        final String wireErrorCode = root.path("errorCode").asString();
        final AgentErrorCode errorCode = AgentErrorCode.fromWireCode(wireErrorCode).orElse(null);
        final String errorMessage = root.path("errorMessage").asString("");

        // Full-login join failures are surfaced as typed framework exceptions so tests can assert on them.
        if (errorCode == AgentErrorCode.PLAYER_JOIN_DENIED)
            throw new BotJoinDeniedException(errorMessage);
        if (errorCode == AgentErrorCode.PLAYER_JOIN_TIMEOUT)
            throw new BotJoinTimeoutException(errorMessage);

        final String displayedErrorCode = errorCode != null
            ? errorCode.wireCode()
            : "UNKNOWN (wire='%s')".formatted(wireErrorCode);
        throw new IllegalStateException(
            "Agent request failed. code=%s message=%s".formatted(displayedErrorCode, errorMessage));
    }

    synchronized void reconnect(Duration timeout)
    {
        close();
        connect(timeout);
    }

    /**
     * Closes the socket and stops the response-timeout watchdog.
     */
    @Override
    public synchronized void close()
    {
        if (readTimeoutWatchdog != null)
        {
            readTimeoutWatchdog.shutdownNow();
            readTimeoutWatchdog = null;
        }
        try
        {
            if (socketChannel != null)
            {
                socketChannel.close();
                socketChannel = null;
            }
        }
        catch (IOException ignored)
        {
            LOG.log(System.Logger.Level.TRACE, "Failed to close agent socket channel cleanly.");
        }
        finally
        {
            reader = null;
            writer = null;
        }
    }

    private void connect(Duration timeout)
    {
        this.readTimeoutWatchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("lk-read-timeout-watchdog").daemon(true).factory()
        );
        this.closedReason = null;

        final long deadline = System.nanoTime() + timeout.toNanos();
        Exception lastException = null;
        while (System.nanoTime() < deadline)
        {
            SocketChannel channel = null;
            try
            {
                channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                channel.connect(UnixDomainSocketAddress.of(socketPath));
                this.socketChannel = channel;
                this.reader = new BufferedReader(
                    new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
                this.writer = new BufferedWriter(
                    new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8));
                return;
            }
            catch (Exception exception)
            {
                lastException = exception;
                closeFailedChannel(channel, exception);
                sleep(100L);
            }
        }

        if (readTimeoutWatchdog != null)
        {
            readTimeoutWatchdog.shutdownNow();
            readTimeoutWatchdog = null;
        }
        throw new IllegalStateException(
            "Failed to connect to agent socket '%s' within timeout %s.".formatted(socketPath, timeout),
            lastException
        );
    }

    private <T> T requireConnected(@Nullable T resource)
    {
        if (resource == null)
            throw new IllegalStateException(
                closedReason != null ? "Client is not connected: " + closedReason : "Client is not connected.");
        return resource;
    }

    private static void closeFailedChannel(@Nullable SocketChannel channel, Exception connectionException)
    {
        if (channel == null)
            return;
        try
        {
            channel.close();
        }
        catch (IOException closeException)
        {
            connectionException.addSuppressed(closeException);
        }
    }

    private static void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for agent connection.", exception);
        }
    }
}

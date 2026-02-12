package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.CommandSource;
import nl.pim16aap2.lightkeeper.framework.Condition;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default LightKeeper framework implementation.
 */
public final class DefaultLightkeeperFramework implements LightkeeperFramework
{
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(45);

    private final RuntimeManifest runtimeManifest;
    private final PaperServerHandle serverHandle;
    private final UdsAgentClient agentClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private DefaultLightkeeperFramework(
        RuntimeManifest runtimeManifest,
        PaperServerHandle serverHandle,
        UdsAgentClient agentClient)
    {
        this.runtimeManifest = runtimeManifest;
        this.serverHandle = serverHandle;
        this.agentClient = agentClient;
    }

    /**
     * Starts a framework from a runtime manifest path.
     *
     * @param runtimeManifestPath
     *     Path to runtime manifest.
     * @return Started framework.
     */
    public static DefaultLightkeeperFramework start(Path runtimeManifestPath)
    {
        final RuntimeManifest runtimeManifest;
        try
        {
            runtimeManifest = new RuntimeManifestReader().read(runtimeManifestPath);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Failed to read runtime manifest at '%s'.".formatted(runtimeManifestPath),
                exception
            );
        }

        final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
        final Path serverJar = Path.of(runtimeManifest.serverJar());
        if (!Files.isDirectory(serverDirectory))
            throw new IllegalStateException("Server directory '%s' does not exist.".formatted(serverDirectory));
        if (!Files.isRegularFile(serverJar))
            throw new IllegalStateException("Server jar '%s' does not exist.".formatted(serverJar));

        final Path diagnosticsDirectory = serverDirectory.resolveSibling("lightkeeper-diagnostics");
        final PaperServerHandle serverHandle = new PaperServerHandle(runtimeManifest, diagnosticsDirectory);
        serverHandle.start(STARTUP_TIMEOUT);

        final UdsAgentClient agentClient = new UdsAgentClient(Path.of(runtimeManifest.udsSocketPath()), AGENT_CONNECT_TIMEOUT);
        agentClient.handshake(
            runtimeManifest.agentAuthToken(),
            runtimeManifest.runtimeProtocolVersion(),
            Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), "")
        );

        return new DefaultLightkeeperFramework(runtimeManifest, serverHandle, agentClient);
    }

    @Override
    public WorldHandle mainWorld()
    {
        ensureOpen();
        return new WorldHandle(this, agentClient.mainWorld());
    }

    @Override
    public WorldHandle newWorld(WorldSpec worldSpec)
    {
        ensureOpen();
        final String worldName = agentClient.newWorld(worldSpec);
        return new WorldHandle(this, worldName);
    }

    @Override
    public CommandResult executeCommand(CommandSource source, String command)
    {
        ensureOpen();
        if (source != CommandSource.CONSOLE)
            throw new IllegalArgumentException("Only CONSOLE command source is supported in v1.");

        final boolean success = agentClient.executeCommand(source, command);
        return new CommandResult(success, success ? "Command succeeded." : "Command failed.");
    }

    @Override
    public void waitUntil(Condition condition, Duration timeout)
    {
        ensureOpen();
        final long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline)
        {
            try
            {
                if (condition.evaluate())
                    return;
            }
            catch (Exception exception)
            {
                throw new IllegalStateException("Condition evaluation failed.", exception);
            }

            try
            {
                Thread.sleep(50L);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for condition.", exception);
            }
        }

        throw new IllegalStateException("Condition did not pass within timeout " + timeout + ".");
    }

    @Override
    public String blockType(WorldHandle world, Vector3Di position)
    {
        ensureOpen();
        return agentClient.blockType(world.name(), position);
    }

    @Override
    public void setBlock(WorldHandle world, Vector3Di position, String material)
    {
        ensureOpen();
        agentClient.setBlock(world.name(), position, material);
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
            return;

        try
        {
            agentClient.close();
        }
        finally
        {
            serverHandle.stop(SHUTDOWN_TIMEOUT);
        }
    }

    private void ensureOpen()
    {
        if (closed.get())
            throw new IllegalStateException("Framework is already closed.");
    }

    private static final class UdsAgentClient implements AutoCloseable
    {
        private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private final Path socketPath;
        private final AtomicLong requestCounter = new AtomicLong(0L);
        private SocketChannel socketChannel;
        private BufferedReader reader;
        private BufferedWriter writer;

        private UdsAgentClient(Path socketPath, Duration connectTimeout)
        {
            this.socketPath = socketPath;
            connect(connectTimeout);
        }

        void handshake(String token, String protocolVersion, String agentSha256)
        {
            send(AgentAction.HANDSHAKE, Map.of(
                "token", token,
                "protocolVersion", protocolVersion,
                "agentSha256", agentSha256
            ));
        }

        String mainWorld()
        {
            final AgentResponse response = send(AgentAction.MAIN_WORLD, Map.of());
            return getRequiredData(response, "worldName");
        }

        String newWorld(WorldSpec worldSpec)
        {
            final AgentResponse response = send(AgentAction.NEW_WORLD, Map.of(
                "worldName", worldSpec.name(),
                "worldType", worldSpec.worldType().name(),
                "environment", worldSpec.environment().name(),
                "seed", Long.toString(worldSpec.seed())
            ));
            return getRequiredData(response, "worldName");
        }

        boolean executeCommand(CommandSource source, String command)
        {
            final AgentResponse response = send(AgentAction.EXECUTE_COMMAND, Map.of(
                "source", source.name(),
                "command", command
            ));
            return Boolean.parseBoolean(getRequiredData(response, "success"));
        }

        String blockType(String worldName, Vector3Di position)
        {
            final AgentResponse response = send(AgentAction.BLOCK_TYPE, Map.of(
                "worldName", worldName,
                "x", Integer.toString(position.x()),
                "y", Integer.toString(position.y()),
                "z", Integer.toString(position.z())
            ));
            return getRequiredData(response, "material");
        }

        void setBlock(String worldName, Vector3Di position, String material)
        {
            send(AgentAction.SET_BLOCK, Map.of(
                "worldName", worldName,
                "x", Integer.toString(position.x()),
                "y", Integer.toString(position.y()),
                "z", Integer.toString(position.z()),
                "material", material
            ));
        }

        synchronized AgentResponse send(AgentAction action, Map<String, String> arguments)
        {
            final String requestId = Long.toString(requestCounter.incrementAndGet());
            final AgentRequest request = new AgentRequest(requestId, action, arguments);
            try
            {
                writer.write(objectMapper.writeValueAsString(request));
                writer.newLine();
                writer.flush();

                final String responseLine = reader.readLine();
                if (responseLine == null)
                    throw new IllegalStateException("Agent connection closed unexpectedly.");

                final AgentResponse response = objectMapper.readValue(responseLine, AgentResponse.class);
                if (!requestId.equals(response.requestId()))
                {
                    throw new IllegalStateException(
                        "Unexpected response id '%s' for request '%s'."
                            .formatted(response.requestId(), requestId)
                    );
                }

                if (!response.success())
                {
                    throw new IllegalStateException(
                        "Agent request failed. code=%s message=%s"
                            .formatted(response.errorCode(), response.errorMessage())
                    );
                }

                return response;
            }
            catch (IOException exception)
            {
                throw new IllegalStateException(
                    "Failed to communicate with agent via socket '%s'.".formatted(socketPath),
                    exception
                );
            }
        }

        @Override
        public synchronized void close()
        {
            try
            {
                if (socketChannel != null)
                    socketChannel.close();
            }
            catch (IOException ignored)
            {
            }
        }

        private void connect(Duration timeout)
        {
            final long deadline = System.nanoTime() + timeout.toNanos();
            Exception lastException = null;

            while (System.nanoTime() < deadline)
            {
                try
                {
                    final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(socketPath));
                    this.socketChannel = channel;
                    this.reader = new BufferedReader(
                        new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8)
                    );
                    this.writer = new BufferedWriter(
                        new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8)
                    );
                    return;
                }
                catch (Exception exception)
                {
                    lastException = exception;
                    sleep(100L);
                }
            }

            throw new IllegalStateException(
                "Failed to connect to agent socket '%s' within timeout %s."
                    .formatted(socketPath, timeout),
                lastException
            );
        }

        private static String getRequiredData(AgentResponse response, String key)
        {
            final String value = response.data().get(key);
            if (value == null)
                throw new IllegalStateException("Missing response field '%s' from agent.".formatted(key));
            return value;
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

    private static final class PaperServerHandle
    {
        private final RuntimeManifest runtimeManifest;
        private final Path diagnosticsDirectory;
        private final List<String> outputLines = new ArrayList<>();
        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private Process process;
        private Thread outputThread;

        private PaperServerHandle(RuntimeManifest runtimeManifest, Path diagnosticsDirectory)
        {
            this.runtimeManifest = runtimeManifest;
            this.diagnosticsDirectory = diagnosticsDirectory;
        }

        void start(Duration timeout)
        {
            final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            final Path serverDirectory = Path.of(runtimeManifest.serverDirectory());
            final Path serverJar = Path.of(runtimeManifest.serverJar());

            final ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable.toString(),
                "-Xmx1024M",
                "-Xms1024M",
                "-D" + RuntimeProtocol.PROPERTY_SOCKET_PATH + "=" + runtimeManifest.udsSocketPath(),
                "-D" + RuntimeProtocol.PROPERTY_AUTH_TOKEN + "=" + runtimeManifest.agentAuthToken(),
                "-D" + RuntimeProtocol.PROPERTY_PROTOCOL_VERSION + "=" + runtimeManifest.runtimeProtocolVersion(),
                "-D" + RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256 + "=" +
                    Objects.requireNonNullElse(runtimeManifest.agentJarSha256(), ""),
                "-jar",
                serverJar.toString(),
                "--nogui"
            );
            processBuilder.directory(serverDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            try
            {
                process = processBuilder.start();
                outputThread = createOutputReaderThread(process, outputLines, startedLatch);
                outputThread.start();
                final boolean started = startedLatch.await(timeout.toSeconds(), TimeUnit.SECONDS);
                if (!started)
                {
                    writeDiagnostics("startup-timeout");
                    stop(Duration.ofSeconds(5));
                    throw new IllegalStateException(
                        "Paper server did not start within timeout. Tail:%n%s".formatted(outputTail())
                    );
                }
            }
            catch (Exception exception)
            {
                writeDiagnostics("startup-failure");
                stop(Duration.ofSeconds(5));
                throw new IllegalStateException("Failed to start Paper server.", exception);
            }
        }

        void stop(Duration timeout)
        {
            if (process == null)
                return;

            try
            {
                if (process.isAlive())
                {
                    try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)))
                    {
                        writer.write("stop");
                        writer.newLine();
                        writer.flush();
                    }

                    final boolean stopped = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
                    if (!stopped && process.isAlive())
                        process.destroyForcibly();
                }
            }
            catch (Exception exception)
            {
                if (process.isAlive())
                    process.destroyForcibly();
                writeDiagnostics("shutdown-failure");
            }

            if (outputThread != null)
            {
                try
                {
                    outputThread.join(TimeUnit.SECONDS.toMillis(5));
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private String outputTail()
        {
            synchronized (outputLines)
            {
                final int startIndex = Math.max(0, outputLines.size() - 40);
                return String.join(System.lineSeparator(), outputLines.subList(startIndex, outputLines.size()));
            }
        }

        private void writeDiagnostics(String reason)
        {
            try
            {
                Files.createDirectories(diagnosticsDirectory);
                final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    .replace(":", "-");
                final Path bundleDirectory = diagnosticsDirectory.resolve("bundle-" + timestamp);
                Files.createDirectories(bundleDirectory);

                Files.writeString(bundleDirectory.resolve("reason.txt"), reason, StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("manifest-server-dir.txt"),
                    runtimeManifest.serverDirectory(), StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("manifest-socket-path.txt"),
                    runtimeManifest.udsSocketPath(), StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("manifest-protocol-version.txt"),
                    runtimeManifest.runtimeProtocolVersion(), StandardCharsets.UTF_8);
                Files.writeString(bundleDirectory.resolve("server-output.log"),
                    String.join(System.lineSeparator(), outputLines), StandardCharsets.UTF_8);
            }
            catch (IOException ignored)
            {
            }
        }

        private static Thread createOutputReaderThread(
            Process process,
            List<String> outputLines,
            CountDownLatch startedLatch)
        {
            return Thread.ofPlatform()
                .name("lightkeeper-paper-output-reader")
                .daemon(true)
                .unstarted(() -> {
                    try (BufferedReader reader =
                             new BufferedReader(
                                 new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                    {
                        String line;
                        while ((line = reader.readLine()) != null)
                        {
                            synchronized (outputLines)
                            {
                                outputLines.add(line);
                            }

                            if (line.contains("Done (") && line.endsWith(")! For help, type \"help\""))
                                startedLatch.countDown();
                        }
                    }
                    catch (IOException ignored)
                    {
                    }
                });
        }
    }
}

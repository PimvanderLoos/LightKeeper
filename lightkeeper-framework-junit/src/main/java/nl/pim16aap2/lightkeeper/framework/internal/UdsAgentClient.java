package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;
import nl.pim16aap2.lightkeeper.framework.CommandSource;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuSnapshot;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.jspecify.annotations.Nullable;

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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent RPC client backed by a Unix Domain Socket.
 */
@Log
final class UdsAgentClient implements AutoCloseable
{
    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path socketPath;
    private final AtomicLong requestCounter = new AtomicLong(0L);
    private SocketChannel socketChannel;
    private BufferedReader reader;
    private BufferedWriter writer;

    UdsAgentClient(Path socketPath, Duration connectTimeout)
    {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath may not be null.");
        connect(Objects.requireNonNull(connectTimeout, "connectTimeout may not be null."));
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

    AgentPlayerData createPlayer(
        String name,
        UUID uuid,
        String worldName,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nullable Double health,
        @Nullable Set<String> permissions)
    {
        final Map<String, String> arguments = new java.util.HashMap<>();
        arguments.put("name", name);
        arguments.put("uuid", uuid.toString());
        arguments.put("worldName", worldName);
        if (x != null && y != null && z != null)
        {
            arguments.put("x", Double.toString(x));
            arguments.put("y", Double.toString(y));
            arguments.put("z", Double.toString(z));
        }
        if (health != null)
            arguments.put("health", Double.toString(health));
        if (permissions != null && !permissions.isEmpty())
            arguments.put("permissions", String.join(",", permissions));

        final AgentResponse response = send(AgentAction.CREATE_PLAYER, arguments);
        final UUID createdUuid = UUID.fromString(getRequiredData(response, "uuid"));
        final String createdName = getRequiredData(response, "name");
        return new AgentPlayerData(createdUuid, createdName);
    }

    void removePlayer(UUID uuid)
    {
        send(AgentAction.REMOVE_PLAYER, Map.of(
            "uuid", uuid.toString()
        ));
    }

    void executePlayerCommand(UUID uuid, String command)
    {
        send(AgentAction.EXECUTE_PLAYER_COMMAND, Map.of(
            "uuid", uuid.toString(),
            "command", command
        ));
    }

    void placePlayerBlock(UUID uuid, String material, int x, int y, int z)
    {
        send(AgentAction.PLACE_PLAYER_BLOCK, Map.of(
            "uuid", uuid.toString(),
            "material", material,
            "x", Integer.toString(x),
            "y", Integer.toString(y),
            "z", Integer.toString(z)
        ));
    }

    MenuSnapshot menuSnapshot(UUID uuid)
    {
        final AgentResponse response = send(AgentAction.GET_OPEN_MENU, Map.of(
            "uuid", uuid.toString()
        ));
        final boolean open = Boolean.parseBoolean(getRequiredData(response, "open"));
        if (!open)
            return new MenuSnapshot(false, "", List.of());

        final String title = getRequiredData(response, "title");
        final String itemsJson = response.data().getOrDefault("itemsJson", "[]");
        try
        {
            final MenuItemSnapshot[] items = objectMapper.readValue(itemsJson, MenuItemSnapshot[].class);
            return new MenuSnapshot(true, title, List.of(items));
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to parse menu snapshot JSON.", exception);
        }
    }

    void clickMenuSlot(UUID uuid, int slot)
    {
        send(AgentAction.CLICK_MENU_SLOT, Map.of(
            "uuid", uuid.toString(),
            "slot", Integer.toString(slot)
        ));
    }

    void dragMenuSlots(UUID uuid, String materialKey, int... slots)
    {
        final String slotList = Arrays.stream(slots)
            .mapToObj(Integer::toString)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        send(AgentAction.DRAG_MENU_SLOTS, Map.of(
            "uuid", uuid.toString(),
            "material", materialKey,
            "slots", slotList
        ));
    }

    void waitTicks(int ticks)
    {
        send(AgentAction.WAIT_TICKS, Map.of(
            "ticks", Integer.toString(ticks)
        ));
    }

    List<String> playerMessages(UUID uuid)
    {
        final AgentResponse response = send(AgentAction.GET_PLAYER_MESSAGES, Map.of(
            "uuid", uuid.toString()
        ));
        final String messagesJson = response.data().getOrDefault("messagesJson", "[]");
        try
        {
            final String[] messages = objectMapper.readValue(messagesJson, String[].class);
            return List.of(messages);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to parse player messages JSON.", exception);
        }
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
            log.fine(() -> "Failed to close agent socket channel cleanly.");
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

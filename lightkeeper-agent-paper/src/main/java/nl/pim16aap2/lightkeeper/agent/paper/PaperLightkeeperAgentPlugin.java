package nl.pim16aap2.lightkeeper.agent.paper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import nl.pim16aap2.lightkeeper.nms.v121r6.BotPlayerNmsAdapterV1_21_R6;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentRequest;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paper plugin that exposes a UDS control channel for LightKeeper tests.
 */
public final class PaperLightkeeperAgentPlugin extends JavaPlugin implements Listener
{
    private static final long SYNC_OPERATION_TIMEOUT_SECONDS = 30L;
    private static final long WAIT_TICKS_TIMEOUT_MILLIS = 60_000L;
    static final String SUPPORTED_MINECRAFT_VERSION = "1.21.11";
    static final List<String> SUPPORTED_NMS_REVISIONS = List.of("v1_21_R6", "v1_21_R7");
    static final String CRAFTBUKKIT_PACKAGE_PREFIX = "org.bukkit.craftbukkit.";
    private static final String MAIN_MENU_TITLE = "Main Menu";
    private static final String SUB_MENU_TITLE = "Sub Menu";
    private static final System.Logger LOGGER = System.getLogger(PaperLightkeeperAgentPlugin.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<UUID, Player> syntheticPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerMessageHistory = new ConcurrentHashMap<>();
    private final AtomicLong tickCounter = new AtomicLong(0L);

    private @Nullable IBotPlayerNmsAdapter botPlayerNmsAdapter;

    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("lightkeeper-agent-accept-", 0).factory()
    );
    private final ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean running;
    private Path socketPath = Path.of("lightkeeper-agent.sock");
    private String authToken = "";
    private String protocolVersion = RuntimeProtocol.VERSION;
    private String expectedAgentSha256 = "";
    private @Nullable ServerSocketChannel serverSocketChannel;

    /**
     * Initializes agent configuration, event listeners, and socket server.
     */
    @Override
    public void onEnable()
    {
        try
        {
            initializeConfiguration();
            validateNmsCompatibility();
            botPlayerNmsAdapter = new BotPlayerNmsAdapterV1_21_R6();
            Bukkit.getPluginManager().registerEvents(this, this);
            startTickLoop();
            startServer();
        }
        catch (Exception exception)
        {
            getLogger().severe("Failed to start LightKeeper Paper agent: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Stops the socket server and cleans up synthetic player state.
     */
    @Override
    public void onDisable()
    {
        running = false;
        closeQuietly(serverSocketChannel);
        cleanupSyntheticPlayers();
        requestExecutor.shutdownNow();
        acceptExecutor.shutdownNow();

        if (socketPath != null)
        {
            try
            {
                Files.deleteIfExists(socketPath);
            }
            catch (IOException exception)
            {
                getLogger().warning("Failed to delete agent socket path '" + socketPath + "'.");
            }
        }
    }

    /**
     * Handles plugin commands used by integration tests.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
            return false;

        if (!command.getName().equalsIgnoreCase("lktestgui"))
            return false;

        openMainMenu(player);
        return true;
    }

    /**
     * Handles menu click interactions for built-in test menus.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event)
    {
        final InventoryView view = event.getView();
        final String title = view.getTitle();
        if (!MAIN_MENU_TITLE.equals(title) && !SUB_MENU_TITLE.equals(title))
            return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (MAIN_MENU_TITLE.equals(title) && event.getRawSlot() == 0)
        {
            sendTrackedMessage(player, "You clicked Button 1");
            openSubMenu(player);
            return;
        }

        if (SUB_MENU_TITLE.equals(title) && event.getRawSlot() == 0)
        {
            openMainMenu(player);
            return;
        }

        if (SUB_MENU_TITLE.equals(title) && event.getRawSlot() == 2)
            player.closeInventory();
    }

    private void initializeConfiguration()
    {
        final String configuredSocketPath = requireNonBlankProperty(RuntimeProtocol.PROPERTY_SOCKET_PATH);
        socketPath = Path.of(configuredSocketPath).toAbsolutePath();
        authToken = requireNonBlankProperty(RuntimeProtocol.PROPERTY_AUTH_TOKEN);
        protocolVersion = requireNonBlankProperty(RuntimeProtocol.PROPERTY_PROTOCOL_VERSION);
        expectedAgentSha256 = System.getProperty(RuntimeProtocol.PROPERTY_EXPECTED_AGENT_SHA256, "");
    }

    private void validateNmsCompatibility()
    {
        final String bukkitVersion = Bukkit.getBukkitVersion();
        if (!bukkitVersion.equals(SUPPORTED_MINECRAFT_VERSION)
            && !bukkitVersion.startsWith(SUPPORTED_MINECRAFT_VERSION + "-"))
        {
            throw new IllegalStateException(
                "Unsupported Bukkit version '%s'. This agent only supports Minecraft version '%s'."
                    .formatted(bukkitVersion, SUPPORTED_MINECRAFT_VERSION)
            );
        }

        final @Nullable String detectedNmsRevision = extractCraftBukkitRevision(
            Bukkit.getServer().getClass().getPackageName()
        );
        if (detectedNmsRevision != null && !SUPPORTED_NMS_REVISIONS.contains(detectedNmsRevision))
        {
            throw new IllegalStateException(
                "Unsupported server NMS revision '%s'. Supported revisions: %s (Bukkit version: %s)."
                    .formatted(detectedNmsRevision, String.join(", ", SUPPORTED_NMS_REVISIONS), bukkitVersion)
            );
        }
    }

    static @Nullable String extractCraftBukkitRevision(String packageName)
    {
        final String normalizedPackageName = packageName == null ? "" : packageName.trim();
        if ("org.bukkit.craftbukkit".equals(normalizedPackageName))
            return null;
        if (!normalizedPackageName.startsWith(CRAFTBUKKIT_PACKAGE_PREFIX))
        {
            throw new IllegalStateException(
                "Unexpected CraftBukkit package '%s'. Expected prefix '%s'."
                    .formatted(normalizedPackageName, CRAFTBUKKIT_PACKAGE_PREFIX)
            );
        }

        final int separatorIndex = normalizedPackageName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == normalizedPackageName.length() - 1)
        {
            throw new IllegalStateException(
                "Unable to resolve CraftBukkit NMS revision from package '%s'."
                    .formatted(normalizedPackageName)
            );
        }

        final String revision = normalizedPackageName.substring(separatorIndex + 1);
        if (revision.equals("craftbukkit"))
            return null;
        return revision;
    }

    private void startTickLoop()
    {
        Bukkit.getScheduler().runTaskTimer(this, () -> tickCounter.incrementAndGet(), 1L, 1L);
    }

    private void startServer()
        throws IOException
    {
        if (socketPath.getParent() != null)
            Files.createDirectories(socketPath.getParent());
        Files.deleteIfExists(socketPath);

        serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverSocketChannel.bind(UnixDomainSocketAddress.of(socketPath));

        running = true;
        acceptExecutor.execute(this::acceptLoop);
        getLogger().info("LightKeeper agent started at socket path: " + socketPath);
    }

    private void acceptLoop()
    {
        while (running)
        {
            try
            {
                final ServerSocketChannel localServerSocketChannel = serverSocketChannel;
                if (localServerSocketChannel == null)
                {
                    if (running)
                        getLogger().warning(
                            "Agent accept loop is running without an initialized server socket channel."
                        );
                    return;
                }
                final SocketChannel socketChannel = localServerSocketChannel.accept();
                requestExecutor.execute(() -> handleConnection(socketChannel));
            }
            catch (IOException exception)
            {
                if (running)
                    getLogger().warning("Agent accept loop failed: " + exception.getMessage());
            }
        }
    }

    private void handleConnection(SocketChannel socketChannel)
    {
        try (
            socketChannel;
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(socketChannel), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Channels.newOutputStream(socketChannel), StandardCharsets.UTF_8))
        )
        {
            boolean handshakeCompleted = false;
            String line;
            while ((line = reader.readLine()) != null)
            {
                final RequestDispatchResult dispatchResult = handleRequestLine(line, handshakeCompleted);
                handshakeCompleted = dispatchResult.handshakeCompleted();
                final AgentResponse response = dispatchResult.response();
                writer.write(objectMapper.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
        catch (IOException exception)
        {
            if (running)
                getLogger().warning("Agent connection failed: " + exception.getMessage());
        }
    }

    private RequestDispatchResult handleRequestLine(String line, boolean handshakeCompleted)
    {
        try
        {
            final AgentRequest request = objectMapper.readValue(line, AgentRequest.class);
            return dispatchRequest(request, handshakeCompleted);
        }
        catch (Exception exception)
        {
            return new RequestDispatchResult(
                errorResponse(
                    "unknown",
                    "INVALID_REQUEST",
                    "Failed to parse request: " + exception.getMessage()
                ),
                handshakeCompleted
            );
        }
    }

    private RequestDispatchResult dispatchRequest(AgentRequest request, boolean handshakeCompleted)
    {
        final Map<String, String> arguments = request.arguments() == null ? Map.of() : request.arguments();
        final String requestId = Objects.requireNonNullElse(request.requestId(), "unknown");

        try
        {
            if (request.action() == AgentAction.HANDSHAKE)
            {
                final AgentResponse handshakeResponse = handleHandshake(requestId, arguments);
                return new RequestDispatchResult(handshakeResponse, handshakeCompleted || handshakeResponse.success());
            }

            if (!handshakeCompleted)
            {
                return new RequestDispatchResult(
                    errorResponse(
                        requestId,
                        "HANDSHAKE_REQUIRED",
                        "A successful HANDSHAKE action is required before '%s'."
                            .formatted(String.valueOf(request.action()))
                    ),
                    false
                );
            }

            return new RequestDispatchResult(switch (request.action())
            {
                case MAIN_WORLD -> handleMainWorld(requestId);
                case NEW_WORLD -> handleNewWorld(requestId, arguments);
                case EXECUTE_COMMAND -> handleExecuteCommand(requestId, arguments);
                case BLOCK_TYPE -> handleBlockType(requestId, arguments);
                case SET_BLOCK -> handleSetBlock(requestId, arguments);
                case CREATE_PLAYER -> handleCreatePlayer(requestId, arguments);
                case REMOVE_PLAYER -> handleRemovePlayer(requestId, arguments);
                case EXECUTE_PLAYER_COMMAND -> handleExecutePlayerCommand(requestId, arguments);
                case PLACE_PLAYER_BLOCK -> handlePlacePlayerBlock(requestId, arguments);
                case GET_OPEN_MENU -> handleGetOpenMenu(requestId, arguments);
                case CLICK_MENU_SLOT -> handleClickMenuSlot(requestId, arguments);
                case DRAG_MENU_SLOTS -> handleDragMenuSlots(requestId, arguments);
                case GET_PLAYER_MESSAGES -> handleGetPlayerMessages(requestId, arguments);
                case WAIT_TICKS -> handleWaitTicks(requestId, arguments);
                case GET_SERVER_TICK -> handleGetServerTick(requestId);
                case HANDSHAKE -> throw new IllegalStateException("Unreachable HANDSHAKE dispatch branch.");
            }, true);
        }
        catch (Exception exception)
        {
            getLogger().severe(
                "Agent action '%s' failed for request '%s': %s"
                    .formatted(
                        request.action(),
                        requestId,
                        Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                    )
            );
            getLogger().severe("Agent failure stack trace:");
            exception.printStackTrace();
            return new RequestDispatchResult(
                errorResponse(
                    requestId,
                    "REQUEST_FAILED",
                    Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                ),
                handshakeCompleted
            );
        }
    }

    private record RequestDispatchResult(AgentResponse response, boolean handshakeCompleted)
    {
    }

    private AgentResponse handleHandshake(String requestId, Map<String, String> arguments)
    {
        final String token = arguments.getOrDefault("token", "");
        final String clientProtocolVersion = arguments.getOrDefault("protocolVersion", "");
        final String clientAgentSha = arguments.getOrDefault("agentSha256", "");

        if (!authToken.equals(token))
            return errorResponse(requestId, "AUTH_FAILED", "Auth token mismatch.");
        if (!protocolVersion.equals(clientProtocolVersion))
            return errorResponse(requestId, "PROTOCOL_MISMATCH", "Runtime protocol version mismatch.");
        if (!expectedAgentSha256.isBlank() && !expectedAgentSha256.equalsIgnoreCase(clientAgentSha))
            return errorResponse(requestId, "AGENT_SHA_MISMATCH", "Agent SHA-256 mismatch.");

        return successResponse(requestId, Map.of(
            "protocolVersion", protocolVersion,
            "bukkitVersion", Bukkit.getBukkitVersion()
        ));
    }

    private AgentResponse handleMainWorld(String requestId)
        throws Exception
    {
        final World mainWorld = callOnMainThread(() -> Bukkit.getWorlds().getFirst());
        return successResponse(requestId, Map.of("worldName", mainWorld.getName()));
    }

    private AgentResponse handleNewWorld(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "").trim();
        if (worldName.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'worldName' must not be blank.");

        final String worldTypeValue = arguments.getOrDefault("worldType", "NORMAL");
        final String environmentValue = arguments.getOrDefault("environment", "NORMAL");
        final long seed = parseLong(arguments.getOrDefault("seed", "0"));

        final World world = callOnMainThread(() ->
        {
            final WorldCreator worldCreator = new WorldCreator(worldName);
            worldCreator.type(WorldType.valueOf(worldTypeValue.toUpperCase(Locale.ROOT)));
            worldCreator.environment(World.Environment.valueOf(environmentValue.toUpperCase(Locale.ROOT)));
            worldCreator.seed(seed);
            return worldCreator.createWorld();
        });

        if (world == null)
            return errorResponse(requestId, "WORLD_CREATE_FAILED", "Failed to create world '%s'.".formatted(worldName));

        getLogger().info("LK_AGENT: Created/loaded world '%s' (type=%s, environment=%s, seed=%d)."
            .formatted(world.getName(), worldTypeValue, environmentValue, seed));
        return successResponse(requestId, Map.of("worldName", world.getName()));
    }

    private AgentResponse handleExecuteCommand(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String source = arguments.getOrDefault("source", "CONSOLE");
        final String rawCommand = arguments.getOrDefault("command", "");
        if (rawCommand.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'command' must not be blank.");

        if (!source.equalsIgnoreCase("CONSOLE"))
            return errorResponse(requestId, "UNSUPPORTED_SOURCE", "Only CONSOLE command source is supported in v1.");

        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = callOnMainThread(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));

        return successResponse(requestId, Map.of("success", success.toString()));
    }

    private AgentResponse handleBlockType(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "");
        final int x = parseInt(arguments.getOrDefault("x", "0"));
        final int y = parseInt(arguments.getOrDefault("y", "0"));
        final int z = parseInt(arguments.getOrDefault("z", "0"));

        final String materialName = callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            return world.getBlockAt(x, y, z).getType().name();
        });

        return successResponse(requestId, Map.of("material", materialName));
    }

    private AgentResponse handleSetBlock(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String worldName = arguments.getOrDefault("worldName", "");
        final int x = parseInt(arguments.getOrDefault("x", "0"));
        final int y = parseInt(arguments.getOrDefault("y", "0"));
        final int z = parseInt(arguments.getOrDefault("z", "0"));
        final String materialName = arguments.getOrDefault("material", "");

        if (materialName.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'material' must not be blank.");

        final Material material = parseMaterial(materialName);
        if (material == null)
            return errorResponse(requestId, "INVALID_ARGUMENT", "Unknown material '%s'.".formatted(materialName));

        final String setMaterial = callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().name();
        });

        return successResponse(requestId, Map.of("material", setMaterial));
    }

    private AgentResponse handleCreatePlayer(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final String name = arguments.getOrDefault("name", "").trim();
        final String worldName = arguments.getOrDefault("worldName", "").trim();
        if (name.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'name' must not be blank.");
        if (worldName.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'worldName' must not be blank.");

        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", UUID.randomUUID().toString()));
        final Double x = parseOptionalDouble(arguments.get("x"));
        final Double y = parseOptionalDouble(arguments.get("y"));
        final Double z = parseOptionalDouble(arguments.get("z"));
        final Double health = parseOptionalDouble(arguments.get("health"));
        final String permissionsCsv = arguments.getOrDefault("permissions", "");

        final Player player = callOnMainThread(() ->
        {
            final World world = Bukkit.getWorld(worldName);
            if (world == null)
                throw new IllegalArgumentException("World '%s' does not exist.".formatted(worldName));

            final Location spawnLocation = x == null || y == null || z == null
                ? world.getSpawnLocation()
                : new Location(world, x, y, z);
            final Player spawnedPlayer = requireBotPlayerNmsAdapter().spawnPlayer(uuid, name, world, spawnLocation);
            if (health != null)
                spawnedPlayer.setHealth(Math.min(spawnedPlayer.getMaxHealth(), health));

            if (!permissionsCsv.isBlank())
            {
                final PermissionAttachment attachment = spawnedPlayer.addAttachment(this);
                Arrays.stream(permissionsCsv.split(","))
                    .map(String::trim)
                    .filter(permission -> !permission.isEmpty())
                    .forEach(permission -> attachment.setPermission(permission, true));
                permissionAttachments.put(uuid, attachment);
            }

            return spawnedPlayer;
        });

        syntheticPlayers.put(uuid, player);
        playerMessageHistory.put(uuid, new CopyOnWriteArrayList<>());
        getLogger().info("LK_AGENT: Created synthetic player '%s' (%s) in world '%s'."
            .formatted(player.getName(), player.getUniqueId(), worldName));
        return successResponse(requestId, Map.of(
            "uuid", player.getUniqueId().toString(),
            "name", player.getName()
        ));
    }

    private AgentResponse handleRemovePlayer(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        callOnMainThread(() ->
        {
            final Player player = getRequiredPlayer(uuid);
            final PermissionAttachment attachment = permissionAttachments.remove(uuid);
            if (attachment != null)
                player.removeAttachment(attachment);
            requireBotPlayerNmsAdapter().removePlayer(player);
            syntheticPlayers.remove(uuid);
            playerMessageHistory.remove(uuid);
            getLogger().info("LK_AGENT: Removed synthetic player '%s' (%s)."
                .formatted(player.getName(), player.getUniqueId()));
            return Boolean.TRUE;
        });
        return successResponse(requestId, Map.of("removed", "true"));
    }

    private AgentResponse handleExecutePlayerCommand(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String rawCommand = arguments.getOrDefault("command", "").trim();
        if (rawCommand.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'command' must not be blank.");

        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        final Boolean success = callOnMainThread(() ->
        {
            final Player player = getRequiredPlayer(uuid);
            if (command.equalsIgnoreCase("lktestgui") || command.equalsIgnoreCase("lightkeeper:testgui"))
            {
                openMainMenu(player);
                return true;
            }

            boolean result = player.performCommand(command);
            if (!result)
                result = Bukkit.dispatchCommand(player, command);
            return result;
        });
        return successResponse(requestId, Map.of("success", success.toString()));
    }

    private AgentResponse handlePlacePlayerBlock(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String materialName = arguments.getOrDefault("material", "");
        final int x = parseInt(arguments.getOrDefault("x", "0"));
        final int y = parseInt(arguments.getOrDefault("y", "0"));
        final int z = parseInt(arguments.getOrDefault("z", "0"));
        final Material material = parseMaterial(materialName);
        if (material == null)
            return errorResponse(requestId, "INVALID_ARGUMENT", "Unknown material '%s'.".formatted(materialName));

        final String finalMaterial = callOnMainThread(() ->
        {
            final Player player = getRequiredPlayer(uuid);
            final World world = player.getWorld();
            world.getBlockAt(x, y, z).setType(material);
            return world.getBlockAt(x, y, z).getType().getKey().toString();
        });
        return successResponse(requestId, Map.of("material", finalMaterial));
    }

    private AgentResponse handleGetOpenMenu(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final Map<String, String> data = callOnMainThread(() ->
        {
            final Player player = getRequiredPlayer(uuid);
            final InventoryView view = player.getOpenInventory();

            if (!isActionableOpenInventory(view))
                return Map.of("open", "false");

            final List<Map<String, Object>> items = new ArrayList<>();
            for (int rawSlot = 0; rawSlot < view.countSlots(); ++rawSlot)
            {
                final ItemStack item = view.getItem(rawSlot);
                if (item == null || item.getType().isAir())
                    continue;

                final Map<String, Object> itemData = new HashMap<>();
                itemData.put("slot", rawSlot);
                itemData.put("materialKey", item.getType().getKey().toString());
                itemData.put("displayName", item.getItemMeta() == null ? null : item.getItemMeta().getDisplayName());
                itemData.put(
                    "lore",
                    item.getItemMeta() == null
                        ? List.of()
                        : Objects.requireNonNullElse(item.getItemMeta().getLore(), List.of())
                );
                items.add(itemData);
            }

            return Map.of(
                "open", "true",
                "title", view.getTitle(),
                "itemsJson", objectMapper.writeValueAsString(items)
            );
        });

        return successResponse(requestId, data);
    }

    private AgentResponse handleClickMenuSlot(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final int slot = parseInt(arguments.getOrDefault("slot", "-1"));
        if (slot < 0)
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'slot' must be >= 0.");

        callOnMainThread(() ->
        {
            final Player player = getRequiredPlayer(uuid);
            final InventoryView view = player.getOpenInventory();
            if (!isActionableOpenInventory(view))
                throw new IllegalStateException("Player does not have an actionable open menu.");

            final InventoryClickEvent event = new InventoryClickEvent(
                view,
                view.getSlotType(slot),
                slot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
            );
            Bukkit.getPluginManager().callEvent(event);
            return Boolean.TRUE;
        });
        return successResponse(requestId, Map.of("clicked", "true"));
    }

    private AgentResponse handleDragMenuSlots(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String materialName = arguments.getOrDefault("material", "").trim();
        final Material material = parseMaterial(materialName);
        if (material == null)
            return errorResponse(requestId, "INVALID_ARGUMENT", "Unknown material '%s'.".formatted(materialName));

        final String slots = arguments.getOrDefault("slots", "").trim();
        if (slots.isBlank())
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'slots' must not be blank.");
        final List<Integer> rawSlots = Arrays.stream(slots.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(Integer::parseInt)
            .toList();

        callOnMainThread(() ->
        {
            final Player player = getRequiredPlayer(uuid);
            final InventoryView view = player.getOpenInventory();
            if (!isActionableOpenInventory(view))
                throw new IllegalStateException("Player does not have an actionable open menu.");

            final ItemStack cursorItem = new ItemStack(material);
            final Map<Integer, ItemStack> newItems = new HashMap<>();
            for (final Integer rawSlot : rawSlots)
                newItems.put(rawSlot, cursorItem.clone());

            final InventoryDragEvent event = new InventoryDragEvent(
                view,
                cursorItem.clone(),
                cursorItem,
                true,
                newItems
            );
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled())
            {
                for (final Map.Entry<Integer, ItemStack> entry : newItems.entrySet())
                    view.setItem(entry.getKey(), entry.getValue());
            }
            return Boolean.TRUE;
        });

        return successResponse(requestId, Map.of("dragged", "true"));
    }

    private AgentResponse handleWaitTicks(String requestId, Map<String, String> arguments)
    {
        final int ticks = parseInt(arguments.getOrDefault("ticks", "0"));
        if (ticks < 0)
            return errorResponse(requestId, "INVALID_ARGUMENT", "Argument 'ticks' must be >= 0.");

        final long startTick = tickCounter.get();
        final long targetTick = startTick + ticks;
        final long deadline = System.currentTimeMillis() + WAIT_TICKS_TIMEOUT_MILLIS;
        while (tickCounter.get() < targetTick)
        {
            if (System.currentTimeMillis() >= deadline)
            {
                return errorResponse(
                    requestId,
                    "TIMEOUT",
                    "Timed out waiting for %d ticks. start=%d current=%d target=%d"
                        .formatted(ticks, startTick, tickCounter.get(), targetTick)
                );
            }

            try
            {
                Thread.sleep(10L);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return errorResponse(requestId, "INTERRUPTED", "Interrupted while waiting for ticks.");
            }
        }
        return successResponse(requestId, Map.of(
            "startTick", Long.toString(startTick),
            "endTick", Long.toString(tickCounter.get())
        ));
    }

    private AgentResponse handleGetPlayerMessages(String requestId, Map<String, String> arguments)
        throws Exception
    {
        final UUID uuid = UUID.fromString(arguments.getOrDefault("uuid", ""));
        final String messagesJson = callOnMainThread(() ->
        {
            getRequiredPlayer(uuid);
            capturePlayerMessages(uuid);
            final List<String> messages = playerMessageHistory.getOrDefault(uuid, List.of());
            return objectMapper.writeValueAsString(messages);
        });
        return successResponse(requestId, Map.of("messagesJson", messagesJson));
    }

    private AgentResponse handleGetServerTick(String requestId)
    {
        return successResponse(requestId, Map.of(
            "tick", Long.toString(tickCounter.get())
        ));
    }

    private void cleanupSyntheticPlayers()
    {
        final @Nullable IBotPlayerNmsAdapter nmsAdapter = botPlayerNmsAdapter;
        if (nmsAdapter == null)
            return;

        for (final UUID uuid : Set.copyOf(syntheticPlayers.keySet()))
        {
            try
            {
                final Player player = syntheticPlayers.remove(uuid);
                if (player == null)
                    continue;
                final PermissionAttachment attachment = permissionAttachments.remove(uuid);
                if (attachment != null)
                    player.removeAttachment(attachment);
                nmsAdapter.removePlayer(player);
                playerMessageHistory.remove(uuid);
            }
            catch (Exception exception)
            {
                getLogger().warning(
                    "Failed to cleanup synthetic player '%s': %s"
                        .formatted(
                            uuid,
                            Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getName())
                        )
                );
            }
        }
    }

    private void openMainMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, MAIN_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.STONE));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }

    private void openSubMenu(Player player)
    {
        final Inventory inventory = Bukkit.createInventory(player, 9, SUB_MENU_TITLE);
        inventory.setItem(0, new ItemStack(Material.BARRIER));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        player.openInventory(inventory);
    }

    private Player getRequiredPlayer(UUID uuid)
    {
        final Player player = syntheticPlayers.get(uuid);
        if (player == null)
            throw new IllegalArgumentException("Synthetic player '%s' is not registered.".formatted(uuid));
        return player;
    }

    private void sendTrackedMessage(Player player, String message)
    {
        player.sendMessage(message);
        playerMessageHistory
            .computeIfAbsent(player.getUniqueId(), ignored -> new CopyOnWriteArrayList<>())
            .add(message);
    }

    private void capturePlayerMessages(UUID uuid)
    {
        final List<String> drainedMessages = requireBotPlayerNmsAdapter().drainReceivedMessages(uuid);
        if (drainedMessages.isEmpty())
            return;
        playerMessageHistory
            .computeIfAbsent(uuid, ignored -> new CopyOnWriteArrayList<>())
            .addAll(drainedMessages);
    }

    private static boolean isActionableOpenInventory(InventoryView view)
    {
        return view != null && view.getTopInventory() != null && view.getType() != InventoryType.CRAFTING;
    }

    private <T> T callOnMainThread(java.util.concurrent.Callable<T> callable)
        throws Exception
    {
        return Bukkit.getScheduler()
            .callSyncMethod(this, callable)
            .get(SYNC_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static int parseInt(String value)
    {
        return Integer.parseInt(value.trim());
    }

    private static long parseLong(String value)
    {
        return Long.parseLong(value.trim());
    }

    private static @Nullable Double parseOptionalDouble(@Nullable String value)
    {
        if (value == null || value.isBlank())
            return null;
        return Double.parseDouble(value.trim());
    }

    private static @Nullable Material parseMaterial(@Nullable String materialName)
    {
        final String trimmed = materialName == null ? "" : materialName.trim();
        if (trimmed.isEmpty())
            return null;
        final Material directMatch = Material.matchMaterial(trimmed, true);
        if (directMatch != null)
            return directMatch;
        final String normalized = trimmed.startsWith("minecraft:") ? trimmed.substring("minecraft:".length()) : trimmed;
        return Material.matchMaterial(normalized.toUpperCase(Locale.ROOT), true);
    }

    private IBotPlayerNmsAdapter requireBotPlayerNmsAdapter()
    {
        final @Nullable IBotPlayerNmsAdapter nmsAdapter = botPlayerNmsAdapter;
        if (nmsAdapter == null)
            throw new IllegalStateException("NMS adapter is not initialized.");
        return nmsAdapter;
    }

    private String requireNonBlankProperty(String key)
    {
        final String value = System.getProperty(key, "");
        if (value.isBlank())
            throw new IllegalStateException("Required system property '%s' is missing.".formatted(key));
        return value;
    }

    private static AgentResponse successResponse(String requestId, Map<String, String> data)
    {
        return new AgentResponse(requestId, true, null, null, data);
    }

    private static AgentResponse errorResponse(String requestId, String errorCode, String message)
    {
        return new AgentResponse(requestId, false, errorCode, message, Map.of());
    }

    private static void closeQuietly(@Nullable ServerSocketChannel channel)
    {
        if (channel == null)
            return;

        try
        {
            channel.close();
        }
        catch (IOException exception)
        {
            LOGGER.log(
                System.Logger.Level.WARNING,
                "Failed to close server socket channel cleanly.",
                exception
            );
        }
    }
}

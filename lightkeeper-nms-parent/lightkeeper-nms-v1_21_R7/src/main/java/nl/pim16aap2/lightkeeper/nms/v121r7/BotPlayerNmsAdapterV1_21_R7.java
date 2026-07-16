package nl.pim16aap2.lightkeeper.nms.v121r7;

import nl.pim16aap2.lightkeeper.nms.api.IBotLoginDriver;
import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Paper 1.21.11 (v1_21_R7) synthetic-player adapter.
 */
public final class BotPlayerNmsAdapterV1_21_R7 implements IBotPlayerNmsAdapter
{
    private static final System.Logger LOG = System.getLogger(BotPlayerNmsAdapterV1_21_R7.class.getName());

    private static final int TEXT_EXTRACTION_MAX_DEPTH = NmsValueExtractor.MAX_DEPTH;

    private final Object minecraftServer;
    private final Object playerList;
    private final Map<UUID, Object> playerChannels = new ConcurrentHashMap<>();
    private final Map<UUID, Queue<String>> playerMessageQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Queue<String>> playerChatComponentQueues = new ConcurrentHashMap<>();

    private final Constructor<?> gameProfileConstructor;
    private final Constructor<?> connectionConstructor;
    private final Constructor<?> embeddedChannelConstructor;
    private final Constructor<?> serverPlayerConstructor;

    private final Method craftWorldGetHandleMethod;
    private final Method commonListenerCreateInitialMethod;
    private final Method playerListPlaceNewPlayerMethod;
    private final Method playerListRemoveMethod;
    private final Method serverPlayerGetBukkitEntityMethod;
    private final Method craftPlayerGetHandleMethod;
    private final Method clientInformationCreateDefaultMethod;
    private final Field connectionChannelField;
    private final Field connectionAddressField;
    private final Method embeddedChannelReadOutboundMethod;
    private final Object componentJsonCodec;
    private final Object componentJsonOps;
    private final Method componentCodecEncodeStartMethod;
    private final Method dataResultResultMethod;
    private final Method dataResultErrorMethod;
    private final Object serverboundPacketFlow;

    /**
     * Full-login driver, resolved lazily so the (larger) login reflection surface is only touched when a
     * {@code FULL_LOGIN} join is actually requested; legacy-spawn-only usage never pays for it.
     */
    private volatile @Nullable IBotLoginDriver botLoginDriver;

    /**
     * Initializes reflective handles for the Paper 1.21.11 server internals.
     */
    public BotPlayerNmsAdapterV1_21_R7()
    {
        try
        {
            final ClassLoader serverClassLoader = Bukkit.getServer().getClass().getClassLoader();
            final Object craftServer = Bukkit.getServer();
            final Class<?> craftServerClass = craftServer.getClass();
            final String craftBukkitPackage = craftServerClass.getPackageName();
            final Method craftServerGetServerMethod = craftServerClass.getMethod("getServer");
            minecraftServer = craftServerGetServerMethod.invoke(craftServer);
            playerList = resolvePlayerList(minecraftServer, serverClassLoader);

            final Class<?> componentSerializationClass = NmsReflectionUtils.resolveClass(
                "net.minecraft.network.chat.ComponentSerialization",
                serverClassLoader
            );
            final Class<?> codecClass =
                NmsReflectionUtils.resolveClass("com.mojang.serialization.Codec", serverClassLoader);
            final Class<?> dataResultClass =
                NmsReflectionUtils.resolveClass("com.mojang.serialization.DataResult", serverClassLoader);
            final Class<?> dynamicOpsClass =
                NmsReflectionUtils.resolveClass("com.mojang.serialization.DynamicOps", serverClassLoader);
            final Class<?> jsonOpsClass =
                NmsReflectionUtils.resolveClass("com.mojang.serialization.JsonOps", serverClassLoader);
            componentJsonCodec = resolveComponentJsonCodec(componentSerializationClass, codecClass);
            componentJsonOps = jsonOpsClass.getField("INSTANCE").get(null);
            componentCodecEncodeStartMethod = codecClass.getMethod("encodeStart", dynamicOpsClass, Object.class);
            dataResultResultMethod = dataResultClass.getMethod("result");
            dataResultErrorMethod = dataResultClass.getMethod("error");

            final Class<?> gameProfileClass = NmsReflectionUtils.resolveClass(
                "com.mojang.authlib.GameProfile",
                serverClassLoader
            );
            final Class<?> packetFlowClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.protocol.PacketFlow",
                "net.minecraft.network.protocol.EnumProtocolDirection"
            );
            final Class<?> connectionClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.Connection",
                "net.minecraft.network.NetworkManager"
            );
            final Class<?> serverPlayerClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.server.level.ServerPlayer",
                "net.minecraft.server.level.EntityPlayer"
            );
            final Class<?> commonListenerCookieClass = NmsReflectionUtils.resolveClass(
                "net.minecraft.server.network.CommonListenerCookie",
                serverClassLoader
            );

            final Class<?> minecraftServerClass = NmsReflectionUtils.resolveClass(
                "net.minecraft.server.MinecraftServer",
                serverClassLoader
            );
            final Class<?> serverLevelClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.server.level.ServerLevel",
                "net.minecraft.server.level.WorldServer"
            );
            final Class<?> clientInformationClass = NmsReflectionUtils.resolveClass(
                "net.minecraft.server.level.ClientInformation",
                serverClassLoader
            );
            final Class<?> embeddedChannelClass = NmsReflectionUtils.resolveClass(
                "io.netty.channel.embedded.EmbeddedChannel",
                serverClassLoader
            );

            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            connectionConstructor = connectionClass.getConstructor(packetFlowClass);
            embeddedChannelConstructor = embeddedChannelClass.getConstructor();
            serverboundPacketFlow = resolveServerboundPacketFlow(packetFlowClass);
            serverPlayerConstructor = serverPlayerClass.getConstructor(
                minecraftServerClass,
                serverLevelClass,
                gameProfileClass,
                clientInformationClass
            );

            final Class<?> craftWorldClass = NmsReflectionUtils.resolveClass(
                craftBukkitPackage + ".CraftWorld",
                serverClassLoader
            );
            craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");

            commonListenerCreateInitialMethod = NmsReflectionUtils.resolveStaticFactoryMethod(
                commonListenerCookieClass,
                commonListenerCookieClass,
                gameProfileClass,
                boolean.class
            );
            clientInformationCreateDefaultMethod = NmsReflectionUtils.resolveStaticNoArgFactoryMethod(
                clientInformationClass,
                clientInformationClass
            );
            playerListPlaceNewPlayerMethod = resolvePlayerListPlaceNewPlayerMethod(
                playerList.getClass(),
                connectionClass,
                serverPlayerClass,
                commonListenerCookieClass
            );
            playerListRemoveMethod = resolvePlayerListRemoveMethod(playerList.getClass(), serverPlayerClass);
            serverPlayerGetBukkitEntityMethod = serverPlayerClass.getMethod("getBukkitEntity");

            final Class<?> craftPlayerClass = NmsReflectionUtils.resolveClass(
                craftBukkitPackage + ".entity.CraftPlayer",
                serverClassLoader
            );
            craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");
            connectionChannelField = NmsReflectionUtils.resolveFieldByNameOrAcceptedType(
                connectionClass, "channel", embeddedChannelClass);
            connectionAddressField = NmsReflectionUtils.resolveFieldByNameOrType(
                connectionClass, "address", SocketAddress.class);
            embeddedChannelReadOutboundMethod = embeddedChannelClass.getMethod("readOutbound");
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to initialize v1_21_R7 NMS adapter. Cause: %s: %s"
                    .formatted(exception.getClass().getName(), exception.getMessage()),
                exception
            );
        }
    }

    private static Object resolveComponentJsonCodec(Class<?> componentSerializationClass, Class<?> codecClass)
        throws ReflectiveOperationException
    {
        try
        {
            return componentSerializationClass.getField("CODEC").get(null);
        }
        catch (NoSuchFieldException ignored)
        {
            for (final Field field : componentSerializationClass.getFields())
            {
                if (!Modifier.isStatic(field.getModifiers()) || !codecClass.isAssignableFrom(field.getType()))
                    continue;

                field.setAccessible(true);
                return field.get(null);
            }
            throw new NoSuchFieldException(
                "Failed to resolve component JSON codec field on " + componentSerializationClass.getName()
            );
        }
    }

    private static Object resolveServerboundPacketFlow(Class<?> packetFlowEnumClass)
    {
        final Object[] enumConstants = Objects.requireNonNull(
            packetFlowEnumClass.getEnumConstants(),
            "Expected enum constants for packet flow class."
        );

        for (final Object enumConstant : enumConstants)
        {
            final Enum<?> enumValue = (Enum<?>) enumConstant;
            if ("SERVERBOUND".equals(enumValue.name()))
            {
                return enumConstant;
            }
        }

        final Method stringMethod = NmsReflectionUtils.findNamedNoArgMethod(packetFlowEnumClass, "b");
        if (stringMethod != null)
        {
            for (final Object enumConstant : enumConstants)
            {
                try
                {
                    final Object result = stringMethod.invoke(enumConstant);
                    if (result instanceof String flowName
                        && flowName.toLowerCase(Locale.ROOT).contains("serverbound"))
                    {
                        return enumConstant;
                    }
                }
                catch (ReflectiveOperationException ignored)
                {
                    // Fall through and use default fallback below.
                }
            }
        }

        // Spigot's EnumProtocolDirection typically uses constants "a" (clientbound) and "b" (serverbound).
        final Object fallback = enumConstants[Math.max(0, enumConstants.length - 1)];
        LOG.log(
            System.Logger.Level.WARNING,
            () -> "Could not identify the serverbound packet-flow enum by name or behavior; using positional "
                + "fallback '%s' from %s.".formatted(fallback, packetFlowEnumClass.getName())
        );
        return fallback;
    }

    private static Object resolvePlayerList(Object minecraftServer, ClassLoader serverClassLoader)
        throws ReflectiveOperationException
    {
        final Method namedMethod =
            NmsReflectionUtils.findNamedNoArgMethod(minecraftServer.getClass(), "getPlayerList");
        if (namedMethod != null)
        {
            return namedMethod.invoke(minecraftServer);
        }

        final Class<?> playerListClass =
            NmsReflectionUtils.resolveClass("net.minecraft.server.players.PlayerList", serverClassLoader);

        final Method typedMethod =
            NmsReflectionUtils.findNoArgMethodByReturnType(minecraftServer.getClass(), playerListClass);
        if (typedMethod != null)
        {
            return typedMethod.invoke(minecraftServer);
        }

        final Field typedField = NmsReflectionUtils.findFieldByType(minecraftServer.getClass(), playerListClass);
        if (typedField != null)
        {
            return typedField.get(minecraftServer);
        }

        throw new NoSuchMethodException(
            "Failed to locate PlayerList accessor on " + minecraftServer.getClass().getName()
        );
    }

    private static Method resolvePlayerListPlaceNewPlayerMethod(
        Class<?> playerListClass,
        Class<?> connectionClass,
        Class<?> serverPlayerClass,
        Class<?> commonListenerCookieClass
    )
        throws NoSuchMethodException
    {
        final Method namedMethod = NmsReflectionUtils.findNamedMethod(
            playerListClass,
            "placeNewPlayer",
            connectionClass,
            serverPlayerClass,
            commonListenerCookieClass
        );
        if (namedMethod != null)
        {
            return namedMethod;
        }
        return NmsReflectionUtils.findCompatibleMethod(
            playerListClass, connectionClass, serverPlayerClass, commonListenerCookieClass);
    }

    private static Method resolvePlayerListRemoveMethod(Class<?> playerListClass, Class<?> serverPlayerClass)
        throws NoSuchMethodException
    {
        final Method namedMethod = NmsReflectionUtils.findNamedMethod(playerListClass, "remove", serverPlayerClass);
        if (namedMethod != null)
        {
            return namedMethod;
        }
        return NmsReflectionUtils.findCompatibleMethod(playerListClass, serverPlayerClass);
    }

    /**
     * Spawns and registers a synthetic player in the requested world and teleports it to the spawn location.
     *
     * @param uuid
     *     Player UUID.
     * @param name
     *     Player name.
     * @param world
     *     Target world.
     * @param spawnLocation
     *     Initial player location.
     * @return The spawned Bukkit player instance.
     */
    @Override
    public Player spawnPlayer(UUID uuid, String name, World world, Location spawnLocation)
    {
        Objects.requireNonNull(uuid, "uuid may not be null.");
        Objects.requireNonNull(name, "name may not be null.");
        Objects.requireNonNull(world, "world may not be null.");
        Objects.requireNonNull(spawnLocation, "spawnLocation may not be null.");

        try
        {
            final Object serverLevel = craftWorldGetHandleMethod.invoke(world);
            final Object gameProfile = gameProfileConstructor.newInstance(uuid, name);

            final Object clientInformation = clientInformationCreateDefaultMethod.invoke(null);

            final Object serverPlayer = serverPlayerConstructor.newInstance(
                minecraftServer,
                serverLevel,
                gameProfile,
                clientInformation
            );

            final Object connection = connectionConstructor.newInstance(serverboundPacketFlow);
            final Object embeddedChannel = embeddedChannelConstructor.newInstance();
            connectionChannelField.set(connection, embeddedChannel);
            connectionAddressField.set(connection, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            final Object listenerCookie = commonListenerCreateInitialMethod.invoke(null, gameProfile, false);

            playerListPlaceNewPlayerMethod.invoke(playerList, connection, serverPlayer, listenerCookie);

            final Player bukkitPlayer = (Player) serverPlayerGetBukkitEntityMethod.invoke(serverPlayer);
            bukkitPlayer.teleport(spawnLocation);
            playerChannels.put(uuid, embeddedChannel);
            playerMessageQueues.put(uuid, new ConcurrentLinkedQueue<>());
            playerChatComponentQueues.put(uuid, new ConcurrentLinkedQueue<>());
            return bukkitPlayer;
        }
        catch (InvocationTargetException exception)
        {
            final Throwable rootCause = Objects.requireNonNullElse(exception.getTargetException(), exception);
            throw new IllegalStateException(
                "Failed to spawn synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(name, uuid, rootCause.getClass().getName(), rootCause.getMessage()),
                exception
            );
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to spawn synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(name, uuid, exception.getClass().getName(), exception.getMessage()),
                exception
            );
        }
    }

    /**
     * Returns the full-login driver, resolving its reflection surface on first use.
     *
     * @return The full-login driver for this server.
     */
    @Override
    public IBotLoginDriver loginDriver()
    {
        IBotLoginDriver driver = botLoginDriver;
        if (driver == null)
        {
            synchronized (this)
            {
                driver = botLoginDriver;
                if (driver == null)
                {
                    driver = new BotLoginPipelineDriver();
                    botLoginDriver = driver;
                }
            }
        }
        return driver;
    }

    /**
     * Removes a previously spawned synthetic player from the server and internal channel tracking.
     *
     * @param player
     *     Player to remove.
     */
    @Override
    public void removePlayer(Player player)
    {
        Objects.requireNonNull(player, "player may not be null.");
        try
        {
            final Object serverPlayer = craftPlayerGetHandleMethod.invoke(player);
            playerListRemoveMethod.invoke(playerList, serverPlayer);
            final UUID playerId = player.getUniqueId();
            playerChannels.remove(playerId);
            playerMessageQueues.remove(playerId);
            playerChatComponentQueues.remove(playerId);
        }
        catch (InvocationTargetException exception)
        {
            final Throwable rootCause = Objects.requireNonNullElse(exception.getTargetException(), exception);
            throw new IllegalStateException(
                "Failed to remove synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(
                        player.getName(),
                        player.getUniqueId(),
                        rootCause.getClass().getName(),
                        rootCause.getMessage()
                    ),
                exception
            );
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to remove synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(
                        player.getName(),
                        player.getUniqueId(),
                        exception.getClass().getName(),
                        exception.getMessage()
                    ),
                exception
            );
        }
    }

    /**
     * Drains outbound packet-derived text messages captured for a synthetic player.
     *
     * @param playerId
     *     Player UUID.
     * @return Newly drained message texts.
     */
    @Override
    public List<String> drainReceivedMessages(UUID playerId)
    {
        Objects.requireNonNull(playerId, "playerId may not be null.");
        final Object embeddedChannel = playerChannels.get(playerId);
        if (embeddedChannel == null)
            return List.of();

        drainOutboundPackets(playerId, embeddedChannel);
        return NmsReflectionUtils.drainQueue(
            playerMessageQueues.computeIfAbsent(playerId, ignored -> new ConcurrentLinkedQueue<>()));
    }

    /**
     * Drains newly received chat components (JSON format) for a synthetic player.
     *
     * @param playerId
     *     Synthetic player UUID.
     * @return Newly captured chat components since the previous drain.
     */
    @Override
    public List<String> drainChatComponents(UUID playerId)
    {
        Objects.requireNonNull(playerId, "playerId may not be null.");
        final Object embeddedChannel = playerChannels.get(playerId);
        if (embeddedChannel == null)
            return List.of();

        drainOutboundPackets(playerId, embeddedChannel);
        final Queue<String> componentQueue =
            playerChatComponentQueues.computeIfAbsent(playerId, ignored -> new ConcurrentLinkedQueue<>());
        return NmsReflectionUtils.drainQueue(componentQueue);
    }

    private void drainOutboundPackets(UUID playerId, Object embeddedChannel)
    {
        final Queue<String> messageQueue =
            playerMessageQueues.computeIfAbsent(playerId, ignored -> new ConcurrentLinkedQueue<>());
        final Queue<String> componentQueue =
            playerChatComponentQueues.computeIfAbsent(playerId, ignored -> new ConcurrentLinkedQueue<>());
        try
        {
            Object outboundPacket;
            while ((outboundPacket = embeddedChannelReadOutboundMethod.invoke(embeddedChannel)) != null)
            {
                final String message = extractText(outboundPacket, TEXT_EXTRACTION_MAX_DEPTH, new IdentityHashMap<>());
                if (message != null && !message.isBlank())
                    messageQueue.add(message);

                final String json =
                    extractComponentJson(outboundPacket, TEXT_EXTRACTION_MAX_DEPTH, new IdentityHashMap<>());
                if (json != null)
                    componentQueue.add(json);
            }
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to drain outbound packets for synthetic player '%s'."
                    .formatted(playerId),
                exception
            );
        }
    }

    private @Nullable String extractComponentJson(
        @Nullable Object value,
        int depth,
        IdentityHashMap<Object, Boolean> seen)
    {
        return NmsValueExtractor.extract(
            value, depth, seen,
            this::trySerializeComponentToJson,
            BotPlayerNmsAdapterV1_21_R7::isSafeComponentAccessor,
            false
        );
    }

    /**
     * Detects NMS chat component objects and serialises them to JSON.
     * Returns {@code null} when {@code value} is not a component or serialisation fails.
     */
    private @Nullable String trySerializeComponentToJson(Object value)
    {
        if (!value.getClass().getSimpleName().endsWith("Component") &&
            !value.getClass().getName().startsWith("net.minecraft.network.chat."))
        {
            return null;
        }
        try
        {
            final Object dataResult = componentCodecEncodeStartMethod.invoke(
                componentJsonCodec,
                componentJsonOps,
                value
            );
            final Optional<?> serializedJson = (Optional<?>) dataResultResultMethod.invoke(dataResult);
            if (serializedJson.isPresent())
                return serializedJson.get().toString();

            // Encoding produced a DataResult error rather than a value; the component would otherwise vanish
            // silently from the capture. Surface Mojang's error message so the failure is diagnosable.
            LOG.log(
                System.Logger.Level.WARNING,
                "Failed to serialize chat component of type '%s': %s"
                    .formatted(value.getClass().getName(), readDataResultError(dataResult))
            );
            return null;
        }
        catch (Exception exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "Failed to serialize chat component of type '" + value.getClass().getName() + "'.",
                exception
            );
            return null;
        }
    }

    /**
     * Reads the message from a Mojang {@code DataResult}'s error branch via reflection.
     *
     * @param dataResult
     *     The {@code DataResult} whose {@code error()} branch should be read.
     * @return
     *     The error message, or a diagnostic placeholder when no message is available.
     */
    private String readDataResultError(Object dataResult)
    {
        try
        {
            final Optional<?> error = (Optional<?>) dataResultErrorMethod.invoke(dataResult);
            if (error.isEmpty())
                return "<no error detail>";
            final Object errorValue = error.get();
            return String.valueOf(errorValue.getClass().getMethod("message").invoke(errorValue));
        }
        catch (Exception exception)
        {
            return "<error detail unavailable: " + exception.getClass().getSimpleName() + ">";
        }
    }

    private static boolean isSafeComponentAccessor(Method method)
    {
        if (method.getParameterCount() != 0)
            return false;
        if (method.getDeclaringClass() == Object.class)
            return false;

        final String methodName = method.getName();
        if (methodName.equals("getClass") || methodName.equals("hashCode") || methodName.equals("toString"))
            return false;

        final Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE || returnType.isPrimitive())
            return false;

        // A zero-arg accessor that directly returns a chat-component type is always followed, regardless of its
        // name. Spigot's remapped record accessors are single obfuscated letters (e.g. IChatBaseComponent b() on
        // ClientboundSystemChatPacket), which no name heuristic matches, whereas Paper exposes the same field as
        // content(); keying on the return type rather than the method name captures the component on both distros.
        // Deliberately ONLY the type-name suffix: a whole-chat-package match would also follow MessageSignature/
        // ChatType.Bound/FilterMask accessors on multi-field chat packets and could nondeterministically capture a
        // decoration instead of the message. Note this widens capture to any Component-returning accessor
        // (boss bars, titles, tab-list headers) — component capture is type-driven, not name-driven.
        final String returnTypeName = returnType.getName();
        if (returnTypeName.endsWith("Component"))
            return true;

        // For container return types the component sits one level deeper, so require a component-like accessor
        // name before following the getter to avoid walking unrelated object graphs.
        final String lowerMethodName = methodName.toLowerCase(Locale.ROOT);
        final boolean safeName = methodName.startsWith("get") ||
            methodName.startsWith("is") ||
            lowerMethodName.contains("component") ||
            lowerMethodName.contains("message") ||
            lowerMethodName.contains("content") ||
            lowerMethodName.contains("title");
        if (!safeName)
            return false;

        return Optional.class.isAssignableFrom(returnType) ||
            Collection.class.isAssignableFrom(returnType) ||
            returnType.isArray();
    }

    private static @Nullable String extractText(
        @Nullable Object value,
        int depth,
        IdentityHashMap<Object, Boolean> seen)
    {
        // Try getString() directly first (net.minecraft.network.chat.Component has this method).
        // NmsValueExtractor.extract handles containers first, so we pre-check for String leaves here.
        return NmsValueExtractor.extract(
            value, depth, seen,
            v ->
            {
                if (v instanceof String s)
                    return s.isBlank() ? null : s;
                final String direct = NmsReflectionUtils.invokeStringMethod(v, "getString");
                return (direct != null && !direct.isBlank()) ? direct : null;
            },
            BotPlayerNmsAdapterV1_21_R7::isSafeTextAccessor,
            true
        );
    }

    private static boolean isSafeTextAccessor(Method method)
    {
        if (method.getParameterCount() != 0)
            return false;
        if (method.getDeclaringClass() == Object.class)
            return false;

        final String methodName = method.getName();
        if (methodName.equals("getClass") || methodName.equals("hashCode") || methodName.equals("toString"))
            return false;

        final Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE || returnType.isPrimitive())
            return false;

        if (returnType == String.class)
            return true;
        if (Optional.class.isAssignableFrom(returnType))
            return true;
        if (Collection.class.isAssignableFrom(returnType))
            return true;
        if (returnType.isArray())
            return true;

        final String returnTypeName = returnType.getName();
        if (returnTypeName.startsWith("net.minecraft.network.chat."))
            return true;
        if (returnTypeName.endsWith("Component"))
            return true;

        final String lowerMethodName = methodName.toLowerCase(Locale.ROOT);
        return lowerMethodName.contains("message") ||
            lowerMethodName.contains("content") ||
            lowerMethodName.contains("text") ||
            lowerMethodName.contains("title");
    }

}

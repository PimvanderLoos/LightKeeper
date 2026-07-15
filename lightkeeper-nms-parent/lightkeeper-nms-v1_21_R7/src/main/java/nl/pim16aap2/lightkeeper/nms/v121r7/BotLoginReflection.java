package nl.pim16aap2.lightkeeper.nms.v121r7;

import nl.pim16aap2.lightkeeper.nms.api.BotJoinPhase;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Resolves and holds the (fully reflective) Paper/Spigot 1.21.11 surface used by {@link BotLoginPipelineDriver}
 * to drive the vanilla login pipeline over a real loopback TCP connection.
 *
 * <p>Nothing here references a Minecraft/Netty type at compile time: every class, method, field, and enum
 * constant is resolved through {@link NmsReflectionUtils} with per-distribution name fallbacks. Paper ships
 * Mojang-mapped names; Spigot obfuscates the {@code Connection} class ({@code NetworkManager}), the
 * {@code ConnectionProtocol} enum ({@code EnumProtocol}), all member names, and the login/game/handshake
 * packet classes (legacy {@code PacketLoginOut*}/{@code PacketPlayOut*} names), while keeping Mojang names for
 * the {@code common}/{@code configuration} packages and the protocol-holder classes. Members whose names are
 * obfuscated are therefore resolved structurally (by signature or generic type), never by name.
 */
final class BotLoginReflection
{
    private static final System.Logger LOG = System.getLogger(BotLoginReflection.class.getName());

    /**
     * How the session should react to a received clientbound packet, resolved from its class via
     * {@link #classify(Class)}.
     */
    enum PacketKind
    {
        /** Login compression threshold. */ COMPRESSION,
        /** Login success. */ LOGIN_FINISHED,
        /** Configuration known-packs request. */ KNOWN_PACKS,
        /** Configuration code-of-conduct. */ CODE_OF_CONDUCT,
        /** Configuration finished. */ FINISH_CONFIGURATION,
        /** Common keep-alive. */ KEEP_ALIVE,
        /** Common ping. */ PING,
        /** Play teleport/position. */ TELEPORT,
        /** Play login (join confirmation). */ GAME_LOGIN,
        /** Login-phase disconnect. */ LOGIN_DISCONNECT,
        /** Common disconnect. */ DISCONNECT,
        /** Known informational packet requiring no response. */ CONSUME,
        /** Not recognized (fail-loud in login/config, warn-once in play). */ UNKNOWN
    }

    private final ClassLoader serverClassLoader;

    // ---- Interfaces the client-listener Proxy implements + core protocol types. ----
    private final Class<?> loginListenerInterface;
    private final Class<?> configurationListenerInterface;
    private final Class<?> gameListenerInterface;
    private final Class<?> packetClass;
    private final Class<?> packetFlowClass;
    private final Class<?> connectionProtocolClass;

    // ---- Enum constants the Proxy answers with for the non-packet listener methods. ----
    private final Object clientboundFlow;
    private final Object protocolLogin;
    private final Object protocolConfiguration;
    private final Object protocolPlay;

    // ---- Clientbound packet classes the session dispatches on. ----
    private final Class<?> loginCompressionClass;
    private final Class<?> loginFinishedClass;
    private final Class<?> loginDisconnectClass;
    private final Class<?> selectKnownPacksClass;
    private final Class<?> finishConfigurationClass;
    private final Class<?> codeOfConductClass;
    private final Class<?> keepAliveClass;
    private final Class<?> pingClass;
    private final Class<?> disconnectClass;
    private final Class<?> playerPositionClass;
    private final Class<?> gameLoginClass;

    /**
     * Clientbound packets that are expected in the login/configuration phase and require no response; kept
     * explicit so a genuinely new packet (protocol drift) is not in the set and triggers the fail-loud policy.
     */
    private final Set<Class<?>> consumeClasses;

    // ---- Resolved handles used by the operation methods. ----
    private final Class<?> connectionClass;
    private final Object eventLoopGroupHolder;
    private final Method connectToServerMethod;
    private final Method initiateLoginConnectionMethod;
    private final Method sendMethod;
    private final Method setupInboundProtocolMethod;
    private final Method setupOutboundProtocolMethod;
    private final Method setupCompressionMethod;
    private final Field channelField;
    private final Method channelCloseMethod;

    private final Object configurationClientboundInfo;
    private final Object configurationServerboundInfo;
    private final Object gameClientboundInfo;
    private final Object gameServerboundInfo;

    private final Constructor<?> helloPacketConstructor;
    private final Object loginAcknowledgedPacket;
    private final Object finishConfigurationPacket;
    private final Object acceptCodeOfConductPacket;
    private final Constructor<?> selectKnownPacksConstructor;
    private final Constructor<?> keepAlivePacketConstructor;
    private final Constructor<?> pongPacketConstructor;
    private final Constructor<?> acceptTeleportationConstructor;
    private final Constructor<?> clientInformationPacketConstructor;
    private final Class<?> clientInformationClass;
    private final Method clientInformationCreateDefaultMethod;

    private final Map<AccessorKey, Method> accessorCache = new ConcurrentHashMap<>();

    BotLoginReflection()
    {
        try
        {
            serverClassLoader = Bukkit.getServer().getClass().getClassLoader();
            final Object minecraftServer = resolveMinecraftServer();

            packetClass = NmsReflectionUtils.resolveClass("net.minecraft.network.protocol.Packet", serverClassLoader);
            packetFlowClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.protocol.PacketFlow",
                "net.minecraft.network.protocol.EnumProtocolDirection");
            connectionProtocolClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.ConnectionProtocol",
                "net.minecraft.network.EnumProtocol");
            connectionClass = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.Connection",
                "net.minecraft.network.NetworkManager");
            final Class<?> packetListenerClass =
                NmsReflectionUtils.resolveClass("net.minecraft.network.PacketListener", serverClassLoader);

            clientboundFlow = NmsReflectionUtils.resolveEnumConstant(packetFlowClass, "CLIENTBOUND");
            final Object serverboundFlow = NmsReflectionUtils.resolveEnumConstant(packetFlowClass, "SERVERBOUND");
            protocolLogin = NmsReflectionUtils.resolveEnumConstant(connectionProtocolClass, "LOGIN");
            protocolConfiguration = NmsReflectionUtils.resolveEnumConstant(connectionProtocolClass, "CONFIGURATION");
            protocolPlay = NmsReflectionUtils.resolveEnumConstant(connectionProtocolClass, "PLAY");

            loginListenerInterface = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.protocol.login.ClientLoginPacketListener",
                "net.minecraft.network.protocol.login.PacketLoginOutListener");
            configurationListenerInterface = NmsReflectionUtils.resolveClass(
                "net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener", serverClassLoader);
            gameListenerInterface = NmsReflectionUtils.resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.protocol.game.ClientGamePacketListener",
                "net.minecraft.network.protocol.game.PacketListenerPlayOut");

            loginCompressionClass = resolveFirst(
                "net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket",
                "net.minecraft.network.protocol.login.PacketLoginOutSetCompression");
            loginFinishedClass = resolveFirst(
                "net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket",
                "net.minecraft.network.protocol.login.PacketLoginOutSuccess");
            loginDisconnectClass = resolveFirst(
                "net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket",
                "net.minecraft.network.protocol.login.PacketLoginOutDisconnect");
            selectKnownPacksClass = resolveFirst(
                "net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks");
            finishConfigurationClass = resolveFirst(
                "net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket");
            codeOfConductClass = resolveFirst(
                "net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket");
            keepAliveClass = resolveFirst("net.minecraft.network.protocol.common.ClientboundKeepAlivePacket");
            pingClass = resolveFirst("net.minecraft.network.protocol.common.ClientboundPingPacket");
            disconnectClass = resolveFirst("net.minecraft.network.protocol.common.ClientboundDisconnectPacket");
            playerPositionClass = resolveFirst(
                "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutPosition");
            gameLoginClass = resolveFirst(
                "net.minecraft.network.protocol.game.ClientboundLoginPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutLogin");

            consumeClasses = resolveConsumeClasses();

            // Event loop group + connection factory (real TCP loopback client).
            final Class<?> eventLoopGroupHolderClass = NmsReflectionUtils.resolveClass(
                "net.minecraft.server.network.EventLoopGroupHolder", serverClassLoader);
            final Method remoteMethod = NmsReflectionUtils.resolveStaticFactoryMethod(
                eventLoopGroupHolderClass, eventLoopGroupHolderClass, boolean.class);
            eventLoopGroupHolder = Objects.requireNonNull(
                remoteMethod.invoke(null, false), "EventLoopGroupHolder.remote(false) returned null.");
            connectToServerMethod = resolveConnectToServer(connectionClass, eventLoopGroupHolderClass);
            initiateLoginConnectionMethod = NmsReflectionUtils.findPublicMethod(
                connectionClass, void.class, String.class, int.class, loginListenerInterface);
            sendMethod = NmsReflectionUtils.findPublicMethod(connectionClass, void.class, packetClass);
            setupInboundProtocolMethod = NmsReflectionUtils.findPublicMethod(
                connectionClass, void.class,
                NmsReflectionUtils.resolveClass("net.minecraft.network.ProtocolInfo", serverClassLoader),
                packetListenerClass);
            setupOutboundProtocolMethod = NmsReflectionUtils.findPublicMethod(
                connectionClass, void.class,
                NmsReflectionUtils.resolveClass("net.minecraft.network.ProtocolInfo", serverClassLoader));
            setupCompressionMethod = NmsReflectionUtils.findPublicMethod(
                connectionClass, void.class, int.class, boolean.class);
            final Class<?> channelClass =
                NmsReflectionUtils.resolveClass("io.netty.channel.Channel", serverClassLoader);
            channelField = NmsReflectionUtils.resolveFieldByNameOrAcceptedType(
                connectionClass, "channel", channelClass);
            channelCloseMethod = channelClass.getMethod("close");

            // Bound protocol infos for the configuration and play phases.
            final ProtocolInfos protocolInfos = resolveProtocolInfos(minecraftServer);
            configurationClientboundInfo = protocolInfos.configurationClientbound();
            configurationServerboundInfo = protocolInfos.configurationServerbound();
            gameClientboundInfo = protocolInfos.gameClientbound();
            gameServerboundInfo = protocolInfos.gameServerbound();

            // Serverbound packet factories.
            final Class<?> helloClass = resolveFirst(
                "net.minecraft.network.protocol.login.ServerboundHelloPacket",
                "net.minecraft.network.protocol.login.PacketLoginInStart");
            helloPacketConstructor = helloClass.getConstructor(String.class, UUID.class);
            // These are codec singletons: the IdDispatchCodec encodes them by identity against the registered
            // INSTANCE, so a freshly constructed instance fails to encode. Resolve the INSTANCE field by type
            // (its name is obfuscated on Spigot).
            loginAcknowledgedPacket = resolveSingletonInstance(
                "net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket");
            finishConfigurationPacket = resolveSingletonInstance(
                "net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
            acceptCodeOfConductPacket = resolveSingletonInstance(
                "net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket");
            selectKnownPacksConstructor = resolveFirst(
                "net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks")
                .getConstructor(java.util.List.class);
            keepAlivePacketConstructor = resolveFirst(
                "net.minecraft.network.protocol.common.ServerboundKeepAlivePacket").getConstructor(long.class);
            pongPacketConstructor = resolveFirst(
                "net.minecraft.network.protocol.common.ServerboundPongPacket").getConstructor(int.class);
            acceptTeleportationConstructor = resolveFirst(
                "net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket",
                "net.minecraft.network.protocol.game.PacketPlayInTeleportAccept").getConstructor(int.class);

            clientInformationClass = NmsReflectionUtils.resolveClass(
                "net.minecraft.server.level.ClientInformation", serverClassLoader);
            clientInformationCreateDefaultMethod =
                NmsReflectionUtils.resolveStaticNoArgFactoryMethod(clientInformationClass, clientInformationClass);
            clientInformationPacketConstructor = resolveFirst(
                "net.minecraft.network.protocol.common.ServerboundClientInformationPacket")
                .getConstructor(clientInformationClass);

            // Keep the serverbound flow reachable so a future distro rename is caught at init, not at first send.
            Objects.requireNonNull(serverboundFlow, "SERVERBOUND packet flow constant.");
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to initialize the v1_21_R7 full-login driver reflection surface. Cause: %s: %s"
                    .formatted(exception.getClass().getName(), exception.getMessage()),
                exception);
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // Operations invoked by BotLoginSession (each runs on the Netty event-loop thread from the Proxy handler,
    // except openLoginConnection which runs on the caller's thread).
    // -------------------------------------------------------------------------------------------------------

    /**
     * Opens a loopback TCP connection and sends the login-intent handshake, transitioning the connection into
     * the login phase with the given client-listener Proxy installed.
     *
     * <p>Does not yet send the login-start (hello); the caller must store the returned connection before calling
     * {@link #sendHello(Object, String)} so the Proxy handler never observes a null connection while reacting to
     * the server's login responses.
     *
     * @param port
     *     Server port to connect to.
     * @param listenerProxy
     *     Client-listener Proxy that will receive clientbound packets.
     * @return The connected {@code Connection}/{@code NetworkManager} instance.
     * @throws ReflectiveOperationException
     *     When a reflective call fails.
     */
    Object openConnection(int port, Object listenerProxy)
        throws ReflectiveOperationException
    {
        final InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        final Object connection = connectToServerMethod.invoke(null, address, eventLoopGroupHolder, null);
        initiateLoginConnectionMethod.invoke(connection, "127.0.0.1", port, listenerProxy);
        return connection;
    }

    /**
     * Sends the login-start (hello) packet that begins the login exchange.
     *
     * @param connection
     *     The connection returned by {@link #openConnection(int, Object)}.
     * @param name
     *     Player name to log in as.
     * @throws ReflectiveOperationException
     *     When a reflective call fails.
     */
    void sendHello(Object connection, String name)
        throws ReflectiveOperationException
    {
        sendMethod.invoke(connection, helloPacketConstructor.newInstance(name, offlineUuid(name)));
    }

    /**
     * Applies the compression threshold advertised by a {@code ClientboundLoginCompressionPacket}.
     */
    void applyCompression(Object connection, Object compressionPacket)
        throws ReflectiveOperationException
    {
        final int threshold = ((Number) readNoArg(compressionPacket, int.class)).intValue();
        setupCompressionMethod.invoke(connection, threshold, false);
    }

    /**
     * Acknowledges login and transitions the connection into the configuration phase, sending the client's
     * locale/settings.
     */
    void enterConfigurationPhase(Object connection, Object listenerProxy, @Nullable String locale)
        throws ReflectiveOperationException
    {
        sendMethod.invoke(connection, loginAcknowledgedPacket);
        setupInboundProtocolMethod.invoke(connection, configurationClientboundInfo, listenerProxy);
        setupOutboundProtocolMethod.invoke(connection, configurationServerboundInfo);
        final Object clientInformationPacket =
            clientInformationPacketConstructor.newInstance(buildClientInformation(locale));
        sendMethod.invoke(connection, clientInformationPacket);
    }

    /**
     * Echoes the server's known-pack selection back to it.
     */
    void echoKnownPacks(Object connection, Object selectKnownPacksPacket)
        throws ReflectiveOperationException
    {
        final Object knownPacks = readNoArg(selectKnownPacksPacket, java.util.List.class);
        sendMethod.invoke(connection, selectKnownPacksConstructor.newInstance(knownPacks));
    }

    /**
     * Accepts a server code-of-conduct so the join is not stalled by it.
     */
    void acceptCodeOfConduct(Object connection)
        throws ReflectiveOperationException
    {
        sendMethod.invoke(connection, acceptCodeOfConductPacket);
    }

    /**
     * Finishes configuration and transitions the connection into the play phase.
     */
    void enterPlayPhase(Object connection, Object listenerProxy)
        throws ReflectiveOperationException
    {
        sendMethod.invoke(connection, finishConfigurationPacket);
        setupInboundProtocolMethod.invoke(connection, gameClientboundInfo, listenerProxy);
        setupOutboundProtocolMethod.invoke(connection, gameServerboundInfo);
    }

    /**
     * Answers a keep-alive so the bot is not timed out.
     */
    void answerKeepAlive(Object connection, Object keepAlivePacket)
        throws ReflectiveOperationException
    {
        final long id = ((Number) readNoArg(keepAlivePacket, long.class)).longValue();
        sendMethod.invoke(connection, keepAlivePacketConstructor.newInstance(id));
    }

    /**
     * Answers a ping with a pong.
     */
    void answerPing(Object connection, Object pingPacket)
        throws ReflectiveOperationException
    {
        final int id = ((Number) readNoArg(pingPacket, int.class)).intValue();
        sendMethod.invoke(connection, pongPacketConstructor.newInstance(id));
    }

    /**
     * Acknowledges a teleport so the server accepts the bot's position.
     */
    void acceptTeleport(Object connection, Object positionPacket)
        throws ReflectiveOperationException
    {
        final int id = ((Number) readNoArg(positionPacket, int.class)).intValue();
        sendMethod.invoke(connection, acceptTeleportationConstructor.newInstance(id));
    }

    /**
     * Extracts a best-effort plain-text kick reason from a disconnect packet.
     */
    String disconnectReason(Object disconnectPacket)
    {
        try
        {
            final Object reasonComponent = readComponent(disconnectPacket);
            if (reasonComponent == null)
                return "<no reason>";
            final String text = NmsReflectionUtils.invokeStringMethod(reasonComponent, "getString");
            return text != null && !text.isBlank() ? text : String.valueOf(reasonComponent);
        }
        catch (Exception exception)
        {
            return "<reason unavailable: " + exception.getClass().getSimpleName() + ">";
        }
    }

    /**
     * Closes the connection's channel, best-effort.
     */
    void closeConnection(Object connection)
    {
        try
        {
            final Object channel = channelField.get(connection);
            if (channel != null)
                channelCloseMethod.invoke(channel);
        }
        catch (Exception exception)
        {
            LOG.log(System.Logger.Level.DEBUG, "Failed to close full-login bot connection cleanly.", exception);
        }
    }

    /**
     * Creates the client-listener Proxy over the login/configuration/game listener interfaces.
     */
    Object newListenerProxy(InvocationHandler handler)
    {
        return Proxy.newProxyInstance(
            serverClassLoader,
            new Class<?>[]{loginListenerInterface, configurationListenerInterface, gameListenerInterface},
            handler);
    }

    /**
     * Classifies a received clientbound packet class so the session can react to it.
     *
     * @param clientboundPacketClass
     *     Runtime class of the received packet.
     * @return The reaction kind.
     */
    PacketKind classify(Class<?> clientboundPacketClass)
    {
        if (clientboundPacketClass == loginCompressionClass)
            return PacketKind.COMPRESSION;
        if (clientboundPacketClass == loginFinishedClass)
            return PacketKind.LOGIN_FINISHED;
        if (clientboundPacketClass == selectKnownPacksClass)
            return PacketKind.KNOWN_PACKS;
        if (clientboundPacketClass == codeOfConductClass)
            return PacketKind.CODE_OF_CONDUCT;
        if (clientboundPacketClass == finishConfigurationClass)
            return PacketKind.FINISH_CONFIGURATION;
        if (clientboundPacketClass == keepAliveClass)
            return PacketKind.KEEP_ALIVE;
        if (clientboundPacketClass == pingClass)
            return PacketKind.PING;
        if (clientboundPacketClass == playerPositionClass)
            return PacketKind.TELEPORT;
        if (clientboundPacketClass == gameLoginClass)
            return PacketKind.GAME_LOGIN;
        if (clientboundPacketClass == loginDisconnectClass)
            return PacketKind.LOGIN_DISCONNECT;
        if (clientboundPacketClass == disconnectClass)
            return PacketKind.DISCONNECT;
        if (consumeClasses.contains(clientboundPacketClass))
            return PacketKind.CONSUME;
        return PacketKind.UNKNOWN;
    }

    /**
     * @return The server's {@code Packet} base class (used by the session to recognize packet-handler methods).
     */
    Class<?> packetClass()
    {
        return packetClass;
    }

    /**
     * @return The server's {@code PacketFlow} enum class.
     */
    Class<?> packetFlowClass()
    {
        return packetFlowClass;
    }

    /**
     * @return The server's {@code ConnectionProtocol} enum class.
     */
    Class<?> connectionProtocolClass()
    {
        return connectionProtocolClass;
    }

    /**
     * @return The {@code CLIENTBOUND} packet-flow constant the Proxy reports as its flow.
     */
    Object clientboundFlow()
    {
        return clientboundFlow;
    }

    /**
     * Returns the {@code ConnectionProtocol} constant matching a join phase, for the Proxy's {@code protocol()}.
     *
     * @param phase
     *     Current join phase.
     * @return The matching {@code ConnectionProtocol} enum constant.
     */
    Object protocolConstant(BotJoinPhase phase)
    {
        return switch (phase)
        {
            case LOGIN -> protocolLogin;
            case CONFIGURATION -> protocolConfiguration;
            case PLAY -> protocolPlay;
        };
    }

    /**
     * Computes the offline (cracked) UUID the server derives for a name, matching Bukkit's scheme.
     */
    static UUID offlineUuid(String name)
    {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------------------------------------
    // Resolution helpers.
    // -------------------------------------------------------------------------------------------------------

    private Class<?> resolveFirst(String... classNames)
        throws ClassNotFoundException
    {
        return NmsReflectionUtils.resolveFirstClass(serverClassLoader, classNames);
    }

    private Object resolveMinecraftServer()
        throws ReflectiveOperationException
    {
        final Object craftServer = Bukkit.getServer();
        return craftServer.getClass().getMethod("getServer").invoke(craftServer);
    }

    private Set<Class<?>> resolveConsumeClasses()
    {
        final Set<Class<?>> classes = new HashSet<>();
        // Login-phase informational packets (offline mode never sends encryption/custom-query, but tolerate).
        addOptional(classes,
            "net.minecraft.network.protocol.login.ClientboundHelloPacket",
            "net.minecraft.network.protocol.login.PacketLoginOutEncryptionBegin");
        addOptional(classes,
            "net.minecraft.network.protocol.login.ClientboundCustomQueryPacket",
            "net.minecraft.network.protocol.login.PacketLoginOutCustomPayload");
        // Configuration/common informational packets that require no response (Mojang names on both distros).
        for (final String className : new String[]{
            "net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket",
            "net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket",
            "net.minecraft.network.protocol.configuration.ClientboundResetChatPacket",
            "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket",
            "net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket",
            "net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket",
            "net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket",
            "net.minecraft.network.protocol.common.ClientboundStoreCookiePacket",
            "net.minecraft.network.protocol.common.ClientboundTransferPacket",
            "net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket",
            "net.minecraft.network.protocol.common.ClientboundServerLinksPacket",
            "net.minecraft.network.protocol.common.ClientboundClearDialogPacket",
            "net.minecraft.network.protocol.common.ClientboundShowDialogPacket",
            "net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket"})
        {
            addOptional(classes, className);
        }
        return Set.copyOf(classes);
    }

    private void addOptional(Set<Class<?>> classes, String... classNames)
    {
        try
        {
            classes.add(NmsReflectionUtils.resolveFirstClass(serverClassLoader, classNames));
        }
        catch (ClassNotFoundException ignored)
        {
            LOG.log(
                System.Logger.Level.DEBUG,
                () -> "Optional login/config consume packet not present on this server: " + classNames[0]);
        }
    }

    private static Method resolveConnectToServer(Class<?> connectionClass, Class<?> holderClass)
        throws NoSuchMethodException
    {
        for (final Method method : connectionClass.getDeclaredMethods())
        {
            if (!Modifier.isStatic(method.getModifiers()))
                continue;
            if (!connectionClass.isAssignableFrom(method.getReturnType()))
                continue;
            final Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 3
                || !parameters[0].equals(InetSocketAddress.class)
                || !parameters[1].equals(holderClass))
                continue;

            method.setAccessible(true);
            return method;
        }
        throw new NoSuchMethodException(
            "Failed to resolve connectToServer(InetSocketAddress, EventLoopGroupHolder, ?) on "
                + connectionClass.getName() + ".");
    }

    private Object resolveSingletonInstance(String className)
        throws ReflectiveOperationException
    {
        final Class<?> packetType = NmsReflectionUtils.resolveClass(className, serverClassLoader);
        for (final Field field : packetType.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || !packetType.equals(field.getType()))
                continue;
            field.setAccessible(true);
            return Objects.requireNonNull(field.get(null), "Singleton field on " + className + " was null.");
        }
        throw new NoSuchFieldException(
            "No static singleton instance field of type " + className + " found on itself.");
    }

    private ProtocolInfos resolveProtocolInfos(Object minecraftServer)
        throws ReflectiveOperationException
    {
        final Class<?> protocolInfoClass =
            NmsReflectionUtils.resolveClass("net.minecraft.network.ProtocolInfo", serverClassLoader);
        final Class<?> simpleUnboundProtocolClass =
            NmsReflectionUtils.resolveClass("net.minecraft.network.protocol.SimpleUnboundProtocol", serverClassLoader);
        final Class<?> unboundProtocolClass =
            NmsReflectionUtils.resolveClass("net.minecraft.network.protocol.UnboundProtocol", serverClassLoader);
        final Class<?> registryFriendlyByteBufClass =
            NmsReflectionUtils.resolveClass("net.minecraft.network.RegistryFriendlyByteBuf", serverClassLoader);
        final Class<?> registryAccessClass = NmsReflectionUtils.resolveFirstClass(
            serverClassLoader, "net.minecraft.core.RegistryAccess", "net.minecraft.core.IRegistryCustom");

        // Configuration protocol infos, distinguished by the (generic) listener type argument.
        final Class<?> configurationProtocolsClass = NmsReflectionUtils.resolveClass(
            "net.minecraft.network.protocol.configuration.ConfigurationProtocols", serverClassLoader);
        Object configurationClientbound = null;
        Object configurationServerbound = null;
        for (final Field field : configurationProtocolsClass.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || !protocolInfoClass.equals(field.getType()))
                continue;
            field.setAccessible(true);
            if (genericArgument(field, 0) == configurationListenerInterface)
                configurationClientbound = field.get(null);
            else
                configurationServerbound = field.get(null);
        }

        // Game protocol templates: one SimpleUnboundProtocol (clientbound), one UnboundProtocol (serverbound).
        final Class<?> gameProtocolsClass = NmsReflectionUtils.resolveClass(
            "net.minecraft.network.protocol.game.GameProtocols", serverClassLoader);
        final Field gameClientboundTemplateField =
            singleStaticField(gameProtocolsClass, simpleUnboundProtocolClass);
        final Field gameServerboundTemplateField =
            singleStaticField(gameProtocolsClass, unboundProtocolClass);
        final Class<?> gameContextClass = (Class<?>) genericArgumentType(gameServerboundTemplateField, 2);

        final Object registryAccess = resolveRegistryAccess(minecraftServer, registryAccessClass);
        final Method decoratorMethod = NmsReflectionUtils.resolveStaticFactoryMethod(
            registryFriendlyByteBufClass, Function.class, registryAccessClass);
        final Object decorator = decoratorMethod.invoke(null, registryAccess);

        final Method simpleBindMethod =
            NmsReflectionUtils.findPublicMethod(simpleUnboundProtocolClass, protocolInfoClass, Function.class);
        final Object gameClientbound =
            simpleBindMethod.invoke(gameClientboundTemplateField.get(null), decorator);

        final Method unboundBindMethod = NmsReflectionUtils.findPublicMethod(
            unboundProtocolClass, protocolInfoClass, Function.class, Object.class);
        final Object gameContext = Proxy.newProxyInstance(
            serverClassLoader, new Class<?>[]{gameContextClass}, BotLoginReflection::gameContextInvoke);
        final Object gameServerbound =
            unboundBindMethod.invoke(gameServerboundTemplateField.get(null), decorator, gameContext);

        return new ProtocolInfos(
            Objects.requireNonNull(configurationClientbound, "configuration clientbound ProtocolInfo"),
            Objects.requireNonNull(configurationServerbound, "configuration serverbound ProtocolInfo"),
            gameClientbound,
            gameServerbound);
    }

    @SuppressWarnings({"PMD.UseVarargs", "PMD.CompareObjectsWithEquals"}) // InvocationHandler contract; proxy identity.
    private static Object gameContextInvoke(Object proxy, Method method, Object @Nullable [] args)
    {
        if (method.getReturnType() == boolean.class)
            return Boolean.FALSE;
        if ("toString".equals(method.getName()))
            return "GameProtocols$Context(hasInfiniteMaterials=false)";
        if ("hashCode".equals(method.getName()))
            return System.identityHashCode(proxy);
        if ("equals".equals(method.getName()))
            return args != null && args.length == 1 && proxy == args[0];
        return null;
    }

    private Object resolveRegistryAccess(Object minecraftServer, Class<?> registryAccessClass)
        throws ReflectiveOperationException
    {
        final Method named = NmsReflectionUtils.findNamedNoArgMethod(minecraftServer.getClass(), "registryAccess");
        final Method accessor = named != null
            ? named
            : NmsReflectionUtils.findInstanceNoArgMethodByReturnType(minecraftServer.getClass(), registryAccessClass);
        return Objects.requireNonNull(accessor.invoke(minecraftServer), "registryAccess() returned null.");
    }

    private static Field singleStaticField(Class<?> owner, Class<?> fieldType)
        throws NoSuchFieldException
    {
        Field found = null;
        for (final Field field : owner.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || !fieldType.equals(field.getType()))
                continue;
            if (found != null)
                throw new NoSuchFieldException("Multiple static " + fieldType.getName() + " fields on "
                    + owner.getName() + "; cannot disambiguate.");
            found = field;
        }
        if (found == null)
            throw new NoSuchFieldException("No static " + fieldType.getName() + " field on " + owner.getName() + ".");
        found.setAccessible(true);
        return found;
    }

    private static @Nullable Class<?> genericArgument(Field field, int index)
    {
        final Type argument = genericArgumentType(field, index);
        return argument instanceof Class<?> clazz ? clazz : null;
    }

    private static Type genericArgumentType(Field field, int index)
    {
        return ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[index];
    }

    private Object buildClientInformation(@Nullable String locale)
        throws ReflectiveOperationException
    {
        final Object defaultInformation = clientInformationCreateDefaultMethod.invoke(null);
        if (locale == null)
            return defaultInformation;

        // Rebuild the record with only the language (first component) replaced, so the locale wire path is
        // exercised without depending on the (obfuscated) accessor/constructor-parameter names.
        final RecordComponent[] components = clientInformationClass.getRecordComponents();
        final Class<?>[] parameterTypes = new Class<?>[components.length];
        final Object[] values = new Object[components.length];
        for (int idx = 0; idx < components.length; ++idx)
        {
            parameterTypes[idx] = components[idx].getType();
            values[idx] = components[idx].getAccessor().invoke(defaultInformation);
        }
        values[0] = locale;
        final Constructor<?> canonical = clientInformationClass.getDeclaredConstructor(parameterTypes);
        canonical.setAccessible(true);
        return canonical.newInstance(values);
    }

    private Object readNoArg(Object target, Class<?> returnType)
        throws ReflectiveOperationException
    {
        final Method accessor = accessorCache.computeIfAbsent(
            new AccessorKey(target.getClass(), returnType),
            key -> findNoArgAccessor(key.owner(), key.returnType()));
        return accessor.invoke(target);
    }

    private static Method findNoArgAccessor(Class<?> owner, Class<?> returnType)
    {
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers()))
                    continue;
                final String name = method.getName();
                if ("hashCode".equals(name) || "toString".equals(name))
                    continue;
                if (!returnType.equals(method.getReturnType()))
                    continue;
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException(
            "No no-arg accessor returning " + returnType.getName() + " on " + owner.getName() + ".");
    }

    private @Nullable Object readComponent(Object packet)
        throws ReflectiveOperationException
    {
        for (Class<?> cursor = packet.getClass(); cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers()))
                    continue;
                if (!method.getReturnType().getName().startsWith("net.minecraft.network.chat."))
                    continue;
                method.setAccessible(true);
                return method.invoke(packet);
            }
        }
        return null;
    }

    private record AccessorKey(Class<?> owner, Class<?> returnType)
    {
    }

    private record ProtocolInfos(
        Object configurationClientbound,
        Object configurationServerbound,
        Object gameClientbound,
        Object gameServerbound)
    {
    }
}

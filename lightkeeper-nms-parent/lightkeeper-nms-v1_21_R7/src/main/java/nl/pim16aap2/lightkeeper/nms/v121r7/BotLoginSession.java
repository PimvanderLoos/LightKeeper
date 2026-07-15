package nl.pim16aap2.lightkeeper.nms.v121r7;

import nl.pim16aap2.lightkeeper.nms.api.BotJoinPhase;
import nl.pim16aap2.lightkeeper.nms.api.BotLoginRequest;
import nl.pim16aap2.lightkeeper.nms.api.IBotLoginOutcome;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A single full-login attempt: builds the client-listener Proxy, drives the login/configuration/play phase
 * transitions in response to server packets, and reports the terminal outcome.
 *
 * <p>This class is the Proxy's {@link InvocationHandler}. The server-driven packet handlers run on the Netty
 * event-loop thread (via {@code Connection.channelRead0 -> packet.handle(listener)}); {@link #run(BotLoginRequest)}
 * runs on the caller's thread and blocks on a {@link CompletableFuture} until a handler completes it.
 *
 * <p>Dispatch keys on the packet <em>class</em> ({@code method.getParameterTypes()[0]}), never the method name,
 * because Spigot obfuscates every listener method to {@code a}. Unhandled packets are fail-loud in the
 * login/configuration phase (they would silently stall the join) and warn-once in the play phase.
 */
final class BotLoginSession implements InvocationHandler
{
    private static final System.Logger LOG = System.getLogger(BotLoginSession.class.getName());

    private final BotLoginReflection reflection;
    private final CompletableFuture<IBotLoginOutcome> outcome = new CompletableFuture<>();
    private final Map<Class<?>, Boolean> warnedPlayPackets = new ConcurrentHashMap<>();

    private volatile BotJoinPhase phase = BotJoinPhase.LOGIN;
    private volatile @Nullable Object connection;
    private volatile @Nullable Object listenerProxy;
    private volatile @Nullable String locale;
    private volatile String playerName = "";

    BotLoginSession(BotLoginReflection reflection)
    {
        this.reflection = reflection;
    }

    /**
     * Runs the login pipeline and blocks until it reaches the play phase, is denied, or times out.
     *
     * @param request
     *     Login inputs.
     * @return The terminal outcome.
     */
    IBotLoginOutcome run(BotLoginRequest request)
    {
        this.locale = request.locale();
        this.playerName = request.name();
        final Object proxy = reflection.newListenerProxy(this);
        this.listenerProxy = proxy;

        final Object openedConnection;
        try
        {
            openedConnection = reflection.openConnection(request.port(), proxy);
            this.connection = openedConnection;
            reflection.sendHello(openedConnection, request.name());
        }
        catch (ReflectiveOperationException exception)
        {
            throw new IllegalStateException(
                "Failed to open full-login connection for bot '%s'.".formatted(request.name()), exception);
        }

        try
        {
            final IBotLoginOutcome result = outcome.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!(result instanceof IBotLoginOutcome.Joined))
                reflection.closeConnection(openedConnection);
            return result;
        }
        catch (TimeoutException exception)
        {
            reflection.closeConnection(openedConnection);
            return new IBotLoginOutcome.TimedOut();
        }
        catch (ExecutionException exception)
        {
            reflection.closeConnection(openedConnection);
            throw new IllegalStateException(
                "Full-login pipeline failed for bot '%s': %s".formatted(request.name(), rootMessage(exception)),
                exception);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            reflection.closeConnection(openedConnection);
            throw new IllegalStateException(
                "Interrupted while awaiting full-login of bot '%s'.".formatted(request.name()), exception);
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // InvocationHandler: called by the server's packet dispatch on the Netty event-loop thread.
    // -------------------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("PMD.UseVarargs") // InvocationHandler.invoke's array parameter is fixed by the contract.
    public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] args)
        throws Throwable
    {
        if (method.getDeclaringClass() == Object.class)
            return invokeObjectMethod(proxy, method, firstArg(args));

        final Class<?> returnType = method.getReturnType();
        final Class<?>[] parameterTypes = method.getParameterTypes();

        if (parameterTypes.length == 0)
        {
            if (returnType == reflection.packetFlowClass())
                return reflection.clientboundFlow();
            if (returnType == reflection.connectionProtocolClass())
                return reflection.protocolConstant(phase);
            if (returnType == boolean.class)
                return Boolean.TRUE;   // isAcceptingMessages
        }
        else if (parameterTypes.length == 1)
        {
            // Dispatch keys on whether the single argument is a Packet, flattened to keep nesting shallow.
            final boolean isPacket = reflection.packetClass().isAssignableFrom(parameterTypes[0]);
            if (isPacket && returnType == void.class)
            {
                dispatchPacket(firstArg(args));
                return null;
            }
            if (isPacket && returnType == boolean.class)
                return Boolean.TRUE;   // shouldHandleMessage
            if (!isPacket && returnType == void.class)
            {
                handleTransportDisconnect();   // onDisconnect(DisconnectionDetails)
                return null;
            }
        }

        if (method.isDefault())
            return InvocationHandler.invokeDefault(proxy, method, args);
        return returnType == boolean.class ? Boolean.FALSE : null;
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // Proxy equality is identity by design.
    private @Nullable Object invokeObjectMethod(Object proxy, Method method, @Nullable Object other)
    {
        return switch (method.getName())
        {
            case "equals" -> proxy == other;
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "BotLoginSession$Proxy(phase=" + phase + ")";
            default -> null;
        };
    }

    private static @Nullable Object firstArg(Object @Nullable ... args)
    {
        return args == null || args.length == 0 ? null : args[0];
    }

    private static String rootMessage(Throwable throwable)
    {
        Throwable cursor = throwable;
        // Depth-capped so a self-referential cause chain cannot loop forever.
        for (int depth = 0; depth < 32 && cursor.getCause() != null; ++depth)
            cursor = cursor.getCause();
        final String message = cursor.getMessage();
        return message != null ? message : cursor.getClass().getName();
    }

    private void dispatchPacket(@Nullable Object packet)
    {
        if (packet == null || outcome.isDone())
            return;

        final Class<?> packetClass = packet.getClass();
        try
        {
            switch (reflection.classify(packetClass))
            {
                case COMPRESSION -> reflection.applyCompression(connection(), packet);
                case LOGIN_FINISHED ->
                {
                    // Advance the phase before the transition: setupInboundProtocol validates that the listener's
                    // protocol() already matches the configuration protocol it is being switched to.
                    phase = BotJoinPhase.CONFIGURATION;
                    reflection.enterConfigurationPhase(connection(), proxy(), locale);
                }
                case KNOWN_PACKS -> reflection.echoKnownPacks(connection(), packet);
                case CODE_OF_CONDUCT -> reflection.acceptCodeOfConduct(connection());
                case FINISH_CONFIGURATION ->
                {
                    phase = BotJoinPhase.PLAY;
                    reflection.enterPlayPhase(connection(), proxy());
                }
                case KEEP_ALIVE -> reflection.answerKeepAlive(connection(), packet);
                case PING -> reflection.answerPing(connection(), packet);
                case TELEPORT -> reflection.acceptTeleport(connection(), packet);
                case GAME_LOGIN -> completeJoined();
                case LOGIN_DISCONNECT, DISCONNECT -> completeDenied(packet);
                case CONSUME ->
                {
                    // Known informational packet; no response required.
                }
                case UNKNOWN -> handleUnknownPacket(packetClass);
            }
        }
        catch (ReflectiveOperationException exception)
        {
            outcome.completeExceptionally(new IllegalStateException(
                "Failed to handle %s packet %s.".formatted(phase, packetClass.getName()), exception));
        }
    }

    private void handleUnknownPacket(Class<?> packetClass)
    {
        if (phase == BotJoinPhase.PLAY)
        {
            if (warnedPlayPackets.putIfAbsent(packetClass, Boolean.TRUE) == null)
                LOG.log(System.Logger.Level.DEBUG, () -> "Ignoring unhandled play packet " + packetClass.getName());
            return;
        }

        // Fail-loud: an unhandled login/configuration packet would silently stall the join.
        outcome.completeExceptionally(new IllegalStateException(
            "Unhandled %s packet from server: %s. Add it to the login driver's packet table."
                .formatted(phase, packetClass.getName())));
    }

    private void handleTransportDisconnect()
    {
        if (!outcome.isDone())
            outcome.complete(new IBotLoginOutcome.Denied(phase, "Connection closed during " + phase + "."));
    }

    private void completeJoined()
    {
        if (!outcome.isDone())
            outcome.complete(new IBotLoginOutcome.Joined(playerName));
    }

    private void completeDenied(Object disconnectPacket)
    {
        if (!outcome.isDone())
            outcome.complete(new IBotLoginOutcome.Denied(phase, reflection.disconnectReason(disconnectPacket)));
    }

    private Object connection()
    {
        final Object current = connection;
        if (current == null)
            throw new IllegalStateException("Connection is not initialized.");
        return current;
    }

    private Object proxy()
    {
        final Object current = listenerProxy;
        if (current == null)
            throw new IllegalStateException("Listener proxy is not initialized.");
        return current;
    }
}

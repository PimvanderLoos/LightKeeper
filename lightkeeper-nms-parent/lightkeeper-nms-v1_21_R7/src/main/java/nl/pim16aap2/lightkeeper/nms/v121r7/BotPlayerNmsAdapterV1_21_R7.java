package nl.pim16aap2.lightkeeper.nms.v121r7;

import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper 1.21.11 (v1_21_R7) synthetic-player adapter.
 */
public final class BotPlayerNmsAdapterV1_21_R7 implements IBotPlayerNmsAdapter
{
    private static final int TEXT_EXTRACTION_MAX_DEPTH = 4;
    private static final int TEXT_EXTRACTION_MAX_METHODS = 24;
    private static final System.Logger LOGGER = System.getLogger(BotPlayerNmsAdapterV1_21_R7.class.getName());

    private final Object minecraftServer;
    private final Object playerList;
    private final Map<UUID, Object> playerChannels = new ConcurrentHashMap<>();

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
    private final Object serverboundPacketFlow;

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

            final Class<?> gameProfileClass = resolveClass(
                "com.mojang.authlib.GameProfile",
                serverClassLoader
            );
            final Class<?> packetFlowClass = resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.protocol.PacketFlow",
                "net.minecraft.network.protocol.EnumProtocolDirection"
            );
            final Class<?> connectionClass = resolveFirstClass(
                serverClassLoader,
                "net.minecraft.network.Connection",
                "net.minecraft.network.NetworkManager"
            );
            final Class<?> serverPlayerClass = resolveFirstClass(
                serverClassLoader,
                "net.minecraft.server.level.ServerPlayer",
                "net.minecraft.server.level.EntityPlayer"
            );
            final Class<?> commonListenerCookieClass = resolveClass(
                "net.minecraft.server.network.CommonListenerCookie",
                serverClassLoader
            );

            final Class<?> minecraftServerClass = resolveClass(
                "net.minecraft.server.MinecraftServer",
                serverClassLoader
            );
            final Class<?> serverLevelClass = resolveFirstClass(
                serverClassLoader,
                "net.minecraft.server.level.ServerLevel",
                "net.minecraft.server.level.WorldServer"
            );
            final Class<?> clientInformationClass = resolveClass(
                "net.minecraft.server.level.ClientInformation",
                serverClassLoader
            );
            final Class<?> embeddedChannelClass = resolveClass(
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

            final Class<?> craftWorldClass = resolveClass(
                craftBukkitPackage + ".CraftWorld",
                serverClassLoader
            );
            craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");

            commonListenerCreateInitialMethod = resolveStaticFactoryMethod(
                commonListenerCookieClass,
                commonListenerCookieClass,
                gameProfileClass,
                boolean.class
            );
            clientInformationCreateDefaultMethod = resolveStaticNoArgFactoryMethod(
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

            final Class<?> craftPlayerClass = resolveClass(
                craftBukkitPackage + ".entity.CraftPlayer",
                serverClassLoader
            );
            craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");
            connectionChannelField = resolveFieldByNameOrAcceptedType(connectionClass, "channel", embeddedChannelClass);
            connectionAddressField = resolveFieldByNameOrType(connectionClass, "address", SocketAddress.class);
            embeddedChannelReadOutboundMethod = embeddedChannelClass.getMethod("readOutbound");
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Failed to initialize v1_21_R7 NMS adapter.", exception);
        }
    }

    private static Class<?> resolveClass(String className, ClassLoader classLoader)
        throws ClassNotFoundException
    {
        try
        {
            return Class.forName(className, false, classLoader);
        }
        catch (ClassNotFoundException ignored)
        {
            return Class.forName(className);
        }
    }

    private static Class<?> resolveFirstClass(ClassLoader classLoader, String... classNames)
        throws ClassNotFoundException
    {
        ClassNotFoundException lastException = null;
        for (final String className : classNames)
        {
            try
            {
                return resolveClass(className, classLoader);
            }
            catch (ClassNotFoundException exception)
            {
                lastException = exception;
            }
        }
        throw Objects.requireNonNullElseGet(
            lastException,
            () -> new ClassNotFoundException("No candidate class names were provided.")
        );
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

        final Method stringMethod = findNamedNoArgMethod(packetFlowEnumClass, "b");
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
        return enumConstants[Math.max(0, enumConstants.length - 1)];
    }

    private static Object resolvePlayerList(Object minecraftServer, ClassLoader serverClassLoader)
        throws ReflectiveOperationException
    {
        final Method namedMethod = findNamedNoArgMethod(minecraftServer.getClass(), "getPlayerList");
        if (namedMethod != null)
        {
            return namedMethod.invoke(minecraftServer);
        }

        final Class<?> playerListClass = resolveClass("net.minecraft.server.players.PlayerList", serverClassLoader);

        final Method typedMethod = findNoArgMethodByReturnType(minecraftServer.getClass(), playerListClass);
        if (typedMethod != null)
        {
            return typedMethod.invoke(minecraftServer);
        }

        final Field typedField = findFieldByType(minecraftServer.getClass(), playerListClass);
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
        final Method namedMethod = findNamedMethod(
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
        return findCompatibleMethod(playerListClass, connectionClass, serverPlayerClass, commonListenerCookieClass);
    }

    private static Method resolvePlayerListRemoveMethod(Class<?> playerListClass, Class<?> serverPlayerClass)
        throws NoSuchMethodException
    {
        final Method namedMethod = findNamedMethod(playerListClass, "remove", serverPlayerClass);
        if (namedMethod != null)
        {
            return namedMethod;
        }
        return findCompatibleMethod(playerListClass, serverPlayerClass);
    }

    private static Method resolveStaticFactoryMethod(
        Class<?> ownerClass,
        Class<?> returnTypeClass,
        Class<?>... parameterTypes
    )
        throws NoSuchMethodException
    {
        for (Class<?> cursor = ownerClass; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                {
                    continue;
                }
                if (!returnTypeClass.isAssignableFrom(method.getReturnType()))
                {
                    continue;
                }
                final Class<?>[] methodParameterTypes = method.getParameterTypes();
                if (methodParameterTypes.length != parameterTypes.length)
                {
                    continue;
                }
                boolean compatible = true;
                for (int idx = 0; idx < methodParameterTypes.length; ++idx)
                {
                    if (!methodParameterTypes[idx].isAssignableFrom(parameterTypes[idx]))
                    {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible)
                {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
            "Failed to resolve static factory method on "
                + ownerClass.getName()
                + " for return type "
                + returnTypeClass.getName()
        );
    }

    private static Method resolveStaticNoArgFactoryMethod(Class<?> ownerClass, Class<?> returnTypeClass)
        throws NoSuchMethodException
    {
        return resolveStaticFactoryMethod(ownerClass, returnTypeClass);
    }

    private static Field resolveFieldByNameOrType(Class<?> ownerClass, String preferredName, Class<?> fieldType)
        throws NoSuchFieldException
    {
        try
        {
            final Field field = ownerClass.getField(preferredName);
            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException ignored)
        {
            final Field typedField = findFieldByType(ownerClass, fieldType);
            if (typedField != null)
            {
                return typedField;
            }
            throw new NoSuchFieldException(
                "Failed to resolve field '" + preferredName + "' on " + ownerClass.getName()
            );
        }
    }

    private static Field resolveFieldByNameOrAcceptedType(
        Class<?> ownerClass,
        String preferredName,
        Class<?> acceptedType
    )
        throws NoSuchFieldException
    {
        try
        {
            final Field field = ownerClass.getField(preferredName);
            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException ignored)
        {
            for (Class<?> cursor = ownerClass; cursor != null; cursor = cursor.getSuperclass())
            {
                for (final Field field : cursor.getDeclaredFields())
                {
                    if (!field.getType().isAssignableFrom(acceptedType))
                    {
                        continue;
                    }
                    field.setAccessible(true);
                    return field;
                }
            }
            throw new NoSuchFieldException(
                "Failed to resolve field '" + preferredName + "' on " + ownerClass.getName()
            );
        }
    }

    private static @Nullable Method findNamedNoArgMethod(Class<?> type, String methodName)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            try
            {
                final Method method = cursor.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                // Continue searching in super class.
            }
        }
        return null;
    }

    private static @Nullable Method findNoArgMethodByReturnType(Class<?> type, Class<?> returnType)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (method.getParameterCount() != 0 || !returnType.isAssignableFrom(method.getReturnType()))
                {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static @Nullable Field findFieldByType(Class<?> type, Class<?> fieldType)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Field field : cursor.getDeclaredFields())
            {
                if (!fieldType.isAssignableFrom(field.getType()))
                {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static @Nullable Method findNamedMethod(Class<?> type, String methodName, Class<?>... parameterTypes)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            try
            {
                final Method method = cursor.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                // Continue searching in super class.
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, Class<?>... argumentTypes)
        throws NoSuchMethodException
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != argumentTypes.length)
                {
                    continue;
                }

                boolean compatible = true;
                for (int idx = 0; idx < parameterTypes.length; ++idx)
                {
                    if (!parameterTypes[idx].isAssignableFrom(argumentTypes[idx]))
                    {
                        compatible = false;
                        break;
                    }
                }

                if (!compatible)
                {
                    continue;
                }

                method.setAccessible(true);
                return method;
            }
        }

        throw new NoSuchMethodException(
            "Failed to locate compatible method on "
                + type.getName()
                + " with parameter types "
                + List.of(argumentTypes)
        );
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
            final Throwable rootCause = exception;
            throw new IllegalStateException(
                "Failed to spawn synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(name, uuid, rootCause.getClass().getName(), rootCause.getMessage()),
                exception
            );
        }
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
            playerChannels.remove(player.getUniqueId());
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
            final Throwable rootCause = exception;
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

        final List<String> messages = new ArrayList<>();
        try
        {
            Object outboundPacket;
            while ((outboundPacket = embeddedChannelReadOutboundMethod.invoke(embeddedChannel)) != null)
            {
                final String message = extractText(outboundPacket, TEXT_EXTRACTION_MAX_DEPTH, new IdentityHashMap<>());
                if (message != null && !message.isBlank())
                    messages.add(message);
            }
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                "Failed to drain received messages for synthetic player '%s'."
                    .formatted(playerId),
                exception
            );
        }
        return List.copyOf(messages);
    }

    private static @Nullable String extractText(
        @Nullable Object value,
        int depth,
        IdentityHashMap<Object, Boolean> seen)
    {
        if (value == null || depth < 0 || seen.put(value, Boolean.TRUE) != null)
            return null;

        switch (value)
        {
            case String stringValue ->
            {
                return stringValue;
            }
            case Optional<?> optional ->
            {
                return optional.map(inner -> extractText(inner, depth - 1, seen)).orElse(null);
            }
            case Collection<?> collection ->
            {
                for (final Object element : collection)
                {
                    final String extracted = extractText(element, depth - 1, seen);
                    if (extracted != null && !extracted.isBlank())
                        return extracted;
                }
                return null;
            }
            default ->
            {
            }
        }
        if (value.getClass().isArray())
        {
            final int arrayLength = Array.getLength(value);
            for (int index = 0; index < arrayLength; ++index)
            {
                final String extracted = extractText(Array.get(value, index), depth - 1, seen);
                if (extracted != null && !extracted.isBlank())
                    return extracted;
            }
            return null;
        }

        // net.minecraft.network.chat.Component has getString().
        final String directText = invokeStringMethod(value, "getString");
        if (directText != null && !directText.isBlank())
            return directText;

        int inspectedMethodCount = 0;
        for (final Method method : value.getClass().getMethods())
        {
            if (!isSafeTextAccessor(method))
                continue;
            if (inspectedMethodCount >= TEXT_EXTRACTION_MAX_METHODS)
                break;
            ++inspectedMethodCount;

            try
            {
                final Object nestedValue = method.invoke(value);
                final String extracted = extractText(nestedValue, depth - 1, seen);
                if (extracted != null && !extracted.isBlank())
                    return extracted;
            }
            catch (Exception exception)
            {
                LOGGER.log(
                    System.Logger.Level.TRACE,
                    "Ignoring reflective accessor failure while extracting packet text.",
                    exception
                );
            }
        }
        return null;
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

    private static @Nullable String invokeStringMethod(Object target, String methodName)
    {
        try
        {
            final Method method = target.getClass().getMethod(methodName);
            if (method.getReturnType() != String.class)
                return null;
            return (String) method.invoke(target);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }
}

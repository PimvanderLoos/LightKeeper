package nl.pim16aap2.lightkeeper.nms.v1_21_r6;

import nl.pim16aap2.lightkeeper.nms.api.BotPlayerNmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper 1.21.11 (v1_21_R6) synthetic-player adapter.
 */
public final class BotPlayerNmsAdapterV1_21_R6 implements BotPlayerNmsAdapter
{
    private static final int TEXT_EXTRACTION_MAX_DEPTH = 4;

    private final Object minecraftServer;
    private final Object playerList;
    private final Map<UUID, Object> playerChannels = new ConcurrentHashMap<>();

    private final Class<?> gameProfileClass;
    private final Class<?> packetFlowClass;
    private final Class<?> connectionClass;
    private final Class<?> serverPlayerClass;
    private final Class<?> commonListenerCookieClass;
    private final Class<?> clientInformationClass;

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

    public BotPlayerNmsAdapterV1_21_R6()
    {
        try
        {
            final Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            final Method craftServerGetServerMethod = craftServerClass.getMethod("getServer");
            final Object craftServer = Bukkit.getServer();
            minecraftServer = craftServerGetServerMethod.invoke(craftServer);

            final Method minecraftServerGetPlayerListMethod = minecraftServer.getClass().getMethod("getPlayerList");
            playerList = minecraftServerGetPlayerListMethod.invoke(minecraftServer);

            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
            connectionClass = Class.forName("net.minecraft.network.Connection");
            serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            commonListenerCookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");

            final Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            final Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
            final Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");

            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            connectionConstructor = connectionClass.getConstructor(packetFlowClass);
            embeddedChannelConstructor = embeddedChannelClass.getConstructor();
            serverPlayerConstructor = serverPlayerClass.getConstructor(
                minecraftServerClass,
                serverLevelClass,
                gameProfileClass,
                clientInformationClass
            );

            final Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");

            commonListenerCreateInitialMethod = commonListenerCookieClass.getMethod(
                "createInitial",
                gameProfileClass,
                boolean.class
            );
            clientInformationCreateDefaultMethod = clientInformationClass.getMethod("createDefault");
            playerListPlaceNewPlayerMethod = playerList.getClass().getMethod(
                "placeNewPlayer",
                connectionClass,
                serverPlayerClass,
                commonListenerCookieClass
            );
            playerListRemoveMethod = playerList.getClass().getMethod("remove", serverPlayerClass);
            serverPlayerGetBukkitEntityMethod = serverPlayerClass.getMethod("getBukkitEntity");

            final Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");
            connectionChannelField = connectionClass.getField("channel");
            connectionAddressField = connectionClass.getField("address");
            embeddedChannelReadOutboundMethod = embeddedChannelClass.getMethod("readOutbound");
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Failed to initialize v1_21_R6 NMS adapter.", exception);
        }
    }

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

            @SuppressWarnings("unchecked")
            final Object serverboundPacketFlow = Enum.valueOf(
                (Class<Enum>) packetFlowClass.asSubclass(Enum.class),
                "SERVERBOUND"
            );
            final Object connection = connectionConstructor.newInstance(serverboundPacketFlow);
            final Object embeddedChannel = embeddedChannelConstructor.newInstance();
            connectionChannelField.set(connection, embeddedChannel);
            connectionAddressField.set(connection, new InetSocketAddress("127.0.0.1", 0));
            final Object listenerCookie = commonListenerCreateInitialMethod.invoke(null, gameProfile, false);

            playerListPlaceNewPlayerMethod.invoke(playerList, connection, serverPlayer, listenerCookie);

            final Player bukkitPlayer = (Player) serverPlayerGetBukkitEntityMethod.invoke(serverPlayer);
            bukkitPlayer.teleport(spawnLocation);
            playerChannels.put(uuid, embeddedChannel);
            return bukkitPlayer;
        }
        catch (Exception exception)
        {
            final Throwable rootCause = exception instanceof java.lang.reflect.InvocationTargetException invocationTargetException
                ? Objects.requireNonNullElse(invocationTargetException.getTargetException(), exception)
                : exception;
            throw new IllegalStateException(
                "Failed to spawn synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(name, uuid, rootCause.getClass().getName(), rootCause.getMessage()),
                exception
            );
        }
    }

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
        catch (Exception exception)
        {
            final Throwable rootCause = exception instanceof java.lang.reflect.InvocationTargetException invocationTargetException
                ? Objects.requireNonNullElse(invocationTargetException.getTargetException(), exception)
                : exception;
            throw new IllegalStateException(
                "Failed to remove synthetic player '%s' (%s). Cause: %s: %s"
                    .formatted(player.getName(), player.getUniqueId(), rootCause.getClass().getName(), rootCause.getMessage()),
                exception
            );
        }
    }

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

    private static String extractText(Object value, int depth, IdentityHashMap<Object, Boolean> seen)
    {
        if (value == null || depth < 0 || seen.put(value, Boolean.TRUE) != null)
            return null;

        if (value instanceof String stringValue)
            return stringValue;
        if (value instanceof Optional<?> optional)
            return optional.map(inner -> extractText(inner, depth - 1, seen)).orElse(null);
        if (value instanceof Collection<?> collection)
        {
            for (Object element : collection)
            {
                final String extracted = extractText(element, depth - 1, seen);
                if (extracted != null && !extracted.isBlank())
                    return extracted;
            }
            return null;
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

        for (Method method : value.getClass().getMethods())
        {
            if (method.getParameterCount() != 0)
                continue;
            final String methodName = method.getName();
            if (methodName.equals("getClass") || methodName.equals("hashCode") || methodName.equals("toString"))
                continue;

            try
            {
                final Object nestedValue = method.invoke(value);
                final String extracted = extractText(nestedValue, depth - 1, seen);
                if (extracted != null && !extracted.isBlank())
                    return extracted;
            }
            catch (Exception ignored)
            {
            }
        }
        return null;
    }

    private static String invokeStringMethod(Object target, String methodName)
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

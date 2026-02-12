package nl.pim16aap2.lightkeeper.nms.v1_21_r6;

import nl.pim16aap2.lightkeeper.nms.api.BotPlayerNmsAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper 1.21.11 (v1_21_R6) synthetic-player adapter.
 */
public final class BotPlayerNmsAdapterV1_21_R6 implements BotPlayerNmsAdapter
{
    private final Object minecraftServer;
    private final Object playerList;

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
            connectionChannelField.set(connection, embeddedChannelConstructor.newInstance());
            connectionAddressField.set(connection, new InetSocketAddress("127.0.0.1", 0));
            final Object listenerCookie = commonListenerCreateInitialMethod.invoke(null, gameProfile, false);

            playerListPlaceNewPlayerMethod.invoke(playerList, connection, serverPlayer, listenerCookie);

            final Player bukkitPlayer = (Player) serverPlayerGetBukkitEntityMethod.invoke(serverPlayer);
            bukkitPlayer.teleport(spawnLocation);
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
}

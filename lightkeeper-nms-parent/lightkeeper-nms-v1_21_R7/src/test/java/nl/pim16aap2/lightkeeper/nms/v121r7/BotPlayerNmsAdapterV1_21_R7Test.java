package nl.pim16aap2.lightkeeper.nms.v121r7;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotPlayerNmsAdapterV1_21_R7Test
{
    @Test
    void resolveClass_shouldResolveKnownClass()
        throws Exception
    {
        // execute
        final Class<?> resolvedClass = (Class<?>) invokeStatic(
            "resolveClass",
            new Class<?>[]{String.class, ClassLoader.class},
            "java.lang.String",
            getClass().getClassLoader()
        );

        // verify
        assertThat(resolvedClass).isEqualTo(String.class);
    }

    @Test
    void resolveFirstClass_shouldReturnFirstAvailableClass()
        throws Exception
    {
        // execute
        final Class<?> resolvedClass = (Class<?>) invokeStatic(
            "resolveFirstClass",
            new Class<?>[]{ClassLoader.class, String[].class},
            getClass().getClassLoader(),
            new String[]{"missing.DoesNotExist", "java.lang.Integer"}
        );

        // verify
        assertThat(resolvedClass).isEqualTo(Integer.class);
    }

    @Test
    void resolveFirstClass_shouldThrowExceptionWhenNoCandidateExists()
    {
        // execute + verify
        assertThatThrownBy(() -> invokeStatic(
            "resolveFirstClass",
            new Class<?>[]{ClassLoader.class, String[].class},
            getClass().getClassLoader(),
            new String[]{"missing.AlsoMissing"}
        ))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void resolveServerboundPacketFlow_shouldResolveByEnumName()
        throws Exception
    {
        // execute
        final Object resolved = invokeStatic(
            "resolveServerboundPacketFlow",
            new Class<?>[]{Class.class},
            PacketFlowEnum.class
        );

        // verify
        assertThat(resolved).isEqualTo(PacketFlowEnum.SERVERBOUND);
    }

    @Test
    void extractText_shouldResolveTextFromNestedValues()
        throws Exception
    {
        // setup
        final Object nested = List.of(
            Optional.empty(),
            Optional.of(new MessageWrapper("Hello"))
        );

        // execute
        final String extracted = (String) invokeStatic(
            "extractText",
            new Class<?>[]{Object.class, int.class, IdentityHashMap.class},
            nested,
            4,
            new IdentityHashMap<>()
        );

        // verify
        assertThat(extracted).isEqualTo("Hello");
    }

    @Test
    void extractText_shouldReturnNullForBlankOrCyclicValues()
        throws Exception
    {
        // setup
        final List<Object> cyclic = new java.util.ArrayList<>();
        cyclic.add(cyclic);

        // execute
        final String extracted = (String) invokeStatic(
            "extractText",
            new Class<?>[]{Object.class, int.class, IdentityHashMap.class},
            cyclic,
            4,
            new IdentityHashMap<>()
        );

        // verify
        assertThat(extracted).isNull();
    }

    @Test
    void helperFindMethods_shouldResolveExpectedMembers()
        throws Exception
    {
        // execute
        final Method namedNoArg = (Method) invokeStatic(
            "findNamedNoArgMethod",
            new Class<?>[]{Class.class, String.class},
            MethodFixture.class,
            "getValue"
        );
        final Method byReturnType = (Method) invokeStatic(
            "findNoArgMethodByReturnType",
            new Class<?>[]{Class.class, Class.class},
            MethodFixture.class,
            CharSequence.class
        );
        final Method namedMethod = (Method) invokeStatic(
            "findNamedMethod",
            new Class<?>[]{Class.class, String.class, Class[].class},
            MethodFixture.class,
            "setValue",
            new Class<?>[]{String.class}
        );
        final Method compatibleMethod = (Method) invokeStatic(
            "findCompatibleMethod",
            new Class<?>[]{Class.class, Class[].class},
            MethodFixture.class,
            new Class<?>[]{String.class}
        );

        // verify
        assertThat(namedNoArg).isNotNull();
        assertThat(byReturnType).isNotNull();
        assertThat(namedMethod).isNotNull();
        assertThat(compatibleMethod).isNotNull();
    }

    @Test
    void helperFindFieldMethods_shouldResolveExpectedFields()
        throws Exception
    {
        // execute
        final Field byType = (Field) invokeStatic(
            "findFieldByType",
            new Class<?>[]{Class.class, Class.class},
            FieldFixture.class,
            CharSequence.class
        );
        final Field byNameOrType = (Field) invokeStatic(
            "resolveFieldByNameOrType",
            new Class<?>[]{Class.class, String.class, Class.class},
            FieldFixture.class,
            "missing",
            CharSequence.class
        );
        final Field byAcceptedType = (Field) invokeStatic(
            "resolveFieldByNameOrAcceptedType",
            new Class<?>[]{Class.class, String.class, Class.class},
            FieldFixture.class,
            "missing",
            String.class
        );

        // verify
        assertThat(byType).isNotNull();
        assertThat(byNameOrType).isNotNull();
        assertThat(byAcceptedType).isNotNull();
    }

    @Test
    void helperMethods_shouldHandleFallbackAndFailureBranches()
        throws Exception
    {
        // setup
        final Object fallbackPacketFlow = invokeStatic(
            "resolveServerboundPacketFlow",
            new Class<?>[]{Class.class},
            PacketFlowWithoutServerbound.class
        );
        final String extractedFromArray = (String) invokeStatic(
            "extractText",
            new Class<?>[]{Object.class, int.class, IdentityHashMap.class},
            new Object[]{new MessageWrapper("from-array")},
            4,
            new IdentityHashMap<>()
        );
        final String stringFromMissingMethod = (String) invokeStatic(
            "invokeStringMethod",
            new Class<?>[]{Object.class, String.class},
            new Object(),
            "missing"
        );
        final boolean safeAccessor = (boolean) invokeStatic(
            "isSafeTextAccessor",
            new Class<?>[]{Method.class},
            AccessorFixture.class.getMethod("message")
        );
        final boolean unsafeAccessor = (boolean) invokeStatic(
            "isSafeTextAccessor",
            new Class<?>[]{Method.class},
            AccessorFixture.class.getMethod("primitiveValue")
        );

        // execute + verify
        assertThat(fallbackPacketFlow).isEqualTo(PacketFlowWithoutServerbound.B);
        assertThat(extractedFromArray).isEqualTo("from-array");
        assertThat(stringFromMissingMethod).isNull();
        assertThat(safeAccessor).isTrue();
        assertThat(unsafeAccessor).isFalse();
    }

    @Test
    void helperMethods_shouldThrowExceptionWhenLookupCannotResolveMember()
    {
        // execute + verify
        assertThatThrownBy(() -> invokeStatic(
            "findCompatibleMethod",
            new Class<?>[]{Class.class, Class[].class},
            NoCompatibleMethodFixture.class,
            new Class<?>[]{String.class, String.class}
        ))
            .isInstanceOf(NoSuchMethodException.class);

        assertThatThrownBy(() -> invokeStatic(
            "resolveFieldByNameOrType",
            new Class<?>[]{Class.class, String.class, Class.class},
            NoMatchingFieldFixture.class,
            "missing",
            String.class
        ))
            .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void drainReceivedMessages_shouldDrainNonBlankMessagesFromEmbeddedChannel()
        throws Exception
    {
        // setup
        final UUID playerId = UUID.randomUUID();
        final TestChannel channel = new TestChannel("first", " ", "second");
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "playerChannels", new ConcurrentHashMap<>(Map.of(playerId, channel)));
        setField(adapter, "embeddedChannelReadOutboundMethod", TestChannel.class.getMethod("readOutbound"));

        // execute
        final List<String> messages = adapter.drainReceivedMessages(playerId);

        // verify
        assertThat(messages).containsExactly("first", "second");
    }

    @Test
    void removePlayer_shouldRemoveMappedChannel()
        throws Exception
    {
        // setup
        final UUID playerId = UUID.randomUUID();
        final FakeServerPlayer serverPlayer = new FakeServerPlayer(
            new FakeMinecraftServer(),
            new FakeServerLevel(),
            new FakeGameProfile(playerId, "bot"),
            new FakeClientInformation()
        );
        final org.bukkit.entity.Player craftPlayer = mock(org.bukkit.entity.Player.class);
        when(craftPlayer.getUniqueId()).thenReturn(playerId);
        when(craftPlayer.getName()).thenReturn("bot");
        final FakePlayerList playerList = new FakePlayerList();
        final Map<UUID, Object> channels = new ConcurrentHashMap<>(Map.of(playerId, new Object()));

        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        StaticAccessors.serverPlayerHandle = serverPlayer;
        setField(adapter, "craftPlayerGetHandleMethod", StaticAccessors.class.getMethod("playerHandle"));
        setField(adapter, "playerListRemoveMethod", FakePlayerList.class.getMethod("remove", FakeServerPlayer.class));
        setField(adapter, "playerList", playerList);
        setField(adapter, "playerChannels", channels);

        // execute
        adapter.removePlayer(craftPlayer);

        // verify
        assertThat(channels).isEmpty();
        assertThat(playerList.removedPlayer).isSameAs(serverPlayer);
    }

    @Test
    void spawnPlayer_shouldCreateAndRegisterPlayerWhenHandlesAreConfigured()
        throws Exception
    {
        // setup
        final UUID playerId = UUID.randomUUID();
        final org.bukkit.World world = mock(org.bukkit.World.class);
        final org.bukkit.Location location = mock(org.bukkit.Location.class);
        final org.bukkit.entity.Player bukkitPlayer = mock(org.bukkit.entity.Player.class);
        when(bukkitPlayer.teleport(location)).thenReturn(true);

        final FakeMinecraftServer server = new FakeMinecraftServer();
        final FakePlayerList playerList = new FakePlayerList();
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "minecraftServer", server);
        setField(adapter, "playerList", playerList);
        setField(adapter, "playerChannels", new ConcurrentHashMap<>());

        setField(adapter, "gameProfileConstructor", FakeGameProfile.class.getConstructor(UUID.class, String.class));
        setField(adapter, "connectionConstructor", FakeConnection.class.getConstructor(FakePacketFlow.class));
        setField(adapter, "embeddedChannelConstructor", FakeEmbeddedChannel.class.getConstructor());
        setField(adapter, "serverPlayerConstructor", FakeServerPlayer.class.getConstructor(
            FakeMinecraftServer.class,
            FakeServerLevel.class,
            FakeGameProfile.class,
            FakeClientInformation.class
        ));
        StaticAccessors.serverLevelHandle = new FakeServerLevel();
        setField(adapter, "craftWorldGetHandleMethod", StaticAccessors.class.getMethod("worldHandle"));
        setField(adapter, "commonListenerCreateInitialMethod", FakeCookieFactory.class.getMethod(
            "createInitial",
            FakeGameProfile.class,
            boolean.class
        ));
        setField(adapter, "playerListPlaceNewPlayerMethod", FakePlayerList.class.getMethod(
            "placeNewPlayer",
            FakeConnection.class,
            FakeServerPlayer.class,
            Object.class
        ));
        setField(adapter, "serverPlayerGetBukkitEntityMethod", FakeServerPlayer.class.getMethod("getBukkitEntity"));
        setField(adapter, "clientInformationCreateDefaultMethod", FakeClientInformation.class.getMethod("createDefault"));
        setField(adapter, "connectionChannelField", FakeConnection.class.getField("channel"));
        setField(adapter, "connectionAddressField", FakeConnection.class.getField("address"));
        setField(adapter, "serverboundPacketFlow", FakePacketFlow.SERVERBOUND);
        setField(adapter, "embeddedChannelReadOutboundMethod", FakeEmbeddedChannel.class.getMethod("readOutbound"));

        FakeServerPlayer.returningBukkitPlayer = bukkitPlayer;

        // execute
        final org.bukkit.entity.Player created = adapter.spawnPlayer(playerId, "bot", world, location);

        // verify
        assertThat(created).isSameAs(bukkitPlayer);
        assertThat(playerList.placedPlayer).isNotNull();
        verify(bukkitPlayer).teleport(location);
    }

    @SuppressWarnings("unchecked")
    private static BotPlayerNmsAdapterV1_21_R7 allocateAdapter()
        throws Exception
    {
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        final Object unsafe = theUnsafeField.get(null);
        final Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (BotPlayerNmsAdapterV1_21_R7) allocateInstance.invoke(unsafe, BotPlayerNmsAdapterV1_21_R7.class);
    }

    private static void setField(Object target, String name, Object value)
        throws Exception
    {
        final Field field = BotPlayerNmsAdapterV1_21_R7.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... arguments)
        throws Exception
    {
        final Method method = BotPlayerNmsAdapterV1_21_R7.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try
        {
            return method.invoke(null, arguments);
        }
        catch (InvocationTargetException exception)
        {
            final Throwable cause = exception.getCause();
            if (cause instanceof Exception wrappedException)
                throw wrappedException;
            if (cause instanceof Error wrappedError)
                throw wrappedError;
            throw exception;
        }
    }

    private enum PacketFlowEnum
    {
        CLIENTBOUND,
        SERVERBOUND
    }

    private enum PacketFlowWithoutServerbound
    {
        A,
        B
    }

    private static final class MessageWrapper
    {
        private final String text;

        private MessageWrapper(String text)
        {
            this.text = text;
        }

        @SuppressWarnings("unused")
        public String getString()
        {
            return text;
        }
    }

    private static final class MethodFixture
    {
        private String value = "x";

        @SuppressWarnings("unused")
        public String getValue()
        {
            return value;
        }

        @SuppressWarnings("unused")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    private static final class FieldFixture
    {
        @SuppressWarnings("unused")
        private CharSequence value = "x";
    }

    private static final class AccessorFixture
    {
        @SuppressWarnings("unused")
        public String message()
        {
            return "hello";
        }

        @SuppressWarnings("unused")
        public int primitiveValue()
        {
            return 1;
        }
    }

    private static final class NoCompatibleMethodFixture
    {
        @SuppressWarnings("unused")
        public void onlyInt(int value)
        {
            // no-op
        }
    }

    private static final class NoMatchingFieldFixture
    {
        @SuppressWarnings("unused")
        private int id = 1;
    }

    public static final class TestChannel
    {
        private final Object[] values;
        private int index;

        private TestChannel(Object... values)
        {
            this.values = values;
        }

        public @Nullable Object readOutbound()
        {
            return index < values.length ? values[index++] : null;
        }
    }

    public static final class FakeMinecraftServer
    {
    }

    public static final class FakeServerLevel
    {
    }

    public record FakeGameProfile(UUID id, String name)
    {
    }

    public enum FakePacketFlow
    {
        SERVERBOUND
    }

    public static final class FakeConnection
    {
        public Object channel = new Object();
        public Object address = new Object();

        public FakeConnection(FakePacketFlow packetFlow)
        {
            // no-op
        }
    }

    public static final class FakeEmbeddedChannel
    {
        public @Nullable Object readOutbound()
        {
            return null;
        }
    }

    public static final class FakeClientInformation
    {
        public static FakeClientInformation createDefault()
        {
            return new FakeClientInformation();
        }
    }

    public static final class FakeCookieFactory
    {
        public static Object createInitial(FakeGameProfile profile, boolean transferred)
        {
            return new Object();
        }
    }

    public static final class FakeServerPlayer
    {
        private static @Nullable Player returningBukkitPlayer;

        public FakeServerPlayer(
            FakeMinecraftServer server,
            FakeServerLevel level,
            FakeGameProfile profile,
            FakeClientInformation clientInformation)
        {
            // no-op
        }

        public @Nullable Player getBukkitEntity()
        {
            return returningBukkitPlayer;
        }
    }

    public static final class FakePlayerList
    {
        private @Nullable FakeServerPlayer removedPlayer;
        private @Nullable FakeServerPlayer placedPlayer;

        public void remove(FakeServerPlayer serverPlayer)
        {
            this.removedPlayer = serverPlayer;
        }

        public void placeNewPlayer(FakeConnection connection, FakeServerPlayer serverPlayer, Object cookie)
        {
            this.placedPlayer = serverPlayer;
        }
    }

    public static final class StaticAccessors
    {
        private static @Nullable FakeServerLevel serverLevelHandle;
        private static @Nullable FakeServerPlayer serverPlayerHandle;

        public static @Nullable FakeServerLevel worldHandle()
        {
            return serverLevelHandle;
        }

        public static @Nullable FakeServerPlayer playerHandle()
        {
            return serverPlayerHandle;
        }
    }
}

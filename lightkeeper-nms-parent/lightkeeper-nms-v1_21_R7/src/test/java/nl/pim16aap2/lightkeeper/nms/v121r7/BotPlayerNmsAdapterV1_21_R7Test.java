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
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
        final Class<?> resolvedClass = (Class<?>) invokeStaticOnUtils(
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
        final Class<?> resolvedClass = (Class<?>) invokeStaticOnUtils(
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
        assertThatThrownBy(() -> invokeStaticOnUtils(
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
        final Method namedNoArg = (Method) invokeStaticOnUtils(
            "findNamedNoArgMethod",
            new Class<?>[]{Class.class, String.class},
            MethodFixture.class,
            "getValue"
        );
        final Method byReturnType = (Method) invokeStaticOnUtils(
            "findNoArgMethodByReturnType",
            new Class<?>[]{Class.class, Class.class},
            MethodFixture.class,
            CharSequence.class
        );
        final Method namedMethod = (Method) invokeStaticOnUtils(
            "findNamedMethod",
            new Class<?>[]{Class.class, String.class, Class[].class},
            MethodFixture.class,
            "setValue",
            new Class<?>[]{String.class}
        );
        final Method compatibleMethod = (Method) invokeStaticOnUtils(
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
        final Field byType = (Field) invokeStaticOnUtils(
            "findFieldByType",
            new Class<?>[]{Class.class, Class.class},
            FieldFixture.class,
            CharSequence.class
        );
        final Field byNameOrType = (Field) invokeStaticOnUtils(
            "resolveFieldByNameOrType",
            new Class<?>[]{Class.class, String.class, Class.class},
            FieldFixture.class,
            "missing",
            CharSequence.class
        );
        final Field byAcceptedType = (Field) invokeStaticOnUtils(
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
        final String stringFromMissingMethod = (String) invokeStaticOnUtils(
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
        assertThatThrownBy(() -> invokeStaticOnUtils(
            "findCompatibleMethod",
            new Class<?>[]{Class.class, Class[].class},
            NoCompatibleMethodFixture.class,
            new Class<?>[]{String.class, String.class}
        ))
            .isInstanceOf(NoSuchMethodException.class);

        assertThatThrownBy(() -> invokeStaticOnUtils(
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
        setField(adapter, "playerMessageQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "playerChatComponentQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "embeddedChannelReadOutboundMethod", TestChannel.class.getMethod("readOutbound"));

        // execute
        final List<String> messages = adapter.drainReceivedMessages(playerId);

        // verify
        assertThat(messages).containsExactly("first", "second");
    }

    @Test
    void drains_shouldReturnEmptyListsWhenPlayerHasNoChannel()
        throws Exception
    {
        // setup
        final UUID playerId = UUID.randomUUID();
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "playerChannels", new ConcurrentHashMap<UUID, Object>());

        // execute
        final List<String> messages = adapter.drainReceivedMessages(playerId);
        final List<String> components = adapter.drainChatComponents(playerId);

        // verify
        assertThat(messages).isEmpty();
        assertThat(components).isEmpty();
    }

    @Test
    @SuppressWarnings("NullAway")
    void drains_shouldRejectNullPlayerId()
        throws Exception
    {
        // setup
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();

        // execute + verify
        assertThatThrownBy(() -> adapter.drainReceivedMessages(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("playerId");
        assertThatThrownBy(() -> adapter.drainChatComponents(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("playerId");
    }

    @Test
    void drainReceivedMessages_shouldWrapChannelReadFailures()
        throws Exception
    {
        // setup
        final UUID playerId = UUID.randomUUID();
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "playerChannels", new ConcurrentHashMap<>(Map.of(playerId, new FailingChannel())));
        setField(adapter, "playerMessageQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "playerChatComponentQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "embeddedChannelReadOutboundMethod", FailingChannel.class.getMethod("readOutbound"));

        // execute + verify
        assertThatThrownBy(() -> adapter.drainReceivedMessages(playerId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to drain outbound packets");
    }

    @Test
    void drains_shouldKeepMessageAndComponentQueuesIndependent()
        throws Exception
    {
        // setup
        final UUID playerId = UUID.randomUUID();
        final TestChannel channel = new TestChannel(new ComponentPacket(new FakeComponent("hello")));
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "playerChannels", new ConcurrentHashMap<>(Map.of(playerId, channel)));
        setField(adapter, "playerMessageQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "playerChatComponentQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "embeddedChannelReadOutboundMethod", TestChannel.class.getMethod("readOutbound"));
        setField(adapter, "componentJsonCodec", new FakeCodec());
        setField(adapter, "componentJsonOps", new Object());
        setField(adapter, "componentCodecEncodeStartMethod", FakeCodec.class.getMethod(
            "encodeStart",
            Object.class,
            Object.class
        ));
        setField(adapter, "dataResultResultMethod", FakeDataResult.class.getMethod("result"));

        // execute
        final List<String> messages = adapter.drainReceivedMessages(playerId);
        final List<String> components = adapter.drainChatComponents(playerId);

        // verify
        assertThat(messages).containsExactly("hello");
        assertThat(components).containsExactly("{\"text\":\"hello\"}");
    }

    @Test
    void extractComponentJson_shouldInspectOnlySafeAccessors()
        throws Exception
    {
        // setup
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "componentJsonCodec", new FakeCodec());
        setField(adapter, "componentJsonOps", new Object());
        setField(adapter, "componentCodecEncodeStartMethod", FakeCodec.class.getMethod(
            "encodeStart",
            Object.class,
            Object.class
        ));
        setField(adapter, "dataResultResultMethod", FakeDataResult.class.getMethod("result"));
        final boolean safeComponentAccessor = (boolean) invokeStatic(
            "isSafeComponentAccessor",
            new Class<?>[]{Method.class},
            ComponentPacket.class.getMethod("getComponent")
        );
        final boolean unsafeComponentAccessor = (boolean) invokeStatic(
            "isSafeComponentAccessor",
            new Class<?>[]{Method.class},
            UnsafeComponentPacket.class.getMethod("value")
        );

        // execute
        final String extracted = (String) invokeInstance(
            adapter,
            "extractComponentJson",
            new Class<?>[]{Object.class, int.class, IdentityHashMap.class},
            new ComponentPacket(new FakeComponent("hello")),
            4,
            new IdentityHashMap<>()
        );

        // verify
        assertThat(extracted).isEqualTo("{\"text\":\"hello\"}");
        assertThat(safeComponentAccessor).isTrue();
        assertThat(unsafeComponentAccessor).isFalse();
    }

    @Test
    void extractComponentJson_shouldReturnNullWhenComponentSerializationFails()
        throws Exception
    {
        // setup
        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        setField(adapter, "componentJsonCodec", new ThrowingCodec());
        setField(adapter, "componentJsonOps", new Object());
        setField(adapter, "componentCodecEncodeStartMethod", ThrowingCodec.class.getMethod(
            "encodeStart",
            Object.class,
            Object.class
        ));
        setField(adapter, "dataResultResultMethod", FakeDataResult.class.getMethod("result"));

        // execute
        final String extracted = (String) invokeInstance(
            adapter,
            "extractComponentJson",
            new Class<?>[]{Object.class, int.class, IdentityHashMap.class},
            new FakeComponent("hello"),
            4,
            new IdentityHashMap<>()
        );

        // verify
        assertThat(extracted).isNull();
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
        final Map<UUID, Queue<String>> messageQueues = new ConcurrentHashMap<>(
            Map.of(playerId, new ConcurrentLinkedQueue<>(List.of("message")))
        );
        final Map<UUID, Queue<String>> componentQueues = new ConcurrentHashMap<>(
            Map.of(playerId, new ConcurrentLinkedQueue<>(List.of("{}")))
        );

        final BotPlayerNmsAdapterV1_21_R7 adapter = allocateAdapter();
        StaticAccessors.serverPlayerHandle = serverPlayer;
        setField(adapter, "craftPlayerGetHandleMethod", StaticAccessors.class.getMethod("playerHandle"));
        setField(adapter, "playerListRemoveMethod", FakePlayerList.class.getMethod("remove", FakeServerPlayer.class));
        setField(adapter, "playerList", playerList);
        setField(adapter, "playerChannels", channels);
        setField(adapter, "playerMessageQueues", messageQueues);
        setField(adapter, "playerChatComponentQueues", componentQueues);

        // execute
        adapter.removePlayer(craftPlayer);

        // verify
        assertThat(channels).isEmpty();
        assertThat(messageQueues).isEmpty();
        assertThat(componentQueues).isEmpty();
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
        setField(adapter, "playerMessageQueues", new ConcurrentHashMap<UUID, Queue<String>>());
        setField(adapter, "playerChatComponentQueues", new ConcurrentHashMap<UUID, Queue<String>>());

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
        return invokeStaticOn(BotPlayerNmsAdapterV1_21_R7.class, methodName, parameterTypes, arguments);
    }

    private static Object invokeStaticOnUtils(String methodName, Class<?>[] parameterTypes, Object... arguments)
        throws Exception
    {
        return invokeStaticOn(NmsReflectionUtils.class, methodName, parameterTypes, arguments);
    }

    private static Object invokeStaticOn(
        Class<?> targetClass,
        String methodName,
        Class<?>[] parameterTypes,
        Object... arguments)
        throws Exception
    {
        final Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
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

    private static Object invokeInstance(
        Object target,
        String methodName,
        Class<?>[] parameterTypes,
        Object... arguments)
        throws Exception
    {
        final Method method = BotPlayerNmsAdapterV1_21_R7.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try
        {
            return method.invoke(target, arguments);
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

    public static final class FailingChannel
    {
        @SuppressWarnings("unused")
        public @Nullable Object readOutbound()
        {
            if (System.nanoTime() >= 0L)
                throw new IllegalStateException("boom");
            return null;
        }
    }

    public record ComponentPacket(FakeComponent component)
    {
        @SuppressWarnings("unused")
        public FakeComponent getComponent()
        {
            return component;
        }
    }

    public record UnsafeComponentPacket(FakeComponent value)
    {
    }

    public record FakeComponent(String text)
    {
        @SuppressWarnings("unused")
        public String getString()
        {
            return text;
        }
    }

    public static final class FakeCodec
    {
        @SuppressWarnings("unused")
        public FakeDataResult encodeStart(Object ops, Object component)
        {
            return new FakeDataResult(((FakeComponent) component).text());
        }
    }

    public static final class ThrowingCodec
    {
        @SuppressWarnings("unused")
        public FakeDataResult encodeStart(Object ops, Object component)
        {
            if (System.nanoTime() >= 0L)
                throw new IllegalStateException("boom");
            return new FakeDataResult("unreachable");
        }
    }

    public record FakeDataResult(String text)
    {
        @SuppressWarnings("unused")
        public Optional<String> result()
        {
            return Optional.of("{\"text\":\"%s\"}".formatted(text));
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

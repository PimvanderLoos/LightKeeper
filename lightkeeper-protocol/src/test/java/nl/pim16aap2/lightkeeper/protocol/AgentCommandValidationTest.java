package nl.pim16aap2.lightkeeper.protocol;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Convention guard: every command record must reject a blank {@code requestId} in its compact constructor.
 *
 * <p>All other components are supplied with valid values, so the only thing that can fail construction is the
 * {@code requestId} check. A command #31 that forgets to validate {@code requestId} therefore fails here.
 */
class AgentCommandValidationTest
{
    @Test
    void everyCommand_shouldRejectBlankRequestId()
        throws Exception
    {
        for (final Class<?> commandClass : IAgentCommand.class.getPermittedSubclasses())
        {
            // setup
            final RecordComponent[] components = commandClass.getRecordComponents();
            assertThat(components)
                .as("%s must be a record", commandClass.getName())
                .isNotEmpty();

            final Class<?>[] parameterTypes = new Class<?>[components.length];
            final Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++)
            {
                parameterTypes[i] = components[i].getType();
                arguments[i] = components[i].getName().equals("requestId")
                    ? ""
                    : validDefault(components[i].getType());
            }
            final Constructor<?> constructor = commandClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            // execute + verify
            try
            {
                constructor.newInstance(arguments);
                fail("Expected %s to reject a blank requestId.".formatted(commandClass.getSimpleName()));
            }
            catch (InvocationTargetException exception)
            {
                assertThat(exception.getCause())
                    .as("%s must reject a blank requestId with IllegalArgumentException",
                        commandClass.getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    void mutatePlayerPermissionCommand_shouldRejectBlankRequestId()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new MutatePlayerPermission.Command(
            "   ", uuid, "some.permission", MutatePlayerPermission.Mode.GRANT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requestId");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void mutatePlayerPermissionCommand_shouldRejectNullUuid()
    {
        // execute + verify
        assertThatThrownBy(() -> new MutatePlayerPermission.Command(
            "request-1", null, "some.permission", MutatePlayerPermission.Mode.GRANT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("uuid");
    }

    @Test
    void mutatePlayerPermissionCommand_shouldRejectBlankPermission()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new MutatePlayerPermission.Command(
            "request-1", uuid, "   ", MutatePlayerPermission.Mode.GRANT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("permission");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void mutatePlayerPermissionCommand_shouldRejectNullMode()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new MutatePlayerPermission.Command("request-1", uuid, "some.permission", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mode");
    }

    @Test
    void hasPlayerPermissionCommand_shouldRejectBlankRequestId()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new HasPlayerPermission.Command("   ", uuid, "some.permission"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requestId");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void hasPlayerPermissionCommand_shouldRejectNullUuid()
    {
        // execute + verify
        assertThatThrownBy(() -> new HasPlayerPermission.Command("request-1", null, "some.permission"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("uuid");
    }

    @Test
    void hasPlayerPermissionCommand_shouldRejectBlankPermission()
    {
        // setup
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> new HasPlayerPermission.Command("request-1", uuid, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("permission");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void dropItemResponse_shouldRejectNullResult()
    {
        // execute + verify
        assertThatThrownBy(() -> new DropItem.Response(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("result");
    }

    // -----------------------------------------------------------------------
    // IProtocolValue leaf validation
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void pStringConstructor_shouldRejectNullValue()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PString(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("value");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void pNumberConstructor_shouldRejectNullValue()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PNumber(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("value");
    }

    @Test
    void pEnumConstructor_shouldRejectBlankEnumClass()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PEnum("   ", "ALPHA"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("enumClass");
    }

    @Test
    void pEnumConstructor_shouldRejectBlankName()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PEnum("com.example.SomeEnum", "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void pRefConstructor_shouldRejectBlankClassName()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PRef("   ", "id-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("className");
    }

    @Test
    void pRefConstructor_shouldRejectBlankId()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PRef("com.example.Thing", "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id");
    }

    @Test
    void pDroppedConstructor_shouldRejectBlankAccessorName()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PDropped("   ", "capture-failed: Boom"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("accessorName");
    }

    @Test
    void pDroppedConstructor_shouldRejectBlankRuntimeType()
    {
        // execute + verify
        assertThatThrownBy(() -> new IProtocolValue.PDropped("getBroken", "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runtimeType");
    }

    private static Object validDefault(Class<?> type)
    {
        if (type == String.class)
            return "x";
        if (type == UUID.class)
            return UUID.randomUUID();
        if (type == int.class || type == Integer.class)
            return 0;
        if (type == long.class || type == Long.class)
            return 0L;
        if (type == double.class || type == Double.class)
            return 0.0;
        if (type == boolean.class || type == Boolean.class)
            return false;
        if (type == int[].class)
            return new int[]{0};
        if (type.isEnum())
            return type.getEnumConstants()[0];
        throw new IllegalStateException("Unhandled record component type in test: " + type);
    }
}

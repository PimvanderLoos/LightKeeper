package nl.pim16aap2.lightkeeper.protocol;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

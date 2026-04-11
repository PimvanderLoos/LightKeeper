package nl.pim16aap2.lightkeeper.agent.spigot;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpigotLightkeeperAgentPluginTest
{
    @Test
    void extractCraftBukkitRevision_shouldReturnRevisionForValidPackage()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.v1_21_R7";

        // execute
        final String revision = SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isEqualTo("v1_21_R7");
    }

    @Test
    void extractCraftBukkitRevision_shouldReturnRevisionForR7Package()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.v1_21_R7";

        // execute
        final String revision = SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isEqualTo("v1_21_R7");
    }

    @Test
    void extractCraftBukkitRevision_shouldReturnNullForUnversionedCraftBukkitPackage()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit";

        // execute
        final String revision = SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isNull();
    }

    @Test
    void extractCraftBukkitRevision_shouldReturnNullWhenRevisionSuffixEqualsCraftBukkit()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.craftbukkit";

        // execute
        final String revision = SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isNull();
    }

    @Test
    void extractCraftBukkitRevision_shouldTrimInputBeforeResolving()
    {
        // setup
        final String packageName = "  org.bukkit.craftbukkit.v1_21_R7   ";

        // execute
        final String revision = SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName);

        // verify
        assertThat(revision).isEqualTo("v1_21_R7");
    }

    @Test
    void extractCraftBukkitRevision_shouldThrowExceptionForUnexpectedPackagePrefix()
    {
        // setup
        final String packageName = "org.bukkit.server.v1_21_R7";

        // execute + verify
        assertThatThrownBy(() -> SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unexpected CraftBukkit package");
    }

    @Test
    void extractCraftBukkitRevision_shouldThrowExceptionWhenRevisionCannotBeResolved()
    {
        // setup
        final String packageName = "org.bukkit.craftbukkit.";

        // execute + verify
        assertThatThrownBy(() -> SpigotLightkeeperAgentPlugin.extractCraftBukkitRevision(packageName))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to resolve CraftBukkit NMS revision");
    }

    @Test
    void onDisable_shouldCleanupDispatcherExecutorsAndSocketPath()
        throws Exception
    {
        // setup
        final SpigotLightkeeperAgentPlugin plugin = allocatePlugin();
        final AgentRequestDispatcher dispatcher = mock(AgentRequestDispatcher.class);
        final ExecutorService requestExecutor = mock(ExecutorService.class);
        final ExecutorService acceptExecutor = mock(ExecutorService.class);
        final ServerSocketChannel serverSocketChannel = mock(ServerSocketChannel.class);
        final Path socketPath = Files.createTempFile("lk-agent-test", ".sock");

        setField(plugin, "requestDispatcher", dispatcher);
        setField(plugin, "requestExecutor", requestExecutor);
        setField(plugin, "acceptExecutor", acceptExecutor);
        setField(plugin, "serverSocketChannel", serverSocketChannel);
        setField(plugin, "socketPath", socketPath);

        // execute
        plugin.onDisable();

        // verify
        verify(dispatcher).cleanupSyntheticPlayers();
        verify(requestExecutor).shutdownNow();
        verify(acceptExecutor).shutdownNow();
        verify(serverSocketChannel).close();
        assertThat(socketPath).doesNotExist();
    }

    @Test
    void validateNmsCompatibility_shouldThrowExceptionWhenServerPackageIsUnexpected()
        throws Exception
    {
        // setup
        final SpigotLightkeeperAgentPlugin plugin = allocatePlugin();
        try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class))
        {
            bukkit.when(org.bukkit.Bukkit::getBukkitVersion).thenReturn("1.21.11-R0.2-SNAPSHOT");
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));

            // execute + verify
            assertThatThrownBy(() -> invokePrivateMethod(plugin, "validateNmsCompatibility"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected CraftBukkit package");
        }
    }

    @Test
    void validateNmsCompatibility_shouldThrowExceptionForUnsupportedVersion()
        throws Exception
    {
        // setup
        final SpigotLightkeeperAgentPlugin plugin = allocatePlugin();
        try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class))
        {
            bukkit.when(org.bukkit.Bukkit::getBukkitVersion).thenReturn("1.20.6");
            bukkit.when(org.bukkit.Bukkit::getServer).thenAnswer(invocation -> mock(org.bukkit.Server.class));

            // execute + verify
            assertThatThrownBy(() -> invokePrivateMethod(plugin, "validateNmsCompatibility"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported Bukkit version");
        }
    }

    @SuppressWarnings("unchecked")
    private static SpigotLightkeeperAgentPlugin allocatePlugin()
        throws Exception
    {
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        final Object unsafe = theUnsafeField.get(null);
        final Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (SpigotLightkeeperAgentPlugin) allocateInstance.invoke(unsafe, SpigotLightkeeperAgentPlugin.class);
    }

    private static void setField(Object target, String fieldName, Object value)
        throws Exception
    {
        final Field field = SpigotLightkeeperAgentPlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invokePrivateMethod(Object target, String methodName)
        throws Exception
    {
        final Method method = SpigotLightkeeperAgentPlugin.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        try
        {
            return method.invoke(target);
        }
        catch (java.lang.reflect.InvocationTargetException exception)
        {
            final Throwable cause = exception.getCause();
            if (cause instanceof Exception wrappedException)
                throw wrappedException;
            throw exception;
        }
    }
}

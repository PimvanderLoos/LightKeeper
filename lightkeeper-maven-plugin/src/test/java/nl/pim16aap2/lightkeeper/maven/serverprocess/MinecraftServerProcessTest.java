package nl.pim16aap2.lightkeeper.maven.serverprocess;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinecraftServerProcessTest
{
    @Test
    void waitForStartup_shouldReturnWhenDoneLineIsObserved(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestableMinecraftServerProcess minecraftServerProcess = new TestableMinecraftServerProcess(tempDirectory);
        final StubProcess stubProcess = StubProcess.withInput("""
            [Server thread/INFO]: Starting minecraft server version 1.21.11
            [Server thread/INFO]: Done (1.234s)! For help, type "help"
            """);
        minecraftServerProcess.setProcessForTests(stubProcess);

        // execute
        minecraftServerProcess.waitForStartupForTests(1);

        // verify
        assertThat(stubProcess.destroyForciblyInvoked()).isFalse();
    }

    @Test
    void waitForStartup_shouldThrowWhenStartupFailureIsLogged(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestableMinecraftServerProcess minecraftServerProcess = new TestableMinecraftServerProcess(tempDirectory);
        final StubProcess stubProcess = StubProcess.withInput(
            "[Server thread/ERROR]: Failed to initialize server\n"
        );
        minecraftServerProcess.setProcessForTests(stubProcess);

        // execute
        final var thrown = assertThatThrownBy(() -> minecraftServerProcess.waitForStartupForTests(1));

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Server startup failed:");
        assertThat(stubProcess.destroyForciblyInvoked()).isTrue();
    }

    @Test
    void waitForStartup_shouldTimeoutWhenOutputBlocksWithoutNewline(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestableMinecraftServerProcess minecraftServerProcess = new TestableMinecraftServerProcess(tempDirectory);
        final StubProcess stubProcess = StubProcess.withBlockingInput();
        minecraftServerProcess.setProcessForTests(stubProcess);

        // execute
        final var thrown = assertThatThrownBy(() -> minecraftServerProcess.waitForStartupForTests(1));

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Startup timeout after 1 second(s)");
        assertThat(stubProcess.destroyForciblyInvoked()).isTrue();
    }

    private static void setProcess(MinecraftServerProcess minecraftServerProcess, Process process)
        throws Exception
    {
        final Field processField = MinecraftServerProcess.class.getDeclaredField("process");
        processField.setAccessible(true);
        processField.set(minecraftServerProcess, process);
    }

    private static final class TestableMinecraftServerProcess extends MinecraftServerProcess
    {
        private TestableMinecraftServerProcess(Path serverDirectory)
        {
            super(serverDirectory, serverDirectory.resolve("paper.jar"), "java", null, 512);
        }

        private void setProcessForTests(Process process)
            throws Exception
        {
            setProcess(this, process);
        }

        private void waitForStartupForTests(int timeoutSeconds)
            throws MojoExecutionException
        {
            waitForStartup(timeoutSeconds);
        }
    }

    private static final class StubProcess extends Process
    {
        private final InputStream inputStream;
        private final @Nullable OutputStream backingOutputStream;
        private volatile boolean alive = true;
        private volatile boolean destroyForciblyInvoked;

        private StubProcess(
            InputStream inputStream,
            @Nullable OutputStream backingOutputStream)
        {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream may not be null.");
            this.backingOutputStream = backingOutputStream;
        }

        private static StubProcess withInput(String output)
        {
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            return new StubProcess(inputStream, null);
        }

        private static StubProcess withBlockingInput()
            throws IOException
        {
            final java.io.PipedInputStream inputStream = new java.io.PipedInputStream();
            final java.io.PipedOutputStream outputStream = new java.io.PipedOutputStream(inputStream);
            return new StubProcess(inputStream, outputStream);
        }

        boolean destroyForciblyInvoked()
        {
            return destroyForciblyInvoked;
        }

        @Override
        public OutputStream getOutputStream()
        {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream()
        {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream()
        {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor()
            throws InterruptedException
        {
            while (alive)
                Thread.sleep(10L);
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit)
            throws InterruptedException
        {
            final long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (alive && System.nanoTime() < deadline)
                Thread.sleep(10L);
            return !alive;
        }

        @Override
        public int exitValue()
        {
            if (alive)
                throw new IllegalThreadStateException("Process has not exited.");
            return 0;
        }

        @Override
        public void destroy()
        {
            alive = false;
            closeOutputStream();
        }

        @Override
        public Process destroyForcibly()
        {
            destroyForciblyInvoked = true;
            destroy();
            return this;
        }

        @Override
        public boolean isAlive()
        {
            return alive;
        }

        private void closeOutputStream()
        {
            if (backingOutputStream == null)
                return;
            try
            {
                backingOutputStream.close();
            }
            catch (IOException ignored)
            {
                // ignored in tests
            }
        }
    }
}

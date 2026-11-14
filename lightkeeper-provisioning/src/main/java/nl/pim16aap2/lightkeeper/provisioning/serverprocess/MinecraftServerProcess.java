package nl.pim16aap2.lightkeeper.provisioning.serverprocess;

import lombok.RequiredArgsConstructor;
import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Represents a Minecraft server process that can perform operations such as starting, stopping, and checking if it is
 * running.
 * <p>
 * This class is responsible for managing the lifecycle of a Minecraft server process, including starting it with the
 * specified Java executable and memory settings, and stopping it gracefully.
  * <p>
 * Intentionally not guarded by the framework's orphan reaper: this build-time server is short-lived and
 * driven synchronously by the provisioner, unlike the long-lived per-test server the reaper protects.
 */
@RequiredArgsConstructor
public class MinecraftServerProcess
{
    /** Working directory of this baseline initialization process. */
    private final Path serverDirectory;
    /** Prepared Minecraft server JAR. */
    private final Path jarFile;
    /** Java executable selected by the provisioning caller. */
    private final String javaExecutable;
    /** Optional additional server JVM arguments. */
    private final @Nullable String extraJvmArgs;
    /** Maximum server heap size in MiB. */
    private final int memoryMb;
    /** Current initialization process, absent before startup or after cleanup. */
    private @Nullable Process process;

    /**
     * Starts the server and waits for its readiness output.
     *
     * @param timeoutSeconds
     *     Maximum startup wait.
     * @throws ProvisioningException
     *     If already running, startup fails, or readiness times out.
     */
    public void start(int timeoutSeconds)
        throws ProvisioningException
    {
        if (isRunning())
            throw new ProvisioningException("Server is already running");

        final var pb = new ProcessBuilder(buildCommand())
            .directory(serverDirectory.toFile())
            .redirectErrorStream(true);
        try
        {
            process = pb.start();
            waitForStartup(timeoutSeconds);
        }
        catch (Exception e)
        {
            forceStopProcess();
            throw new ProvisioningException("Failed to start server", e);
        }
    }

    /**
     * Requests a clean server shutdown and bounds the wait.
     *
     * @param timeoutSeconds
     *     Maximum shutdown wait before force termination.
     * @throws ProvisioningException
     *     If not running or shutdown fails.
     */
    public void stop(int timeoutSeconds)
        throws ProvisioningException
    {
        if (!isRunning())
            throw new ProvisioningException("Server is not running");

        final Process runningProcess = requireRunningProcess();

        // Send "stop" command via stdin
        try (
            OutputStreamWriter outputStreamWriter =
                new OutputStreamWriter(runningProcess.getOutputStream(), StandardCharsets.UTF_8);
            PrintWriter writer = new PrintWriter(outputStreamWriter))
        {
            writer.println("stop");
            writer.flush();
        }
        catch (IOException exception)
        {
            forceStopProcess();
            throw new ProvisioningException("Failed to send stop command to server process", exception);
        }

        try
        {
            if (!runningProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS))
            {
                forceStopProcess();
                throw new ProvisioningException("Server didn't stop gracefully, force killed");
            }
        }
        catch (InterruptedException e)
        {
            forceStopProcess();
            Thread.currentThread().interrupt();
            throw new ProvisioningException("Thread was interrupted while waiting for the server to shut down", e);
        }
        catch (Exception e)
        {
            forceStopProcess();
            throw new ProvisioningException("Failed to stop server", e);
        }
    }

    /**
     * Reports whether the current process is alive.
     *
     * @return Whether this initialization process is running.
     */
    public boolean isRunning()
    {
        return process != null && process.isAlive();
    }

    /**
     * Builds the server process argument list.
     *
     * @return Java command and server arguments.
     */
    protected List<String> buildCommand()
    {
        final List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable);
        cmd.add("-Xmx" + memoryMb + "M");
        cmd.add("-Xms" + memoryMb + "M");
        if (extraJvmArgs != null)
            cmd.addAll(Arrays.asList(extraJvmArgs.split("\\s+")));
        cmd.add("-jar");
        cmd.add(jarFile.toString());
        cmd.add("--nogui");
        return cmd;
    }

    /**
     * Watches process output for readiness without waiting indefinitely.
     *
     * @param timeoutSeconds
     *     Maximum readiness wait.
     * @throws ProvisioningException
     *     If startup output, process state, or the wait fails.
     */
    protected void waitForStartup(int timeoutSeconds)
        throws ProvisioningException
    {
        final Process runningProcess = requireRunningProcess();
        final long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
        final ExecutorService outputReaderExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("lightkeeper-startup-output-reader-", 0).daemon(true).factory()
        );

        try
        {
            final Future<?> outputReaderFuture = outputReaderExecutor.submit(
                () -> readProcessOutputLines(runningProcess, outputLines)
            );

            while (System.nanoTime() < deadlineNanos)
            {
                final long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
                final long pollMillis = Math.min(Math.max(remainingMillis, 1L), 200L);
                final String line = outputLines.poll(pollMillis, TimeUnit.MILLISECONDS);
                if (line != null)
                {
                    if (line.endsWith(")! For help, type \"help\"") && line.contains("Done ("))
                        return;

                    if (line.contains("Failed to initialize server"))
                        throw new ProvisioningException("Server startup failed: " + line);
                }

                if (!runningProcess.isAlive() && outputLines.isEmpty())
                    throw new ProvisioningException("Server process ended before startup completed.");

                if (outputReaderFuture.isDone() && outputLines.isEmpty())
                {
                    awaitOutputReader(outputReaderFuture);
                    throw new ProvisioningException("Server output closed before startup completed.");
                }
            }

            throw new ProvisioningException(
                "Startup timeout after %d second(s) waiting for readiness output.".formatted(timeoutSeconds)
            );
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            forceStopProcess();
            throw new ProvisioningException("Interrupted while monitoring startup", e);
        }
        catch (ProvisioningException e)
        {
            forceStopProcess();
            throw e;
        }
        catch (Exception e)
        {
            forceStopProcess();
            throw new ProvisioningException("Failed to monitor startup", e);
        }
        finally
        {
            closeInputStreamQuietly(runningProcess);
            outputReaderExecutor.shutdownNow();
        }
    }

    private static void readProcessOutputLines(Process runningProcess, BlockingQueue<String> outputLines)
    {
        try (
            InputStreamReader isr = new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr)
        )
        {
            String line;
            while ((line = reader.readLine()) != null)
                outputLines.put(line);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException("Failed to read server startup output.", exception);
        }
    }

    private static void awaitOutputReader(Future<?> outputReaderFuture)
        throws ProvisioningException
    {
        try
        {
            outputReaderFuture.get();
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new ProvisioningException("Interrupted while monitoring startup output.", exception);
        }
        catch (ExecutionException exception)
        {
            final Throwable cause = exception.getCause();
            if (cause instanceof UncheckedIOException uncheckedIOException)
            {
                throw new ProvisioningException(
                    Objects.requireNonNullElse(
                        uncheckedIOException.getMessage(),
                        "Failed while reading server startup output."
                    ),
                    exception
                );
            }
            throw new ProvisioningException(
                "Failed while reading server startup output.",
                exception
            );
        }
    }

    private static void closeInputStreamQuietly(Process process)
    {
        try
        {
            process.getInputStream().close();
        }
        catch (IOException ignored)
        {
            // ignored while cleaning up startup monitor resources
        }
    }

    private Process requireRunningProcess()
    {
        if (process == null)
            throw new IllegalStateException("Server is not running");
        return process;
    }

    private void forceStopProcess()
    {
        if (process != null)
        {
            process.destroyForcibly();
            process = null;
        }
    }
}

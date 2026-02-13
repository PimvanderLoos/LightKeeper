package nl.pim16aap2.lightkeeper.maven.serverprocess;

import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.MojoExecutionException;
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
 */
@RequiredArgsConstructor
public class MinecraftServerProcess
{
    private final Path serverDirectory;
    private final Path jarFile;
    private final String javaExecutable;
    private final @Nullable String extraJvmArgs;
    private final int memoryMb;
    private @Nullable Process process;

    public void start(int timeoutSeconds)
        throws MojoExecutionException
    {
        if (isRunning())
            throw new MojoExecutionException("Server is already running");

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
            throw new MojoExecutionException("Failed to start server", e);
        }
    }

    public void stop(int timeoutSeconds)
        throws MojoExecutionException
    {
        if (!isRunning())
            throw new MojoExecutionException("Server is not running");

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
            throw new MojoExecutionException("Failed to send stop command to server process", exception);
        }

        try
        {
            if (!runningProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS))
            {
                forceStopProcess();
                throw new MojoExecutionException("Server didn't stop gracefully, force killed");
            }
        }
        catch (InterruptedException e)
        {
            forceStopProcess();
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Thread was interrupted while waiting for the server to shut down", e);
        }
        catch (Exception e)
        {
            forceStopProcess();
            throw new MojoExecutionException("Failed to stop server", e);
        }
    }

    public boolean isRunning()
    {
        return process != null && process.isAlive();
    }

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

    protected void waitForStartup(int timeoutSeconds)
        throws MojoExecutionException
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
                        throw new MojoExecutionException("Server startup failed: " + line);
                }

                if (!runningProcess.isAlive() && outputLines.isEmpty())
                    throw new MojoExecutionException("Server process ended before startup completed.");

                if (outputReaderFuture.isDone() && outputLines.isEmpty())
                {
                    awaitOutputReader(outputReaderFuture);
                    throw new MojoExecutionException("Server output closed before startup completed.");
                }
            }

            throw new MojoExecutionException(
                "Startup timeout after %d second(s) waiting for readiness output.".formatted(timeoutSeconds)
            );
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            forceStopProcess();
            throw new MojoExecutionException("Interrupted while monitoring startup", e);
        }
        catch (MojoExecutionException e)
        {
            forceStopProcess();
            throw e;
        }
        catch (Exception e)
        {
            forceStopProcess();
            throw new MojoExecutionException("Failed to monitor startup", e);
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
        throws MojoExecutionException
    {
        try
        {
            outputReaderFuture.get();
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while monitoring startup output.", exception);
        }
        catch (ExecutionException exception)
        {
            final Throwable cause = exception.getCause();
            if (cause instanceof UncheckedIOException uncheckedIOException)
            {
                throw new MojoExecutionException(
                    uncheckedIOException.getMessage(),
                    uncheckedIOException.getCause()
                );
            }
            throw new MojoExecutionException(
                "Failed while reading server startup output.",
                cause == null ? exception : cause
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

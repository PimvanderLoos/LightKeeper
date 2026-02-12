package nl.pim16aap2.lightkeeper.maven.serverprocess;

import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Represents a Minecraft server process that can perform operations such as starting, stopping, and checking if it is
 * running.
 * <p>
 * This class is responsible for managing the lifecycle of a Minecraft server process, including starting it with the
 * specified Java executable and memory settings, and stopping it gracefully.
 */
@RequiredArgsConstructor
public abstract class MinecraftServerProcess
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
        List<String> cmd = new ArrayList<>();
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

        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        try (
            InputStreamReader isr = new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr))
        {

            String line;
            while (System.currentTimeMillis() < deadline &&
                (line = reader.readLine()) != null)
            {
                if (line.endsWith(")! For help, type \"help\"") && line.contains("Done ("))
                    return;

                if (line.contains("Failed to initialize server"))
                    throw new MojoExecutionException("Server startup failed: " + line);
            }

            throw new MojoExecutionException("Startup timeout or process ended unexpectedly");
        }
        catch (Exception e)
        {
            forceStopProcess();
            throw new MojoExecutionException("Failed to monitor startup", e);
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

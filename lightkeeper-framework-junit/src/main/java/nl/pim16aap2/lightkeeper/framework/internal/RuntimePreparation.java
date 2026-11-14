package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/** One root-store preparation, retaining both successful inputs and failed producer results for the JUnit launch. */
public final class RuntimePreparation implements AutoCloseable
{
    /** Run-once producer task; its completed failure is retained instead of retrying for each class. */
    private final FutureTask<PreparedTestRuntime> task;

    /**
     * Creates a lazy preparation without starting Maven while the root-store entry is being computed.
     *
     * @param plan
     *     Maven-owned module plan used by the first class in this launch.
     */
    public RuntimePreparation(ModuleTestPlan plan)
    {
        Objects.requireNonNull(plan, "plan may not be null.");
        task = new FutureTask<>(() -> NativeMavenPreparation.prepare(plan));
    }

    /**
     * Starts preparation once and awaits the same result or original failure for every requesting class.
     *
     * @return Shared immutable inputs; their lifetime belongs to this root-store resource.
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
        // Unwrap FutureTask's transport exception while preserving the original producer failure and its stack trace.
    public PreparedTestRuntime await()
    {
        task.run();
        try
        {
            return task.get();
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for shared LightKeeper preparation.", exception);
        }
        catch (ExecutionException exception)
        {
            final Throwable failure = exception.getCause();
            if (failure instanceof RuntimeException runtime)
            {
                throw runtime;
            }
            if (failure instanceof Error error)
            {
                throw error;
            }
            throw new IllegalStateException("Shared LightKeeper preparation failed.", failure);
        }
    }

    /** Closes successful immutable inputs after the root store's test classes have finished. */
    @Override
    public void close()
    {
        if (task.state() == Future.State.SUCCESS)
        {
            task.resultNow().close();
        }
    }
}

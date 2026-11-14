package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.provisioning.session.PreparedServerSnapshot;
import nl.pim16aap2.lightkeeper.provisioning.session.ServerSnapshots;
import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;
import nl.pim16aap2.lightkeeper.runtime.MavenRuntimeContext;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a native launch's immutable snapshot until every class has obtained its private mutable session. */
public final class PreparedTestRuntime implements AutoCloseable
{
    /** Verified immutable server inputs shared within one JUnit launch. */
    private final PreparedServerSnapshot snapshot;
    /** Owning test module, used for class-session placement. */
    private final Path moduleDirectory;
    /** Snapshot directory owned here; normal Failsafe snapshots remain owned by the outer Maven invocation. */
    private final @Nullable Path ownedSnapshot;
    /** Successful native launch metadata to remove after use. */
    private final @Nullable Path launchDirectory;
    /** Ensures root-store cleanup and standalone cleanup cannot run twice. */
    private final AtomicBoolean closed = new AtomicBoolean();

    PreparedTestRuntime(MavenRuntimeContext context, @Nullable Path launchDirectory)
    {
        snapshot = ServerSnapshots.read(Path.of(context.snapshotMetadataPath()));
        moduleDirectory = Path.of(context.plan().moduleDirectory());
        ownedSnapshot = launchDirectory == null ? null : Objects.requireNonNull(
            Path.of(context.snapshotMetadataPath()).getParent()
        );
        this.launchDirectory = launchDirectory;
    }

    /**
     * Returns the immutable inputs used to create independent class sessions.
     *
     * @return Validated prepared snapshot.
     */
    public PreparedServerSnapshot snapshot()
    {
        return snapshot;
    }

    /**
     * Returns the owning Maven test module.
     *
     * @return Canonical module directory.
     */
    public Path moduleDirectory()
    {
        return moduleDirectory;
    }

    /** Releases native-launch inputs after all independent sessions have finished copying them. */
    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            if (ownedSnapshot != null)
            {
                FileUtil.deleteRecursively(ownedSnapshot, "completed native JUnit snapshot");
            }
            if (launchDirectory != null)
            {
                FileUtil.deleteRecursively(launchDirectory, "completed native JUnit preparation");
            }
        }
    }
}

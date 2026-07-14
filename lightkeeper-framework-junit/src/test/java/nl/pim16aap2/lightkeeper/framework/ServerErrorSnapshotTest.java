package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerErrorSnapshotTest
{
    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void constructor_shouldDefaultStackTraceToEmptyListWhenNull()
    {
        // setup + execute
        final ServerErrorSnapshot snapshot = new ServerErrorSnapshot(
            1L,
            ServerErrorSnapshot.Severity.ERROR,
            "ERROR",
            "net.example.SomePlugin",
            "Server thread",
            "boom",
            null,
            null,
            null
        );

        // verify
        assertThat(snapshot.stackTrace()).isEmpty();
    }
}

package nl.pim16aap2.lightkeeper.provisioning.session;

import nl.pim16aap2.lightkeeper.provisioning.util.HashUtil;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Complete stopped server input shared by snapshot and session ownership tests. */
final class PreparedServerFixture
{
    private PreparedServerFixture()
    {
    }

    static RuntimeManifest create(Path root)
        throws IOException
    {
        final Path server = Files.createDirectories(root.resolve("prepared"));
        final Path plugins = Files.createDirectories(server.resolve("plugins"));
        Files.writeString(server.resolve("paper.jar"), "server-bytes");
        Files.writeString(plugins.resolve("target.jar"), "plugin-v1");
        final Path agent = Files.writeString(plugins.resolve("agent.jar"), "agent-bytes");
        Files.writeString(
            server.resolve("server.properties"),
            "server-port=4242\nquery.port=4242\nonline-mode=false\n"
        );
        final Path world = Files.createDirectories(server.resolve("world"));
        Files.writeString(world.resolve("level.dat"), "clean-world");
        return new RuntimeManifest(
            "paper", "1.21.11", 132, "cache", server.toString(), server.resolve("paper.jar").toString(), 1024,
            root.resolve("s/original.sock").toString(), "original-token", agent.toString(), HashUtil.sha256(agent),
            RuntimeProtocol.VERSION, "agent", "-Dfixture=true", List.of(), javaExecutable()
        );
    }

    static String javaExecutable()
    {
        return Path.of(System.getProperty("java.home"), "bin/java").toString();
    }

    static PreparedServerSnapshot snapshot(Path root)
    {
        try
        {
            return ServerSnapshots.read(ServerSnapshots.capture(
                create(root),
                root.resolve("snapshots")
            ));
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to create test fixture.", exception);
        }
    }
}

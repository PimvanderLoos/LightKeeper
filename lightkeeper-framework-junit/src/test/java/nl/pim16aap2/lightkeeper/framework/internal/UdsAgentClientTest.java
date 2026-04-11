package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentAction;
import nl.pim16aap2.lightkeeper.runtime.agent.AgentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdsAgentClientTest
{
    @Test
    void send_shouldThrowExceptionWhenResponseIdDoesNotMatch(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-mismatch.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, new AgentResponse(
            "unexpected-request-id",
            true,
            null,
            null,
            Map.of()
        )))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(AgentAction.WAIT_TICKS, Map.of("ticks", "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected response id");
            client.close();
        }
    }

    @Test
    void send_shouldIncludeProtocolVersionDetailsWhenAgentReportsFailure(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-failure.sock");
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, new AgentResponse(
            "1",
            false,
            "PROTOCOL_MISMATCH",
            "Protocol mismatch.",
            Map.of(
                "expectedProtocolVersion", "7",
                "actualProtocolVersion", "6"
            )
        )))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(AgentAction.WAIT_TICKS, Map.of("ticks", "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=PROTOCOL_MISMATCH")
                .hasMessageContaining("expectedProtocolVersion=7")
                .hasMessageContaining("actualProtocolVersion=6");
            client.close();
        }
    }

    private static final class AgentSocketServer implements AutoCloseable
    {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final ServerSocketChannel serverChannel;
        private final Thread workerThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        private final Path socketPath;

        private AgentSocketServer(Path socketPath, AgentResponse response)
            throws IOException
        {
            this.socketPath = socketPath;
            this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            this.workerThread = new Thread(() -> serveSingleResponse(response), "uds-agent-client-test-server");
            started.countDown();
            this.workerThread.start();
        }

        private static AgentSocketServer start(Path socketPath, AgentResponse response)
            throws IOException, InterruptedException
        {
            final AgentSocketServer server = new AgentSocketServer(socketPath, response);
            if (!server.started.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out while waiting for test socket server startup.");
            return server;
        }

        private void serveSingleResponse(AgentResponse response)
        {
            try (SocketChannel clientChannel = serverChannel.accept();
                 BufferedReader reader = new BufferedReader(
                     Channels.newReader(clientChannel, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(
                     Channels.newWriter(clientChannel, StandardCharsets.UTF_8)))
            {
                final String requestLine = reader.readLine();
                if (requestLine == null)
                    return;
                writer.write(OBJECT_MAPPER.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
            catch (Throwable throwable)
            {
                workerFailure.set(throwable);
            }
        }

        @Override
        public void close()
            throws Exception
        {
            serverChannel.close();
            workerThread.join(TimeUnit.SECONDS.toMillis(3));
            final Throwable failure = workerFailure.get();
            if (failure != null)
                throw new IllegalStateException("Socket server worker failed.", failure);
            Files.deleteIfExists(socketPath);
        }
    }
}

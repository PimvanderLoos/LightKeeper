package nl.pim16aap2.lightkeeper.framework.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
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
        // Return a success response but with the wrong requestId
        final String responseJson = "{\"requestId\":\"unexpected-request-id\",\"success\":true,"
            + "\"startTick\":0,\"endTick\":0}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected response id");
            client.close();
        }
    }

    @Test
    void send_shouldThrowWhenAgentReportsFailure(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path socketPath = tempDirectory.resolve("agent-failure.sock");
        final String responseJson = "{\"requestId\":\"1\",\"success\":false,"
            + "\"errorCode\":\"PROTOCOL_MISMATCH\",\"errorMessage\":\"Protocol mismatch.\"}";
        try (AgentSocketServer server = AgentSocketServer.start(socketPath, responseJson))
        {
            final UdsAgentClient client = new UdsAgentClient(socketPath, Duration.ofSeconds(3));

            // execute + verify
            assertThatThrownBy(() -> client.send(new WaitTicks.Command("1", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=PROTOCOL_MISMATCH");
            client.close();
        }
    }

    private static final class AgentSocketServer implements AutoCloseable
    {
        private final ServerSocketChannel serverChannel;
        private final Thread workerThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        private final Path socketPath;

        private AgentSocketServer(Path socketPath, String responseJson)
            throws IOException
        {
            this.socketPath = socketPath;
            this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            this.workerThread = new Thread(() -> serveSingleResponse(responseJson), "uds-agent-client-test-server");
            started.countDown();
            this.workerThread.start();
        }

        private static AgentSocketServer start(Path socketPath, String responseJson)
            throws IOException, InterruptedException
        {
            final AgentSocketServer server = new AgentSocketServer(socketPath, responseJson);
            if (!server.started.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out while waiting for test socket server startup.");
            return server;
        }

        private void serveSingleResponse(String responseJson)
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
                writer.write(responseJson);
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

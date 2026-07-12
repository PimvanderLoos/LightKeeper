package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerErrorsHandleTest
{
    private IFrameworkGatewayView frameworkGateway;
    private ServerErrorsHandle serverErrorsHandle;

    @BeforeEach
    void setUp()
    {
        frameworkGateway = mock(IFrameworkGatewayView.class);
        serverErrorsHandle = new ServerErrorsHandle(frameworkGateway);
    }

    @Test
    void getCaptured_shouldDelegateToGateway()
    {
        // setup
        final List<ServerErrorSnapshot> errors = List.of(new ServerErrorSnapshot(
            1L,
            ServerErrorSnapshot.Severity.ERROR,
            "ERROR",
            "net.example.SomePlugin",
            "Server thread",
            "boom",
            null,
            null,
            List.of()
        ));
        when(frameworkGateway.capturedServerErrors()).thenReturn(errors);

        // execute
        final List<ServerErrorSnapshot> result = serverErrorsHandle.getCaptured();

        // verify
        assertThat(result).isSameAs(errors);
        verify(frameworkGateway).capturedServerErrors();
    }

    @Test
    void clear_shouldDelegateToGatewayAndReturnSelf()
    {
        // execute
        final ServerErrorsHandle result = serverErrorsHandle.clear();

        // verify
        assertThat(result).isSameAs(serverErrorsHandle);
        verify(frameworkGateway).clearServerErrors();
    }
}

package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.ClearServerErrors;
import nl.pim16aap2.lightkeeper.protocol.GetServerErrors;
import nl.pim16aap2.lightkeeper.protocol.ServerErrorEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServerErrorActionsTest
{
    @Mock
    private AgentServerErrorCapture serverErrorCapture;

    @Test
    void handleGetServerErrors_shouldReturnSnapshotDroppedCountAndActiveFlag()
    {
        // setup
        final ServerErrorEntry entry = new ServerErrorEntry(
            1L, "ERROR", "ERROR", "logger", "thread", "message", null, null, List.of());
        when(serverErrorCapture.snapshot()).thenReturn(List.of(entry));
        when(serverErrorCapture.droppedCount()).thenReturn(3L);
        when(serverErrorCapture.active()).thenReturn(true);
        final AgentServerErrorActions actions = new AgentServerErrorActions(serverErrorCapture);

        // execute
        final GetServerErrors.Response response =
            actions.handleGetServerErrors(new GetServerErrors.Command("request-1"));

        // verify
        assertThat(response.errors()).containsExactly(entry);
        assertThat(response.droppedCount()).isEqualTo(3L);
        assertThat(response.captureActive()).isTrue();
    }

    @Test
    void handleClearServerErrors_shouldDelegateToCapture()
    {
        // setup
        final AgentServerErrorActions actions = new AgentServerErrorActions(serverErrorCapture);

        // execute
        final ClearServerErrors.Response response =
            actions.handleClearServerErrors(new ClearServerErrors.Command("request-2"));

        // verify
        assertThat(response).isNotNull();
        verify(serverErrorCapture).clear();
    }
}

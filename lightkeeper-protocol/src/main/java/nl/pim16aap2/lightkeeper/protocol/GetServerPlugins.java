package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Retrieves a list of plugins installed on the server.
 */
public final class GetServerPlugins
{
    private GetServerPlugins()
    {
    }

    /**
     * Command record for {@code GET_SERVER_PLUGINS}
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param name
     *     The name of the plugin to look for. When provided, only the plugin whose name is an exact match may be
     *     returned.
     *
     *     <p>When {@code null}, all plugins are returned.
     */
    public record Command(
        String requestId,
        @Nullable String name
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * Response record for {@code GET_SERVER_PLUGINS}.
     *
     * @param plugins
     *     The list of plugins installed on the server. When the command's {@code name} was provided, this list will
     *     contain at most one plugin, or be empty if no plugin with that name was found.
     *
     *     <p>When the command's {@code name} was {@code null}, this list will contain all plugins installed on the
     *     server.
     */
    public record Response(
        List<ServerPlugin> plugins
    ) implements IAgentResponse
    {
        public Response
        {
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
        }
    }
}

/**
 * Agent protocol command types.
 *
 * <p>Contains the sealed {@link nl.pim16aap2.lightkeeper.protocol.IAgentCommand} interface and one typed record per
 * protocol action. The {@link nl.pim16aap2.lightkeeper.protocol.IAgentResponse} and
 * {@link nl.pim16aap2.lightkeeper.protocol.AgentErrorCode} types for the response side live here too.
 *
 * <p>Commands are ordinary typed records and use the shared wire mapper for polymorphic round trips:
 * <pre>{@code
 * ObjectMapper mapper = AgentProtocolMapper.create();
 * IAgentCommand<?> command = new MainWorld.Command("request-1");
 * String json = mapper.writeValueAsString(command);
 * IAgentCommand<?> decoded = mapper.readValue(json, IAgentCommand.class);
 * }</pre>
 *
 * <p>See {@link nl.pim16aap2.lightkeeper.protocol.IAgentCommand} for the complete registration recipe when adding
 * a protocol action.
 */
package nl.pim16aap2.lightkeeper.protocol;

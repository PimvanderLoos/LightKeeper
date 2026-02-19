/**
 * Spigot-side implementation of the LightKeeper runtime agent protocol.
 *
 * <p>This package hosts the complete Paper/Spigot integration for the LightKeeper runtime agent.
 * It opens a Unix domain socket (UDS), authenticates incoming clients through a handshake, and executes protocol
 * actions against a running Minecraft server in a deterministic and thread-safe way.
 *
 * <p><strong>Primary goal</strong>: provide a stable automation surface so LightKeeper clients can create worlds,
 * spawn synthetic players, execute commands, inspect game state, and interact with inventories through a remote
 * protocol without relying on in-process hooks.
 *
 * <p><strong>Some important classes</strong>:
 * <ul>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.SpigotLightkeeperAgentPlugin} is the composition root.
 *       It validates server compatibility, wires dependencies, starts the UDS server, and listens for menu events.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.AgentRequestDispatcher} deserializes requests,
 *       enforces handshake/authentication, and routes actions to dedicated domain handlers.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.AgentSyntheticPlayerStore} keeps synthetic player state
 *       (registry, permissions, and captured messages) consistent across requests.</li>
 * </ul>
 *
 * <p><strong>Supporting utilities</strong>:
 * <ul>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.AgentConfiguration} loads and validates required runtime
 *       system properties.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.AgentRequestParsers} centralizes parsing and normalization
 *       of textual request arguments.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.AgentResponses} standardizes success/error response shapes.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.agent.spigot.AgentMenuActions} exposes generic menu snapshot/click/drag
 *       inventory actions over the runtime protocol.</li>
 * </ul>
 *
 * <p><strong>Typical request flow example</strong>:
 * <ol>
 *   <li>Client sends {@code HANDSHAKE} with token, protocol version, and optional expected agent hash.</li>
 *   <li>Client issues domain requests such as {@code NEW_WORLD}, {@code CREATE_PLAYER}, or
 *       {@code CLICK_MENU_SLOT}.</li>
 *   <li>Dispatcher calls the appropriate action handler, which executes server mutations on the Bukkit main thread.
 *   </li>
 *   <li>Handler returns an {@code AgentResponse} containing success/error status and response data.</li>
 * </ol>
 *
 * <p>The package is intentionally self-contained: all protocol handling, validation, dispatch, execution, and
 * response formatting for the Spigot agent live here.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.agent.spigot;

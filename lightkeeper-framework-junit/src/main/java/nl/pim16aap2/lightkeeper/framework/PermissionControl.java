package nl.pim16aap2.lightkeeper.framework;

import java.util.Objects;
import java.util.UUID;

/**
 * Handle for mutating and querying a synthetic player's permissions.
 * <p>
 * Honesty contract: {@link #grant}, {@link #revoke}, and {@link #unset} mutate LightKeeper's own
 * {@code PermissionAttachment} on the synthetic player. This <strong>bypasses permission plugins</strong> such as
 * LuckPerms — the mutation is real as far as Bukkit's permission resolution is concerned, but no permission plugin
 * observes it. For permission-plugin-realistic tests, drive the plugin itself instead (e.g. console
 * {@code lp user ... permission set ...} commands).
 * <p>
 * {@link #has(String)} is a <strong>live</strong> query: every call asks the server for the player's current
 * {@code hasPermission} result, which reflects all permission sources (attachments, permission plugins, defaults,
 * and OP status) — not just LightKeeper's own attachment.
 */
public final class PermissionControl
{
    private final IFrameworkGatewayView frameworkGateway;
    private final UUID playerId;

    /**
     * Creates a permission control handle.
     *
     * @param frameworkGateway
     *     Internal gateway for operations.
     * @param playerId
     *     UUID of the player whose permissions this handle controls.
     */
    PermissionControl(IFrameworkGatewayView frameworkGateway, UUID playerId)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.playerId = Objects.requireNonNull(playerId, "playerId may not be null.");
    }

    /**
     * Grants a permission node by setting it to {@code true} on the player's attachment.
     *
     * @param permission
     *     The permission node to grant.
     * @return This handle for fluent chaining.
     */
    public PermissionControl grant(String permission)
    {
        frameworkGateway.grantPermission(playerId, permission);
        return this;
    }

    /**
     * Revokes a permission node by setting it to {@code false} on the player's attachment.
     * <p>
     * Unlike {@link #unset(String)}, this overrides any other source that would grant the node.
     *
     * @param permission
     *     The permission node to revoke.
     * @return This handle for fluent chaining.
     */
    public PermissionControl revoke(String permission)
    {
        frameworkGateway.revokePermission(playerId, permission);
        return this;
    }

    /**
     * Removes a permission node from the player's attachment, restoring the player's default for that node.
     * <p>
     * A no-op when the node was never granted or revoked through this handle (or at spawn time).
     *
     * @param permission
     *     The permission node to remove from the attachment.
     * @return This handle for fluent chaining.
     */
    public PermissionControl unset(String permission)
    {
        frameworkGateway.unsetPermission(playerId, permission);
        return this;
    }

    /**
     * Queries the live value of a permission node for this player.
     *
     * @param permission
     *     The permission node to query.
     * @return The player's current {@code hasPermission} result on the server.
     */
    public boolean has(String permission)
    {
        return frameworkGateway.hasPermission(playerId, permission);
    }
}

package nl.pim16aap2.lightkeeper.protocol;

/**
 * Selects how a synthetic player is placed onto the server by {@link CreatePlayer.Command}.
 *
 * <p>Serialized on the wire by {@linkplain Enum#name() name}, so the constant names are part of the protocol
 * contract and must not be renamed without a {@code RuntimeProtocol.VERSION} bump.
 */
public enum JoinMode
{
    /**
     * Drive the full vanilla login pipeline over a real loopback TCP connection.
     *
     * <p>The join fires the real {@code AsyncPlayerPreLoginEvent}, {@code PlayerLoginEvent}, and
     * {@code PlayerJoinEvent}, traverses the 1.20.2+ configuration phase, and lets the server derive the
     * offline UUID from the player name. Because it is a genuine login, it can be denied by whitelist, ban,
     * or a full server; such denials are surfaced as typed errors. The {@code uuid} field of
     * {@link CreatePlayer.Command} must be {@code null} under this mode.
     */
    FULL_LOGIN,

    /**
     * Spawn the synthetic player directly through the internal, reflective spawn path.
     *
     * <p>This is the historical fast path: it fires {@code PlayerJoinEvent} but skips the pre-login, login,
     * and configuration phases. The caller-supplied {@code uuid} is honored. Kept as an uncontracted internal
     * shortcut for LightKeeper's own fast tests.
     */
    LEGACY_SPAWN
}

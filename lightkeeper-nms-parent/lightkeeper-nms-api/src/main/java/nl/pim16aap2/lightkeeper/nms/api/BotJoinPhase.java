package nl.pim16aap2.lightkeeper.nms.api;

/**
 * Protocol phase a {@link IBotLoginDriver} was in when a full-login join was denied.
 *
 * <p>Surfaced on {@link BotLoginOutcome.Denied} so callers can distinguish, for example, a whitelist/ban
 * kick during login from a kick during the configuration or play phase.
 */
public enum BotJoinPhase
{
    /**
     * The login phase (handshake, hello, compression, login success).
     */
    LOGIN,

    /**
     * The 1.20.2+ configuration phase (known packs, registries, configuration finish).
     */
    CONFIGURATION,

    /**
     * The play phase (after the player has been placed onto the server).
     */
    PLAY
}

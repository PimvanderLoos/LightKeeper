package nl.pim16aap2.lightkeeper.runtime.agent;

/**
 * Supported LightKeeper agent actions for v1.1.
 */
public enum AgentAction
{
    HANDSHAKE,
    MAIN_WORLD,
    NEW_WORLD,
    EXECUTE_COMMAND,
    BLOCK_TYPE,
    SET_BLOCK,
    CREATE_PLAYER,
    REMOVE_PLAYER,
    EXECUTE_PLAYER_COMMAND,
    PLACE_PLAYER_BLOCK,
    GET_OPEN_MENU,
    CLICK_MENU_SLOT,
    DRAG_MENU_SLOTS,
    GET_PLAYER_MESSAGES,
    WAIT_TICKS,
    GET_SERVER_TICK
}

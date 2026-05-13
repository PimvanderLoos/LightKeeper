package nl.pim16aap2.lightkeeper.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed command hierarchy for the LightKeeper agent protocol.
 *
 * <p>Every protocol action is represented by exactly one record implementing this interface. Jackson uses the
 * {@code "action"} JSON property to select the correct subtype during deserialization; the value matches the
 * historical {@code AgentAction} enum name so existing wire traffic remains compatible.
 *
 * <p>Adding a new action requires: (1) a new record implementing {@code IAgentCommand}, (2) a new
 * {@link JsonSubTypes.Type} entry here, and (3) a new {@code permits} clause entry. The pattern-matching switch
 * in {@code AgentRequestDispatcher} will then fail to compile until the case is handled — intentional.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HandshakeCommand.class, name = "HANDSHAKE"),
    @JsonSubTypes.Type(value = MainWorldCommand.class, name = "MAIN_WORLD"),
    @JsonSubTypes.Type(value = NewWorldCommand.class, name = "NEW_WORLD"),
    @JsonSubTypes.Type(value = ExecuteCommandCommand.class, name = "EXECUTE_COMMAND"),
    @JsonSubTypes.Type(value = BlockTypeCommand.class, name = "BLOCK_TYPE"),
    @JsonSubTypes.Type(value = SetBlockCommand.class, name = "SET_BLOCK"),
    @JsonSubTypes.Type(value = CreatePlayerCommand.class, name = "CREATE_PLAYER"),
    @JsonSubTypes.Type(value = RemovePlayerCommand.class, name = "REMOVE_PLAYER"),
    @JsonSubTypes.Type(value = ExecutePlayerCommandCommand.class, name = "EXECUTE_PLAYER_COMMAND"),
    @JsonSubTypes.Type(value = PlacePlayerBlockCommand.class, name = "PLACE_PLAYER_BLOCK"),
    @JsonSubTypes.Type(value = LeftClickBlockCommand.class, name = "LEFT_CLICK_BLOCK"),
    @JsonSubTypes.Type(value = RightClickBlockCommand.class, name = "RIGHT_CLICK_BLOCK"),
    @JsonSubTypes.Type(value = GetOpenMenuCommand.class, name = "GET_OPEN_MENU"),
    @JsonSubTypes.Type(value = ClickMenuSlotCommand.class, name = "CLICK_MENU_SLOT"),
    @JsonSubTypes.Type(value = DragMenuSlotsCommand.class, name = "DRAG_MENU_SLOTS"),
    @JsonSubTypes.Type(value = GetPlayerMessagesCommand.class, name = "GET_PLAYER_MESSAGES"),
    @JsonSubTypes.Type(value = WaitTicksCommand.class, name = "WAIT_TICKS"),
    @JsonSubTypes.Type(value = GetServerTickCommand.class, name = "GET_SERVER_TICK"),
    @JsonSubTypes.Type(value = TeleportPlayerCommand.class, name = "TELEPORT_PLAYER"),
    @JsonSubTypes.Type(value = LoadChunkCommand.class, name = "LOAD_CHUNK"),
    @JsonSubTypes.Type(value = UnloadChunkCommand.class, name = "UNLOAD_CHUNK"),
    @JsonSubTypes.Type(value = IsChunkLoadedCommand.class, name = "IS_CHUNK_LOADED"),
    @JsonSubTypes.Type(value = GetPlayerInventoryCommand.class, name = "GET_PLAYER_INVENTORY"),
    @JsonSubTypes.Type(value = DropItemCommand.class, name = "DROP_ITEM"),
    @JsonSubTypes.Type(value = RegisterEventListenerCommand.class, name = "REGISTER_EVENT_LISTENER"),
    @JsonSubTypes.Type(value = GetCapturedEventsCommand.class, name = "GET_CAPTURED_EVENTS"),
    @JsonSubTypes.Type(value = ClearCapturedEventsCommand.class, name = "CLEAR_CAPTURED_EVENTS"),
    @JsonSubTypes.Type(value = UnregisterEventListenerCommand.class, name = "UNREGISTER_EVENT_LISTENER"),
    @JsonSubTypes.Type(value = GetPlayerChatComponentsCommand.class, name = "GET_PLAYER_CHAT_COMPONENTS"),
    @JsonSubTypes.Type(value = GetServerPlatformCommand.class, name = "GET_SERVER_PLATFORM"),
})
public sealed interface IAgentCommand
    permits HandshakeCommand, MainWorldCommand, NewWorldCommand, ExecuteCommandCommand,
            BlockTypeCommand, SetBlockCommand, CreatePlayerCommand, RemovePlayerCommand,
            ExecutePlayerCommandCommand, PlacePlayerBlockCommand, LeftClickBlockCommand,
            RightClickBlockCommand, GetOpenMenuCommand, ClickMenuSlotCommand,
            DragMenuSlotsCommand, GetPlayerMessagesCommand, WaitTicksCommand,
            GetServerTickCommand, TeleportPlayerCommand, LoadChunkCommand,
            UnloadChunkCommand, IsChunkLoadedCommand, GetPlayerInventoryCommand,
            DropItemCommand, RegisterEventListenerCommand, GetCapturedEventsCommand,
            ClearCapturedEventsCommand, UnregisterEventListenerCommand,
            GetPlayerChatComponentsCommand, GetServerPlatformCommand
{
    /**
     * Correlation identifier matching the response's {@code requestId}.
     *
     * @return Non-blank request identifier.
     */
    String requestId();
}

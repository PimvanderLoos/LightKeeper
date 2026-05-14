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
 * <p>Adding a new action requires: (1) a new namespace class with inner {@code Command} and {@code Response}
 * records, (2) a new {@link JsonSubTypes.Type} entry here, and (3) a new {@code permits} clause entry. The
 * pattern-matching switch in {@code AgentRequestDispatcher} will then fail to compile until the case is handled
 * — intentional.
 *
 * @param <R>
 *     The typed response record returned by this command.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Handshake.Command.class, name = "HANDSHAKE"),
    @JsonSubTypes.Type(value = MainWorld.Command.class, name = "MAIN_WORLD"),
    @JsonSubTypes.Type(value = NewWorld.Command.class, name = "NEW_WORLD"),
    @JsonSubTypes.Type(value = ExecuteCommand.Command.class, name = "EXECUTE_COMMAND"),
    @JsonSubTypes.Type(value = BlockType.Command.class, name = "BLOCK_TYPE"),
    @JsonSubTypes.Type(value = SetBlock.Command.class, name = "SET_BLOCK"),
    @JsonSubTypes.Type(value = CreatePlayer.Command.class, name = "CREATE_PLAYER"),
    @JsonSubTypes.Type(value = RemovePlayer.Command.class, name = "REMOVE_PLAYER"),
    @JsonSubTypes.Type(value = ExecutePlayerCommand.Command.class, name = "EXECUTE_PLAYER_COMMAND"),
    @JsonSubTypes.Type(value = PlacePlayerBlock.Command.class, name = "PLACE_PLAYER_BLOCK"),
    @JsonSubTypes.Type(value = LeftClickBlock.Command.class, name = "LEFT_CLICK_BLOCK"),
    @JsonSubTypes.Type(value = RightClickBlock.Command.class, name = "RIGHT_CLICK_BLOCK"),
    @JsonSubTypes.Type(value = GetOpenMenu.Command.class, name = "GET_OPEN_MENU"),
    @JsonSubTypes.Type(value = ClickMenuSlot.Command.class, name = "CLICK_MENU_SLOT"),
    @JsonSubTypes.Type(value = DragMenuSlots.Command.class, name = "DRAG_MENU_SLOTS"),
    @JsonSubTypes.Type(value = GetPlayerMessages.Command.class, name = "GET_PLAYER_MESSAGES"),
    @JsonSubTypes.Type(value = WaitTicks.Command.class, name = "WAIT_TICKS"),
    @JsonSubTypes.Type(value = GetServerTick.Command.class, name = "GET_SERVER_TICK"),
    @JsonSubTypes.Type(value = TeleportPlayer.Command.class, name = "TELEPORT_PLAYER"),
    @JsonSubTypes.Type(value = LoadChunk.Command.class, name = "LOAD_CHUNK"),
    @JsonSubTypes.Type(value = UnloadChunk.Command.class, name = "UNLOAD_CHUNK"),
    @JsonSubTypes.Type(value = IsChunkLoaded.Command.class, name = "IS_CHUNK_LOADED"),
    @JsonSubTypes.Type(value = GetPlayerInventory.Command.class, name = "GET_PLAYER_INVENTORY"),
    @JsonSubTypes.Type(value = DropItem.Command.class, name = "DROP_ITEM"),
    @JsonSubTypes.Type(value = RegisterEventListener.Command.class, name = "REGISTER_EVENT_LISTENER"),
    @JsonSubTypes.Type(value = GetCapturedEvents.Command.class, name = "GET_CAPTURED_EVENTS"),
    @JsonSubTypes.Type(value = ClearCapturedEvents.Command.class, name = "CLEAR_CAPTURED_EVENTS"),
    @JsonSubTypes.Type(value = UnregisterEventListener.Command.class, name = "UNREGISTER_EVENT_LISTENER"),
    @JsonSubTypes.Type(value = GetPlayerChatComponents.Command.class, name = "GET_PLAYER_CHAT_COMPONENTS"),
    @JsonSubTypes.Type(value = GetServerPlatform.Command.class, name = "GET_SERVER_PLATFORM"),
})
public sealed interface IAgentCommand<R extends IAgentResponse>
    permits Handshake.Command, MainWorld.Command, NewWorld.Command, ExecuteCommand.Command,
            BlockType.Command, SetBlock.Command, CreatePlayer.Command, RemovePlayer.Command,
            ExecutePlayerCommand.Command, PlacePlayerBlock.Command, LeftClickBlock.Command,
            RightClickBlock.Command, GetOpenMenu.Command, ClickMenuSlot.Command,
            DragMenuSlots.Command, GetPlayerMessages.Command, WaitTicks.Command,
            GetServerTick.Command, TeleportPlayer.Command, LoadChunk.Command,
            UnloadChunk.Command, IsChunkLoaded.Command, GetPlayerInventory.Command,
            DropItem.Command, RegisterEventListener.Command, GetCapturedEvents.Command,
            ClearCapturedEvents.Command, UnregisterEventListener.Command,
            GetPlayerChatComponents.Command, GetServerPlatform.Command
{
    /**
     * Correlation identifier matching the response's {@code requestId}.
     *
     * @return Non-blank request identifier.
     */
    String requestId();

    /**
     * Returns the concrete response class for this command, used by the client to deserialize typed responses.
     *
     * @return Non-null response class token.
     */
    Class<R> responseType();
}

package nl.pim16aap2.lightkeeper.protocol;

/**
 * Sealed response hierarchy for the LightKeeper agent protocol.
 *
 * <p>Every protocol action has a corresponding typed response record implementing this interface. The dispatcher
 * serializes a successful response by writing the domain record as JSON and injecting a {@code "success": true} field
 * and the originating command's {@code requestId}. Error responses are written as
 * {@code {"requestId":"...","success":false,"errorCode":"...","errorMessage":"..."}}.
 *
 * <p>Client side: read the response {@link tools.jackson.databind.JsonNode}, check {@code success}, then
 * deserialize via {@code objectMapper.treeToValue(root, command.responseType())}.
 */
public sealed interface IAgentResponse
    permits
    BlockType.Response,
    ClearCapturedEvents.Response,
    ClearServerErrors.Response,
    ClickMenuSlot.Response,
    CreatePlayer.Response,
    DragMenuSlots.Response,
    DropItem.Response,
    ExecuteCommand.Response,
    ExecutePlayerCommand.Response,
    GetCapturedEvents.Response,
    GetOpenMenu.Response,
    GetPlayerChatComponents.Response,
    GetPlayerInventory.Response,
    GetPlayerMessages.Response,
    GetServerErrors.Response,
    GetServerPlatform.Response,
    GetServerTick.Response,
    Handshake.Response,
    HasPlayerPermission.Response,
    IsChunkLoaded.Response,
    LeftClickBlock.Response,
    LoadChunk.Response,
    MainWorld.Response,
    MutatePlayerPermission.Response,
    NewWorld.Response,
    PlacePlayerBlock.Response,
    RegisterEventListener.Response,
    RemovePlayer.Response,
    RightClickBlock.Response,
    SetBlock.Response,
    TeleportPlayer.Response,
    UnloadChunk.Response,
    UnregisterEventListener.Response,
    WaitTicks.Response
{
}

package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.ChatComponentSnapshot;
import nl.pim16aap2.lightkeeper.framework.InventorySnapshot;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.ServerErrorSnapshot;
import nl.pim16aap2.lightkeeper.framework.ServerErrorsHandle;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandleAssertionsTest
{
    @Test
    void playerHandleAssert_hasNameAndMessages()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.receivedMessagesText()).thenReturn("hello world");

        // execute + verify
        LightkeeperAssertions.assertThat(handle)
            .hasName("bot")
            .receivedMessage("hello")
            .receivedMessagesText()
            .contains("world");
    }

    @Test
    void playerHandleAssert_shouldFailWhenNameDoesNotMatch()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(handle).hasName("other"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected player name");
    }

    @Test
    void playerHandleAssert_shouldValidateInventoryAndClickableChatText()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.inventory()).thenReturn(new InventorySnapshot(List.of(
            new MenuItemSnapshot(3, "minecraft:stone", "Stone", List.of())
        )));
        when(handle.chatComponents()).thenReturn(List.of(
            new ChatComponentSnapshot("{\"text\":\"Open\",\"clickEvent\":{\"action\":\"run_command\"}}")
        ));

        // execute + verify
        LightkeeperAssertions.assertThat(handle)
            .hasItemInInventory("minecraft:stone")
            .doesNotHaveItemInInventory("minecraft:diamond")
            .hasClickableChatText("Open");
    }

    @Test
    void playerHandleAssert_shouldFailWhenUnexpectedItemExists()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.inventory()).thenReturn(new InventorySnapshot(List.of(
            new MenuItemSnapshot(2, "minecraft:stone", "Stone", List.of())
        )));

        // execute + verify
        assertThatThrownBy(() ->
            LightkeeperAssertions.assertThat(handle).doesNotHaveItemInInventory("minecraft:stone"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("slot 2");
    }

    @Test
    void playerHandleAssert_shouldFailWhenClickableChatTextMissing()
    {
        // setup
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.chatComponents()).thenReturn(List.of(new ChatComponentSnapshot("{\"text\":\"Open\"}")));

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(handle).hasClickableChatText("Open"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("clickable chat text");
    }

    @Test
    void playerHandleAssert_shouldMatchClickEventNestedInExtraChild()
    {
        // setup — NMS serializes the clickable text inside an 'extra' child, not the root object
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.chatComponents()).thenReturn(List.of(new ChatComponentSnapshot(
            "{\"text\":\"\",\"extra\":[{\"text\":\"Open\",\"clickEvent\":{\"action\":\"run_command\"}}]}")));

        // execute + verify — a root-only check would miss this
        LightkeeperAssertions.assertThat(handle).hasClickableChatText("Open");
    }

    @Test
    void playerHandleAssert_shouldReportParseFailuresWhenComponentJsonIsMalformed()
    {
        // setup — a malformed component containing the text must not silently read as "not clickable"
        final PlayerHandle handle = mock(PlayerHandle.class);
        when(handle.name()).thenReturn("bot");
        when(handle.chatComponents()).thenReturn(List.of(new ChatComponentSnapshot("{\"text\":\"Open\" BROKEN")));

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(handle).hasClickableChatText("Open"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("failed to parse");
    }

    @Test
    void lightkeeperFrameworkAssert_shouldExposeServerOutputAndValidateNoErrors()
    {
        // setup — a captured WARNING must not fail hasNoServerErrors()
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of(serverError(
            ServerErrorSnapshot.Severity.WARNING, "WARN", "a warning, not an error")));
        when(framework.serverOutput()).thenReturn(List.of("Server started", "Done"));

        // execute + verify
        LightkeeperAssertions.assertThat(framework)
            .hasNoServerErrors()
            .serverOutput()
            .contains("Done");
    }

    @Test
    void lightkeeperFrameworkAssert_shouldFailWhenServerErrorsWereCaptured()
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of(new ServerErrorSnapshot(
            1L,
            ServerErrorSnapshot.Severity.ERROR,
            "ERROR",
            "net.example.SomePlugin",
            "Server thread",
            "boom",
            "java.lang.IllegalStateException",
            "boom",
            List.of("java.lang.IllegalStateException: boom", "\tat net.example.SomePlugin.on(SomePlugin.java:1)")
        )));

        // execute + verify — the failure message carries the structured context, including the stack trace
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(framework).hasNoServerErrors())
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("net.example.SomePlugin")
            .hasMessageContaining("boom")
            .hasMessageContaining("\tat net.example.SomePlugin.on(SomePlugin.java:1)");
    }

    @Test
    void lightkeeperFrameworkAssert_shouldIgnoreAllowlistedServerErrors()
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of(serverError(
            ServerErrorSnapshot.Severity.ERROR, "ERROR", "known moving_piston complaint")));

        // execute + verify
        LightkeeperAssertions.assertThat(framework)
            .hasNoServerErrors(error -> error.message().contains("moving_piston"));
    }

    @Test
    void lightkeeperFrameworkAssert_shouldTruncateOverlongStackTraceInFailureMessage()
    {
        // setup — more stack trace lines than the 15-line rendering cap
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        final List<String> stackTrace = IntStream.range(0, 20)
            .mapToObj(i -> "\tat net.example.SomePlugin.frame" + i + "(SomePlugin.java:" + i + ")")
            .toList();
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of(new ServerErrorSnapshot(
            1L,
            ServerErrorSnapshot.Severity.ERROR,
            "ERROR",
            "net.example.SomePlugin",
            "Server thread",
            "boom",
            "java.lang.IllegalStateException",
            "boom",
            stackTrace
        )));

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(framework).hasNoServerErrors())
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("... (5 more stack trace lines)");
    }

    @Test
    void lightkeeperFrameworkAssert_shouldRenderQuestionMarkWhenThreadNameIsEmpty()
    {
        // setup
        final ILightkeeperFramework framework = mock(ILightkeeperFramework.class);
        final ServerErrorsHandle serverErrorsHandle = mock(ServerErrorsHandle.class);
        when(framework.serverErrors()).thenReturn(serverErrorsHandle);
        when(serverErrorsHandle.getCaptured()).thenReturn(List.of(new ServerErrorSnapshot(
            1L, ServerErrorSnapshot.Severity.ERROR, "ERROR", "net.example.SomePlugin", "", "boom",
            null, null, List.of())));

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(framework).hasNoServerErrors())
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("(thread: ?)");
    }

    private static ServerErrorSnapshot serverError(
        ServerErrorSnapshot.Severity severity,
        String levelName,
        String message)
    {
        return new ServerErrorSnapshot(
            1L, severity, levelName, "net.example.SomePlugin", "Server thread", message, null, null, List.of());
    }

    @Test
    void menuHandleAssert_shouldValidateTitleAndItems()
    {
        // setup
        final MenuHandle handle = mock(MenuHandle.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(handle.hasTitle("Main")).thenReturn(true);
        when(handle.hasItemAt(0, "minecraft:stone")).thenReturn(true);
        when(handle.hasItemAt(1, itemStack)).thenReturn(true);

        // execute + verify
        LightkeeperAssertions.assertThat(handle)
            .hasTitle("Main")
            .hasItemAt(0, "minecraft:stone")
            .hasItemAt(1, itemStack);
    }

    @Test
    void menuHandleAssert_shouldFailWhenItemIsMissing()
    {
        // setup
        final MenuHandle handle = mock(MenuHandle.class);
        when(handle.hasItemAt(5, "minecraft:diamond")).thenReturn(false);

        // execute + verify
        assertThatThrownBy(() -> LightkeeperAssertions.assertThat(handle).hasItemAt(5, "minecraft:diamond"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("slot 5");
    }

    @Test
    void worldBlockAssert_shouldValidateMaterialByEnumAndKey()
    {
        // setup
        final WorldHandle handle = mock(WorldHandle.class);
        when(handle.blockTypeAt(new Vector3Di(1, 2, 3))).thenReturn("stone");
        when(handle.blockTypeAt(new Vector3Di(4, 5, 6))).thenReturn("minecraft:dirt");

        // execute + verify
        LightkeeperAssertions.assertThat(handle).hasBlockAt(1, 2, 3).ofType(Material.STONE);
        LightkeeperAssertions.assertThat(handle).hasBlockAt(4, 5, 6).ofType("dirt");
    }

    @Test
    void worldBlockAssert_shouldFailWhenMaterialDoesNotMatch()
    {
        // setup
        final WorldHandle handle = mock(WorldHandle.class);
        when(handle.blockTypeAt(new Vector3Di(0, 0, 0))).thenReturn("minecraft:stone");

        // execute + verify
        assertThatThrownBy(() ->
            LightkeeperAssertions.assertThat(handle).hasBlockAt(0, 0, 0).ofType("minecraft:dirt"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected block at (0,0,0)");
    }
}

package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkApiVisibilityTest
{
    @Test
    void constructors_shouldNotBePublicForHandleTypes() throws Exception
    {
        // setup
        final Constructor<PlayerHandle> playerConstructor =
            PlayerHandle.class.getDeclaredConstructor(IFrameworkGatewayView.class, UUID.class, String.class);
        final Constructor<MenuHandle> menuConstructor =
            MenuHandle.class.getDeclaredConstructor(IFrameworkGatewayView.class, PlayerHandle.class);
        final Constructor<WorldHandle> worldConstructor =
            WorldHandle.class.getDeclaredConstructor(IFrameworkGatewayView.class, String.class);
        final Constructor<ServerErrorsHandle> serverErrorsConstructor =
            ServerErrorsHandle.class.getDeclaredConstructor(IFrameworkGatewayView.class);
        final Constructor<PermissionControl> permissionControlConstructor =
            PermissionControl.class.getDeclaredConstructor(IFrameworkGatewayView.class, UUID.class);
        final Constructor<BlockRef> blockRefConstructor =
            BlockRef.class.getDeclaredConstructor(IFrameworkGatewayView.class, String.class, BlockPos.class);
        final Constructor<EntityQuery> entityQueryConstructor =
            EntityQuery.class.getDeclaredConstructor(IFrameworkGatewayView.class, String.class);

        // execute
        final boolean isPlayerConstructorPublic = Modifier.isPublic(playerConstructor.getModifiers());
        final boolean isMenuConstructorPublic = Modifier.isPublic(menuConstructor.getModifiers());
        final boolean isWorldConstructorPublic = Modifier.isPublic(worldConstructor.getModifiers());
        final boolean isServerErrorsConstructorPublic = Modifier.isPublic(serverErrorsConstructor.getModifiers());
        final boolean isPermissionControlConstructorPublic =
            Modifier.isPublic(permissionControlConstructor.getModifiers());
        final boolean isBlockRefConstructorPublic = Modifier.isPublic(blockRefConstructor.getModifiers());
        final boolean isEntityQueryConstructorPublic = Modifier.isPublic(entityQueryConstructor.getModifiers());

        // verify
        assertThat(isPlayerConstructorPublic).isFalse();
        assertThat(isMenuConstructorPublic).isFalse();
        assertThat(isWorldConstructorPublic).isFalse();
        assertThat(isServerErrorsConstructorPublic).isFalse();
        assertThat(isPermissionControlConstructorPublic).isFalse();
        assertThat(isBlockRefConstructorPublic).isFalse();
        assertThat(isEntityQueryConstructorPublic).isFalse();
    }

    @Test
    void facetImplementations_shouldNotBePublicAndHaveNonPublicConstructors() throws Exception
    {
        // setup
        final List<Class<?>> facadeClasses = List.of(
            Class.forName("nl.pim16aap2.lightkeeper.framework.internal.ServerControlFacade"),
            Class.forName("nl.pim16aap2.lightkeeper.framework.internal.WorldsFacade"),
            Class.forName("nl.pim16aap2.lightkeeper.framework.internal.BotsFacade"),
            Class.forName("nl.pim16aap2.lightkeeper.framework.internal.EventsFacade"));

        // execute
        final boolean allTypesNonPublic = facadeClasses.stream()
            .noneMatch(facadeClass -> Modifier.isPublic(facadeClass.getModifiers()));
        final boolean allConstructorsNonPublic = facadeClasses.stream()
            .flatMap(facadeClass -> Arrays.stream(facadeClass.getDeclaredConstructors()))
            .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()));

        // verify
        assertThat(allTypesNonPublic).isTrue();
        assertThat(allConstructorsNonPublic).isTrue();
    }
}

package nl.pim16aap2.lightkeeper.nms.v121r7;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pure, server-decoupled reflection helpers the full-login driver relies on to resolve the
 * obfuscated Spigot surface structurally. The server-coupled pipeline itself is exercised by the integration
 * tests (it cannot run without a live server).
 */
class BotLoginReflectionHelpersTest
{
    private enum SampleEnum
    {
        ALPHA,
        BETA
    }

    @SuppressWarnings("unused")
    private static final class SampleTarget
    {
        public static String staticAccessor()
        {
            return "static";
        }

        public String instanceAccessor()
        {
            return "instance";
        }

        public void ambiguous(String first, int second)
        {
        }

        private void ambiguous(int flipped, String order)
        {
        }
    }

    @Test
    void resolveEnumConstant_shouldFindConstantByName()
    {
        // execute
        final Object constant = NmsReflectionUtils.resolveEnumConstant(SampleEnum.class, "BETA");

        // verify
        assertThat(constant).isEqualTo(SampleEnum.BETA);
    }

    @Test
    void resolveEnumConstant_shouldThrowWhenNameIsUnknown()
    {
        // execute + verify
        assertThatThrownBy(() -> NmsReflectionUtils.resolveEnumConstant(SampleEnum.class, "GAMMA"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GAMMA");
    }

    @Test
    void findPublicMethod_shouldPreferPublicOverPrivateWithSameArity()
        throws Exception
    {
        // execute
        final Method method =
            NmsReflectionUtils.findPublicMethod(SampleTarget.class, void.class, String.class, int.class);

        // verify — the private (int, String) overload must not be selected.
        assertThat(method.getName()).isEqualTo("ambiguous");
        assertThat(method.getParameterTypes()).containsExactly(String.class, int.class);
    }

    @Test
    void findInstanceNoArgMethodByReturnType_shouldSkipStaticMethods()
        throws Exception
    {
        // execute
        final Method method =
            NmsReflectionUtils.findInstanceNoArgMethodByReturnType(SampleTarget.class, String.class);

        // verify
        assertThat(method.getName()).isEqualTo("instanceAccessor");
        assertThat(method.invoke(new SampleTarget())).isEqualTo("instance");
    }

    @Test
    void offlineUuid_shouldMatchBukkitOfflinePlayerScheme()
    {
        // setup
        final String name = "lk_full_login";
        final UUID expected =
            UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

        // execute
        final UUID actual = BotLoginReflection.offlineUuid(name);

        // verify
        assertThat(actual).isEqualTo(expected);
    }
}

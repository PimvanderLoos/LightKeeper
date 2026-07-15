package nl.pim16aap2.lightkeeper.nms.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation tests for {@link BotLoginRequest}.
 */
class BotLoginRequestTest
{
    @Test
    void constructor_shouldAcceptValidInputsWithNullLocale()
    {
        // execute
        final BotLoginRequest request = new BotLoginRequest("Bot", 25_565, null, Duration.ofSeconds(30));

        // verify
        assertThat(request.name()).isEqualTo("Bot");
        assertThat(request.port()).isEqualTo(25_565);
        assertThat(request.locale()).isNull();
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void constructor_shouldRejectBlankName()
    {
        // execute + verify
        assertThatThrownBy(() -> new BotLoginRequest("   ", 25_565, null, Duration.ofSeconds(30)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void constructor_shouldRejectPortOutOfRange()
    {
        // execute + verify
        assertThatThrownBy(() -> new BotLoginRequest("Bot", 70_000, null, Duration.ofSeconds(30)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port");
    }

    @Test
    void constructor_shouldRejectBlankLocaleWhenPresent()
    {
        // execute + verify
        assertThatThrownBy(() -> new BotLoginRequest("Bot", 25_565, "   ", Duration.ofSeconds(30)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("locale");
    }

    @Test
    void constructor_shouldRejectNonPositiveTimeout()
    {
        // execute + verify
        assertThatThrownBy(() -> new BotLoginRequest("Bot", 25_565, null, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeout");
    }
}

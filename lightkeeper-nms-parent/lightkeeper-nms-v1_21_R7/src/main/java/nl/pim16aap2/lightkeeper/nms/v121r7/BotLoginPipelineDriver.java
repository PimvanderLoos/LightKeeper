package nl.pim16aap2.lightkeeper.nms.v121r7;

import nl.pim16aap2.lightkeeper.nms.api.BotLoginRequest;
import nl.pim16aap2.lightkeeper.nms.api.IBotLoginDriver;
import nl.pim16aap2.lightkeeper.nms.api.IBotLoginOutcome;

import java.util.Objects;

/**
 * Paper/Spigot 1.21.11 full-login driver: drives the vanilla login pipeline over a real loopback TCP
 * connection using a fully reflective, per-distribution surface (see {@link BotLoginReflection}).
 *
 * <p>The (larger) login reflection surface is resolved once in the constructor; each {@link #login} runs an
 * independent {@link BotLoginSession}. Because it references zero server classes at compile time, one compiled
 * artifact drives both the Mojang-mapped Paper jar and the obfuscated Spigot jar.
 */
final class BotLoginPipelineDriver implements IBotLoginDriver
{
    private final BotLoginReflection reflection;

    /**
     * Resolves the full-login reflection surface for the running server.
     */
    BotLoginPipelineDriver()
    {
        this.reflection = new BotLoginReflection();
    }

    @Override
    public IBotLoginOutcome login(BotLoginRequest request)
    {
        Objects.requireNonNull(request, "request");
        return new BotLoginSession(reflection).run(request);
    }
}

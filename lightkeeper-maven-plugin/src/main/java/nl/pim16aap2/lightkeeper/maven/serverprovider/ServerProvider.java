package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.logging.Log;

/**
 * Represents a provider for a specific type of server.
 * <p>
 * This class serves as a base for different server providers, such as Spigot or Paper. It contains common properties
 * and methods that can be shared among different server types.
 */
@RequiredArgsConstructor
public abstract class ServerProvider
{
    @Getter(AccessLevel.PROTECTED)
    private final Log log;

    /**
     * The name of the server type.
     *
     * @return the name of the server type.
     */
    @Getter
    private final String name;

}

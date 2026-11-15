# LightKeeper

**Test your Minecraft plugin on a real Paper server, directly from JUnit.**

LightKeeper builds your actual plugin JAR, starts Minecraft with it installed, and lets tests control players,
commands, worlds and menus. It stops the server when the tests finish. Use it for behavior that needs the real
server, alongside your ordinary unit tests.

[Try a working project](#try-the-example) · [Add it to your plugin](#quick-start) ·
[Website](https://lightkeeper.pim16aap2.nl/) · [API reference](https://lightkeeper.pim16aap2.nl/javadoc/)

## Try the example

You need Java 21, Maven 3.9+ and network access. Linux is exercised in CI; macOS is recommended for its Unix-domain
socket transport. Native Windows execution is not yet verified. The default server is Paper 1.21.11 for this SDK.

```sh
git clone https://github.com/PimvanderLoos/LightKeeper.git
cd LightKeeper
mvn -f examples/single-module/pom.xml -Dit.test=GettingStartedIT verify
```

The example is an independent plugin project. You do not need to build or install the LightKeeper repository first.
It resolves the published SDK, packages its plugin, starts Paper and runs the test below. Success ends with
`Tests run: 1, Failures: 0, Errors: 0` for `GettingStartedIT` and `BUILD SUCCESS`. The first run downloads/prepares
server files; subsequent runs reuse caches. Download and startup times depend on your machine and connection.

## Quick start

Prefer setup for your project layout? Use the [website configurator](https://pimvanderloos.github.io/LightKeeper/setup.html).
After adding the plugin, `mvn lightkeeper:doctor` checks effective setup without starting Minecraft or changing project files.

Keep your production API dependencies and packaging. Add the following to an existing Maven plugin project that
produces a JAR with `plugin.yml` or `paper-plugin.yml`. For a separate E2E module, use the
[test-module example](examples/reactor/README.md) instead.

### 1. Add the test dependency

Use **both** LightKeeper artifacts. The **starter** supplies the JUnit engine, test API and assertions on your test
classpath. The **Maven plugin** prepares the real plugin/server inputs during your build.

```xml
<properties>
    <lightkeeper.version>1.2.0-SNAPSHOT</lightkeeper.version>
</properties>
<repositories>
    <repository>
        <id>eldonexus-snapshots</id>
        <url>https://eldonexus.de/repository/maven-snapshots/</url>
    </repository>
</repositories>
<pluginRepositories>
    <pluginRepository>
        <id>eldonexus-snapshots</id>
        <url>https://eldonexus.de/repository/maven-snapshots/</url>
    </pluginRepository>
</pluginRepositories>
<dependencies>
    <dependency>
        <groupId>nl.pim16aap2.lightkeeper</groupId>
        <artifactId>lightkeeper-junit-starter</artifactId>
        <version>${lightkeeper.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 2. Bind preparation and Failsafe

```xml
<build>
    <plugins>
        <plugin>
            <groupId>nl.pim16aap2.lightkeeper</groupId>
            <artifactId>lightkeeper-maven-plugin</artifactId>
            <version>${lightkeeper.version}</version>
            <executions>
                <execution>
                    <goals>
                        <goal>lock-build</goal>
                        <goal>prepare-server</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.6.1</version>
            <configuration>
                <failIfNoTests>true</failIfNoTests>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Paper, the supported Minecraft version, cache locations and the runtime agent are configured automatically.
The selected plugin is your module's actual packaged artifact, including Shade/Assembly output. Installation does
not require it to enable: tests of intentionally missing dependencies or load failures remain supported.

### 3. Copy the first test

Save this complete example as `src/test/java/example/GettingStartedIT.java`. It checks that a synthetic player can
place a block in the real server. It uses no commands from LightKeeper's internal test plugin.

<!-- lightkeeper:first-test -->
```java
package example;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

/** A complete first integration test; no commands or plugins from LightKeeper's internal fixtures are needed. */
@ExtendWith(LightkeeperExtension.class)
class GettingStartedIT
{
    @Test
    void placeBlock_shouldUpdateWorld(ILightkeeperFramework framework)
    {
        // setup
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().builder()
            .withName("lk_tester")
            .atSpawn(world)
            .build();

        // execute
        player.placeBlock("minecraft:stone", 1, 100, 0).andWaitTicks(1);

        // verify
        assertThat(world).hasBlockAt(1, 100, 0).ofType("minecraft:stone");
    }
}
```
<!-- /lightkeeper:first-test -->

Run it once through Maven:

```sh
mvn -Dit.test=GettingStartedIT verify
```

After that initial build, save production changes and run a JUnit class or method normally in your IDE. LightKeeper
automatically packages the current production inputs before starting the server. No manifest VM options or IDE
plugin are needed. See the [IDE guide](docs/ide-testing.md) for setup details and supported build context.

Builds report their current phase and cache reuse. Failures link a readable `index.html` overview with the cause,
next action and original logs; see [progress and reports](https://pimvanderloos.github.io/LightKeeper/ide-testing.html#progress-and-reports).

## Test your plugin's behavior next

The [standalone consumer](examples/single-module) includes a command returning values from its deployed Java code
and filtered resources. Its `SourceFreshnessIT` asserts that response inside Minecraft. Edit the production message,
run the test again and see the old expectation fail; update the expectation and it passes. The website includes
[a short walkthrough](https://lightkeeper.pim16aap2.nl/getting-started.html#source-freshness-demo).

For your own plugin, replace the example command with one it actually registers. Explicit extra plugins belong in
`src/test/lightkeeper/plugins/`, with config overlays in `src/test/lightkeeper/config/`.

## Guides and reference

- [Full setup and first test](https://lightkeeper.pim16aap2.nl/getting-started.html).
- [Separate test modules, Shade and Assembly](examples/reactor/README.md).
- [IDE runs, common setup failures and build settings](docs/ide-testing.md).
- [Assertions, fixtures, configuration and module architecture](docs/usage-reference.md).
- [Test evidence and platform limits](docs/consumer-validation.md).

## Project background

LightKeeper was developed largely with Codex CLI, with human planning and review. Its automated consumer tests
exercise actual Paper/Spigot runtimes and real Maven packaging; the evidence and remaining platform limits are
listed above. Contributor build instructions are in the [usage reference](docs/usage-reference.md#running-locally).

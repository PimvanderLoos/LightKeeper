# LightKeeper

LightKeeper is an end-to-end testing stack for Minecraft plugins.

It provisions a real server runtime (Paper or Spigot), installs your test assets, boots the server with a LightKeeper
agent, and gives your JUnit tests a high-level API for worlds, synthetic players, inventories, commands, and
assertions.

Note: This project was almost entirely made with Codex-CLI to see how good such tools are these days. It was still
planned and reviewed by a human.

## What This Project Contains

- `lightkeeper-maven-plugin`  
  Maven plugin that prepares and caches server runtimes (`prepare-server`) and optionally cleans server work
  directories (`cleanup-server`).
- `lightkeeper-framework-junit`  
  JUnit-facing API (`ILightkeeperFramework`, `LightkeeperExtension`, handles + AssertJ assertions).
- `lightkeeper-agent-spigot`  
  In-server plugin agent that exposes test operations over a local UDS channel.
- `lightkeeper-runtime-core`  
  Shared runtime protocol + runtime manifest model.
- `lightkeeper-nms-parent`  
  NMS integration modules used by the agent.
- `lightkeeper-maven-plugin-test`  
  Integration tests that run against both Paper and Spigot.

## How It Works

1. During `pre-integration-test`, `lightkeeper:prepare-server` resolves/builds a server and creates a runtime manifest.
2. Server binaries and prepared base server directories are cached in Maven local-repo cache folders.
3. The test server is started with the LightKeeper agent.
4. Your tests connect through `lightkeeper-framework-junit` (using the runtime manifest path).
5. During `post-integration-test`, `lightkeeper:cleanup-server` can delete server work directories when tests pass.

## Requirements

- Java 21
- Maven 3.9+
- Linux/macOS recommended (UDS-based transport)

## Quick Start

### 1) Add the Maven plugin and framework dependency

```xml

<build>
    <plugins>
        <plugin>
            <groupId>nl.pim16aap2.lightkeeper</groupId>
            <artifactId>lightkeeper-maven-plugin</artifactId>
            <version>${lightkeeper.version}</version>
            <configuration>
                <userAgent>LightKeeper/${project.version} ([email protected])</userAgent>
                <agentJarPath>${project.build.directory}/lightkeeper-agent/lightkeeper-agent-spigot.jar</agentJarPath>
            </configuration>
            <executions>
                <execution>
                    <id>prepare-server</id>
                    <goals>
                        <goal>prepare-server</goal>
                    </goals>
                    <configuration>
                        <serverType>paper</serverType>
                        <runtimeManifestPath>${project.build.directory}/lightkeeper/runtime-manifest.json</runtimeManifestPath>
                    </configuration>
                </execution>
                <execution>
                    <id>cleanup-server</id>
                    <phase>post-integration-test</phase>
                    <goals>
                        <goal>cleanup-server</goal>
                    </goals>
                    <configuration>
                        <deleteTargetServerOnSuccess>true</deleteTargetServerOnSuccess>
                        <failsafeSummaryPath>${project.build.directory}/failsafe-reports/failsafe-summary.xml</failsafeSummaryPath>
                        <serverWorkDirectoryRoot>${project.build.directory}/lightkeeper-server</serverWorkDirectoryRoot>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>

<dependencies>
<dependency>
    <groupId>nl.pim16aap2.lightkeeper</groupId>
    <artifactId>lightkeeper-framework-junit</artifactId>
    <version>${lightkeeper.version}</version>
    <scope>test</scope>
</dependency>
</dependencies>
```

### 2) Pass runtime manifest to tests

Use Failsafe/Surefire system properties:

```xml

<systemPropertyVariables>
    <lightkeeper.runtimeManifestPath>${project.build.directory}/lightkeeper/runtime-manifest.json</lightkeeper.runtimeManifestPath>
</systemPropertyVariables>
```

### 3) Write tests with the framework

```java

@ExtendWith(LightkeeperExtension.class)
class MyPluginIT
{
    @Test
    void playerBots_shouldInteractWithTheServer(ILightkeeperFramework framework)
    {
        // setup
        final WorldHandle world = framework.mainWorld();
        final PlayerHandle player = framework.buildPlayer()
            .withName("lk_tester")
            .atSpawn(world)
            .withPermissions("minecraft.command.time")
            .build();

        // execute
        player.executeCommand("lktestgui")
            .andWaitForMenuOpen(10)
            .verifyMenuName("Main Menu")
            .clickAtIndex(0)
            .andWaitTicks(1);

        // verify
        assertPlayer(player).receivedMessage("clicked");
        assertWorld(world).hasBlockAt(1, 100, 0).ofType("minecraft:stone");
    }
}
```

## Core Features

- Real server E2E tests (not mocks)
- Platform support: Paper + Spigot
- Runtime caching for fast repeated runs
- World provisioning (folder/archive sources)
- Plugin provisioning (filesystem path or Maven coordinates, optional transitive resolution)
- Config overlay support (copy tree into prepared server directory)
- Synthetic players and fluent interaction API
- Menu interaction and assertions
- Received-message assertions with AssertJ string chaining

## World and Plugin Provisioning

`prepare-server` supports custom worlds and plugins in plugin configuration:

```xml

<configuration>
    <worlds>
        <world>
            <name>fixture-world</name>
            <sourceType>folder</sourceType> <!-- folder | archive -->
            <sourcePath>${project.basedir}/src/test/resources/worlds/fixture-world</sourcePath>
            <loadOnStartup>false</loadOnStartup>
        </world>
    </worlds>
    <plugins>
        <plugin>
            <sourceType>path</sourceType> <!-- path | maven -->
            <path>${project.basedir}/src/test/resources/plugins/MyDependency.jar</path>
            <renameTo>MyDependency.jar</renameTo>
        </plugin>
        <plugin>
            <sourceType>maven</sourceType>
            <groupId>com.example</groupId>
            <artifactId>my-plugin</artifactId>
            <version>1.2.3</version>
            <includeTransitive>false</includeTransitive>
        </plugin>
    </plugins>
    <configOverlayPath>${project.basedir}/src/test/resources/overlay</configOverlayPath>
</configuration>
```

## Caching and Retry Behavior

- JAR cache and base-server cache are separate.
- Cache keys include server type/version/build identity, Java version, OS/arch, runtime protocol version, and agent
  identity.
- Start retries reuse the prepared artifacts; failed starts do not force a full re-download/rebuild unless explicitly
  configured via force flags.

## Running Locally

- Fast module verification:
    - `mvn -pl lightkeeper-maven-plugin-test -am verify`
- Full quality pipeline used in this repository:
  -
  `mvn clean test verify -P=errorprone -Dmaven.javadoc.skip=true install checkstyle:checkstyle pmd:check jacoco:report`

## Integration Test Logs and Reports

- Failsafe reports:
    - `lightkeeper-maven-plugin-test/target/failsafe-reports/`
- Runtime manifests:
    - `lightkeeper-maven-plugin-test/target/lightkeeper/*-runtime-manifest.json`
- Prepared server directories:
    - `lightkeeper-maven-plugin-test/target/lightkeeper-server/`

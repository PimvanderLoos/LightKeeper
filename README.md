# LightKeeper

[![](https://jitpack.io/v/PimvanderLoos/LightKeeper.svg)](https://jitpack.io/#PimvanderLoos/LightKeeper)

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
  In-server production runtime agent plugin that exposes LightKeeper RPC operations over UDS.
- `lightkeeper-protocol`
  Shared typed command and response records for the versioned agent wire protocol.
- `lightkeeper-spigot-test-plugin`
  Standalone functional test plugin that provides the `lktestgui` InventoryGUI workflow.
- `lightkeeper-runtime-core`
  Runtime manifest model and cross-process protocol constants.
- `lightkeeper-nms-parent`
  NMS integration modules used by the agent.
- `lightkeeper-integration-tests`
  Integration tests that run against both Paper and Spigot.

## How It Works

1. During `pre-integration-test`, `lightkeeper:prepare-server` resolves/builds a server and creates a runtime manifest.
2. Server binaries and prepared base server directories are cached in Maven local-repo cache folders.
3. The test server is started with the embedded LightKeeper agent (`lightkeeper-agent-spigot`) auto-provisioned by
   `lightkeeper-maven-plugin`.
4. Your tests connect through `lightkeeper-framework-junit` (using the runtime manifest path).
5. During `post-integration-test`, `lightkeeper:cleanup-server` can delete server work directories when tests pass.

## Key Features

- **Server Lifecycle Control**: Crash and restart the server mid-test to verify recovery logic.
- **World & Chunk Control**: Load/unload chunks, check chunk status, and teleport players between worlds.
- **Inventory & Item Drops**: Inspect player inventories and simulate item drops.
- **Dynamic Event Capture**: Capture and assert on any Bukkit event dynamically without writing custom listeners.
- **Clickable Chat Assertions**: Verify clickable chat message text and actions, extract a click action, and click it.
- **Command Tab-Completion**: Query a player's permission-filtered command completions exactly as a real client would.
- **Platform Awareness**: Write tests that adapt to Paper or Spigot specifics.

## Requirements

- Java 21
- Maven 3.9+
- Linux/macOS recommended (UDS-based transport)

## Developers

This project can be included as a dependency using [JitPack](https://jitpack.io/#PimvanderLoos/LightKeeper).

The usual test setup needs both the Maven plugin and the JUnit framework module. The Maven plugin provisions the server
runtime, and the framework module provides the API used by your tests.

### Maven

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>
```

```xml

<build>
    <plugins>
        <plugin>
            <groupId>com.github.PimvanderLoos.LightKeeper</groupId>
            <artifactId>lightkeeper-maven-plugin</artifactId>
            <version>1.2.0</version>
        </plugin>
    </plugins>
</build>

<dependencies>
    <dependency>
        <groupId>com.github.PimvanderLoos.LightKeeper</groupId>
        <artifactId>lightkeeper-framework-junit</artifactId>
        <version>1.2.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Both goals support `-Dlightkeeper.skip=true` to disable a whole LightKeeper lane (e.g. per job in a
CI matrix) without pom changes. Skipping `prepare-server` deletes any stale runtime manifest, so
integration tests that still run will fail fast instead of using leftover server state; combine
with `-DskipITs` to skip the tests too.


### Gradle

```gradle
repositories {
    maven { url "https://jitpack.io/" }
}

dependencies {
    testImplementation("com.github.PimvanderLoos.LightKeeper:lightkeeper-framework-junit:1.2.0")
}
```

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
        // The API is organised into facets: framework.server(), .worlds(), .bots(), .events().
        // setup
        final WorldHandle world = framework.worlds().main();
        final PlayerHandle player = framework.bots().builder()
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

> The framework surface is organised into facets — `framework.server()`, `.worlds()`, `.bots()`, and
> `.events()`. Use these facet accessors to reach the server, worlds, bots, and event-capture APIs.

## Core Features

- Real server E2E tests (not mocks)
- Platform support: Paper + Spigot
- Runtime caching for fast repeated runs
- World provisioning (folder/archive sources)
- Plugin provisioning (filesystem path or Maven coordinates, optional transitive resolution)
- Config overlay support (copy tree into prepared server directory)
- Synthetic players and fluent interaction API
- Runtime permission control per player (`player.permissions().grant/revoke/unset/has`)
- Observable interaction outcomes: block clicks return `InteractionResult` (event fired + cancellation),
  drops return `DropResult` (`DROPPED`/`CANCELLED`/`EMPTY_HAND`)
- Menu interaction and assertions; menu actions auto-wait for an open menu, and `clickItem("name")`
  clicks by item display name
- World templates: provision world folders via `<worlds>` and load them with
  `worlds().fromTemplate("name")` — typos fail loudly instead of silently creating a fresh world
- Received-message assertions with AssertJ string chaining
- Explicit retrying assertions: `eventually(timeout, () -> assertThat(...))` re-runs a live probe until it
  passes, and reports the attempt count, elapsed time, and last failure on timeout
- Typed event payloads: captured events carry real types (numbers, booleans, UUIDs, enums, entity/world
  references, nested records) — values the agent cannot encode appear as explicit `DROPPED` markers instead
  of being silently absent
- Bot chat and deterministic cancellation: `player.chat("...")` fires the real chat event;
  `capture.cancelNext(n)` cancels exactly the next N events of a captured class (LOWEST priority, so the
  capture still records the final cancelled state); captured events carry the server tick they fired on
- Command tab-completion: `player.tabComplete("/cmd")` returns the live server command map's completions for that
  player, permission-filtered exactly as a real client's tab-complete would be — a bot that lacks a command's
  permission never sees it. A leading slash is accepted and normalized; a trailing space selects argument
  completion (and is never trimmed), while an unknown command yields an empty list
- Clickable chat components: capture a player's rendered chat components with `player.chatComponents()`, extract a
  click action from a snapshot with `component.clickRunCommand()`/`component.clickSuggestedCommand()`, and dispatch
  a `run_command` click as the player with `player.clickChatComponent(component)` — the same path a real click takes
- Block state as a first-class value: place blocks with full state via
  `world.setBlockAt(pos, BlockSpec.parse("minecraft:lever[face=floor,powered=true]"))` and assert partially
  via `world.blockAt(pos).is(BlockSpec.parse("minecraft:lever[powered=true]"))` — only named properties
  are compared
- Entity queries: `world.entities().ofType("minecraft:block_display").within(min, max).count()` counts
  live entities cheaply (pairs with `eventually`), and `.snapshot()` freezes matching entities — position,
  custom name, PDC keys, and the display transformation (translation/scale/rotations) — in one main-thread
  burst so every snapshot shares one server tick
- Diagnostics-on-failure: failed tests automatically get a bundle (test outcome, captured server errors,
  server console output) under `target/lightkeeper-reports/`
- Graceful server lifecycle control from tests (`server().stop()`, `server().start()`, `server().restart()`),
  plus `server().crash()` for hard-kill scenarios
- Server directory access (`server().directory()`, `server().pluginDataDirectory(name)`) for seeding files
  while the server is stopped

### Full-login bots

By default, `bots().join(...)` and `bots().builder()...build()` spawn a synthetic player through a fast
internal path that fires `PlayerJoinEvent` but skips the login handshake. For tests that need a *real* login,
call `.fullLogin()` on the builder:

```java
final PlayerHandle bot = framework.bots().builder()
    .withName("realbot")
    .atSpawn(framework.worlds().main())
    .withLocale("en_us")   // optional client locale (LK-12), sent during the configuration phase
    .fullLogin()
    .build();
```

A full-login bot connects over a real loopback TCP connection and drives the entire vanilla login pipeline
(handshake → login → the 1.20.2+ configuration phase → play), so it behaves like a genuine client. This differs
from the default spawn in three ways:

- **Real login events fire.** `AsyncPlayerPreLoginEvent`, `PlayerLoginEvent`, and `PlayerJoinEvent` all fire, in
  each platform's own order. This is what makes permission plugins such as LuckPerms (which load a user at
  pre-login and inject their `Permissible` at login) behave correctly for the bot.
- **The offline UUID is server-derived.** The server derives the bot's UUID from its name
  (`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`); any UUID passed to the builder is ignored under
  `fullLogin()`. The returned handle carries the server-assigned UUID.
- **The join can be denied.** Because it is a genuine login, a full-login bot is subject to the whitelist, bans,
  the max-player limit, and plugin denials. A denial is surfaced as a `BotJoinDeniedException` (carrying the
  server's kick reason); a login that does not complete in time throws a `BotJoinTimeoutException`. The call
  blocks until the bot has fully joined (or is denied/times out).

Full-login joins require the server to run in offline mode with proxy forwarding off (both are the provisioner's
defaults).

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
            <sourceType>path</sourceType> <!-- path | maven | modrinth | url -->
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
        <plugin>
            <sourceType>modrinth</sourceType>
            <modrinthProject>luckperms</modrinthProject>
            <modrinthVersion>v5.5.0-bukkit</modrinthVersion>
            <modrinthLoader>bukkit</modrinthLoader>
            <renameTo>LuckPerms.jar</renameTo>
        </plugin>
        <plugin>
            <sourceType>url</sourceType>
            <url>https://example.com/plugins/MyVerifiedDependency.jar</url>
            <sha256>0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef</sha256>
        </plugin>
    </plugins>
    <configOverlayPath>${project.basedir}/src/test/resources/overlay</configOverlayPath>
</configuration>
```

### Example: Testing with Vault on Paper

Vault publishes runtime plugin jars through GitHub releases rather than Modrinth. Pin the release URL and SHA-256 so CI
uses a reproducible artifact; update both values when Vault publishes a newer release.

```xml

<properties>
    <vault.version>1.7.3</vault.version>
    <vault.url>https://github.com/MilkBowl/Vault/releases/download/${vault.version}/Vault.jar</vault.url>
    <vault.sha256>a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d</vault.sha256>
</properties>

<configuration>
    <serverType>paper</serverType>
    <serverVersion>latest-supported</serverVersion>
    <runtimeManifestPath>${project.build.directory}/lightkeeper/runtime-manifest.json</runtimeManifestPath>
    <plugins>
        <plugin>
            <sourceType>path</sourceType>
            <path>${project.build.directory}/${project.build.finalName}.jar</path>
            <renameTo>${project.artifactId}.jar</renameTo>
        </plugin>
        <plugin>
            <sourceType>url</sourceType>
            <url>${vault.url}</url>
            <sha256>${vault.sha256}</sha256>
            <renameTo>Vault.jar</renameTo>
        </plugin>
    </plugins>
</configuration>
```

```java

@ExtendWith(LightkeeperExtension.class)
class VaultIT
{
    @Test
    void vaultInfo_shouldReportVaultVersion(ILightkeeperFramework framework)
    {
        // setup
        final PlayerHandle player = framework.bots().builder()
            .withName("vault_tester")
            .atSpawn(framework.worlds().main())
            .withPermissions("vault.admin")
            .build();

        // execute
        player.executeCommand("vault-info");

        // verify
        assertPlayer(player).receivedMessage("Vault v1.7.3 Information");
    }
}
```

Plugin dependencies intentionally do not support floating latest versions. Use an exact Maven version, exact Modrinth
version/version ID, or exact URL plus checksum. `latest-supported` is available for the Paper/Spigot server version only.

## Caching and Retry Behavior

- JAR cache and base-server cache are separate.
- Maven plugin dependencies use Maven's local repository. URL and Modrinth plugin dependencies are cached under
  `lightkeeper.pluginArtifactCacheDirectoryRoot`, which defaults to
  `${settings.localRepository}/nl/pim16aap2/lightkeeper/cache/plugins`.
- URL plugin dependencies require SHA-256. Modrinth plugin files are verified against Modrinth's file checksum before
  entering the LightKeeper plugin cache.
- Cache keys include stable server artifact identity (Paper jar SHA-256 or Spigot BuildTools identity), with
  Java/OS included for build-sensitive Spigot outputs.
- `prepare-server` prunes expired unused cache-key directories by default (`cleanupUnusedCacheDirectories=true`).
  Expiry thresholds reuse `jarCacheExpiryDays` and `baseServerCacheExpiryDays`.
- To disable automatic unused-cache pruning, set:
  `<cleanupUnusedCacheDirectories>false</cleanupUnusedCacheDirectories>`
- Start retries reuse the prepared artifacts; failed starts do not force a full re-download/rebuild unless explicitly
  configured via force flags.

GitHub Actions can cache URL and Modrinth plugin downloads separately from the normal Maven cache:

```yaml
- uses: actions/cache@v4
  with:
    path: ~/.m2/repository/nl/pim16aap2/lightkeeper/cache/plugins
    key: lightkeeper-plugin-cache-${{ runner.os }}-${{ hashFiles('**/pom.xml') }}
```

## Running Locally

- Fast module verification:
    - `mvn -pl lightkeeper-integration-tests -am verify`
- Full quality pipeline used in this repository:
  -
  `mvn clean test verify -P=errorprone -Dmaven.javadoc.skip=true install checkstyle:checkstyle pmd:check jacoco:report`

## Integration Test Logs and Reports

- Failsafe reports:
    - `lightkeeper-integration-tests/target/failsafe-reports/`
- Diagnostics bundles (written by the JUnit extension when a test fails):
    - `target/lightkeeper-reports/<TestClass>/<testMethod>-<timestamp>/` with `outcome.txt`,
      `server-errors.txt`, and `server-output.log`
    - Control via system properties: `lightkeeper.diagnostics` = `on-failure` (default) | `always` | `off`,
      and `lightkeeper.diagnosticsDirectory` to relocate the report root
- Runtime manifests:
    - `lightkeeper-integration-tests/target/lightkeeper/*-runtime-manifest.json`
- Prepared server directories:
    - `lightkeeper-integration-tests/target/lightkeeper-server/`

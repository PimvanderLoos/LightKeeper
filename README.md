# LightKeeper

[Documentation](https://lightkeeper.pim16aap2.nl/) · [API Reference and versions](https://lightkeeper.pim16aap2.nl/javadoc/)


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

Use the EldoNexus snapshot repository for both dependencies and build plugins. The native group is
`nl.pim16aap2.lightkeeper`; the current development version is `1.2.0-SNAPSHOT`.

Add the starter with **test scope**. It supplies the framework, Jupiter API/engine/launcher, AssertJ and the Bukkit API
used by test handles. The existing `lightkeeper-framework-junit` remains available if you manage those dependencies.
Keep your existing production API dependencies and packaging configuration.

See the [starter README](lightkeeper-junit-starter/README.md) for what it includes and how its role differs from
`lightkeeper-maven-plugin`.

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

### Gradle dependency

For an explicitly prepared runtime, the same test dependency is available to Gradle projects:

```gradle
repositories {
    maven { url "https://eldonexus.de/repository/maven-snapshots/" }
    maven { url "https://hub.spigotmc.org/nexus/content/groups/public/" }
}
dependencies {
    testImplementation("nl.pim16aap2.lightkeeper:lightkeeper-junit-starter:1.2.0-SNAPSHOT")
}
```

The Maven goals below provision the runtime; adding a Gradle dependency does not register Maven lifecycle goals.
The explicit `Lightkeeper.start(Path)` / `lightkeeper.runtimeManifestPath` route remains available.

## Quick Start

### 1) Bind preparation and Failsafe in your existing plugin module

The module must package a deployable plugin with `plugin.yml` or `paper-plugin.yml`. LightKeeper selects its actual
Maven main artifact after packaging, including Shade/Assembly replacement. It does not reconstruct a JAR filename.
Paper, the supported Minecraft version, cache paths, Java and a LightKeeper-specific User-Agent have defaults.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>nl.pim16aap2.lightkeeper</groupId>
            <artifactId>lightkeeper-maven-plugin</artifactId>
            <version>${lightkeeper.version}</version>
            <executions>
                <execution>
                    <goals><goal>prepare-server</goal></goals>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.6.1</version>
            <configuration>
                <failIfNoTests>true</failIfNoTests>
                <systemPropertyVariables>
                    <lightkeeper.runtimeManifestPath>${project.build.directory}/lightkeeper/runtime-manifest.json</lightkeeper.runtimeManifestPath>
                </systemPropertyVariables>
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

The extension stops the server process; a separate `cleanup-server` execution is optional. Failsafe 3.6 uses
`-DskipITs` for integration tests and `-Dmaven.test.skip=true` for all tests. `-DskipTests` alone skips Surefire unit
tests; with Failsafe 3.5 it also skips ITs. Preparation respects the effective Failsafe skip configuration.
`-Dlightkeeper.skip=true` still skips LightKeeper preparation/cleanup; skip the ITs too if they need that runtime.

### 2) Select another producer or add fixtures when needed

A separate E2E module needs a real test-scoped dependency on the producer for reactor order and `-am`. Select that
existing dependency with:

```xml
<configuration>
    <pluginUnderTest>
        <artifactId>spigot-packager</artifactId>
    </pluginUnderTest>
</configuration>
```

Add `groupId` if the short selector is ambiguous. A `classifier` alone selects the current project's attached JAR;
a classifier on a dependency selector matches the declared dependency classifier. An explicit `path` is authoritative
and cannot be combined with artifact coordinates. A failed explicit selection never falls back to another JAR.
Use `<serverOnly>true</serverOnly>` for intentional server-only tests.

Extra fixture JARs in `src/test/lightkeeper/plugins/*.jar` are installed in sorted order; server config files in
`src/test/lightkeeper/config/` are overlaid automatically. Missing directories are normal. An explicit
`configOverlayPath` replaces the conventional overlay. Existing path/Maven/URL/Modrinth asset configuration remains
available. Duplicate inputs are installed once; conflicting output names or replacements of the target/agent fail.

Legacy explicit `plugins` lists keep their original asset-only behavior when `pluginUnderTest` is omitted, with a
migration warning. Add `<pluginUnderTest/>` to select this module as the target while retaining explicit additional
assets, or choose a producer/path/serverOnly. This preserves existing test-only modules without guessing their target.

Validation checks archive/descriptor metadata before server provisioning. It does not require the target to load or
be enabled, check that the main class exists, or install dependencies named in plugin metadata. Include Vault or
other dependency plugins explicitly only when your test scenario needs them.

### Run tests from your IDE

The existing durable IDE preparation remains available using `-Dlightkeeper.ide=true`. Repackage changed production
sources before refreshing that preparation; an existing JAR hash alone cannot prove that sources were built.
Discovered runs use a private server session per test class. Methods share that session; `@FreshServer` and explicit
restarts preserve its world data. Independent classes and launches start from clean prepared inputs. Explicit manifest
paths retain control over their existing server directory. See the [IDE test setup guide](docs/ide-testing.md).

### 3) Write tests with the framework

```java
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

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
        assertThat(player).receivedMessage("clicked");
        assertThat(world).hasBlockAt(1, 100, 0).ofType("minecraft:stone");
    }
}
```

> The framework surface is organised into facets — `framework.server()`, `.worlds()`, `.bots()`, and
> `.events()`. Use these facet accessors to reach the server, worlds, bots, and event-capture APIs.

### Assertions

Use the static `LightkeeperAssertions.assertThat` entry point for domain checks and ordinary AssertJ checks:

```java
assertThat(framework)
    .server()
    .plugin("MyPlugin")
    .isEnabled();
assertThat(player).hasPermission("myplugin.use");
assertThat(player)
    .inventory()
    .itemAt(0)
    .isMaterial("minecraft:stone");
assertThat(menu)
    .snapshot()
    .isOpen()
    .hasTitle("Main Menu");
assertThat(capture)
    .events()
    .anySatisfy(
        event -> assertThat(event)
            .value("message")
            .asString()
            .isEqualTo("hello")
    );
assertThat(component).runsCommand("Click me", "/myplugin confirm");
```

Assertions are eager and read-only. Handle checks query current state; navigation to snapshots or collections freezes
that observation. For asynchronous effects, recreate the complete observation inside the retry callback:

```java
LightkeeperAssertions.eventually(
    Duration.ofSeconds(10),
    () -> assertThat(player)
        .receivedMessages()
        .contains("done")
);
```

Item checks cover material, display name, and lore, without implying full Bukkit item equality.
The [assertion coverage register](docs/assertion-coverage.md) records every supported surface, intentional exclusions,
and deferred assertions that require additional readbacks.

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
    <pluginUnderTest/>
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
    <pluginUnderTest/>
    <plugins>
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
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;

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
        assertThat(player).receivedMessage("Vault v1.7.3 Information");
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

For public API changes, update the [assertion coverage and decisions](docs/assertion-coverage.md) register, including
explicit reasons for APIs that should not receive custom assertions.

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

# LightKeeper usage reference

Run commands below from the repository root unless a consumer path is shown. For the first working test,
start with the [quickstart](../README.md#quick-start). This reference covers APIs, fixtures, configuration
and contributor workflows after that first successful run.

## What This Project Contains

| Module | Purpose |
| --- | --- |
| `lightkeeper-junit-starter` | Test dependency supplying the framework, coherent JUnit stack, AssertJ and Bukkit test API. |
| `lightkeeper-maven-plugin` | Build plugin protecting producer outputs and preparing real server inputs from Maven's packaged artifacts. |
| `lightkeeper-framework-junit` | JUnit extension, automatic native preparation, server control, handles and assertions. |
| `lightkeeper-provisioning` | Shared server downloads, caches, assets, immutable snapshots and private sessions, without Maven internals. |
| `lightkeeper-agent-spigot` | In-server RPC agent, installed automatically. |
| `lightkeeper-runtime-core` / `lightkeeper-protocol` | Runtime metadata and typed cross-process commands. |
| `lightkeeper-nms-parent` | Server-revision adapters used by the agent. |
| `lightkeeper-spigot-test-plugin` / `lightkeeper-integration-tests` | Functional fixture plugin and full-stack Paper/Spigot tests. |

Use **both the starter and Maven plugin** in an ordinary Maven consumer. The starter puts the test API and engine on
its test classpath; the build plugin binds preparation to Maven. Neither declaration replaces the other.

## How It Works

1. `lock-build` acquires the selected modules' output leases before compilation and packaging change outputs.
2. Your ordinary Maven packaging runs, including filtering, generated inputs, Shade and Assembly. `prepare-server`
   installs the actual selected artifact and captures an immutable server snapshot.
3. Failsafe consumes its live Maven invocation's snapshot. After one `mvn verify`, a direct JUnit class/method run
   automatically invokes Maven to package current saved production sources and resources before preparing its snapshot.
4. Each test class receives a private server session. Methods share that session; process restarts retain its world.
5. The extension stops Minecraft and removes successful sessions. Failed sessions retain diagnostic files;
   `cleanup-server` remains optional.

## Key Features

- **Server Lifecycle Control**: Crash and restart the server mid-test to verify recovery logic.
- **World & Chunk Control**: Load/unload chunks, check chunk status, and teleport players between worlds.
- **Inventory & Item Drops**: Inspect player inventories and simulate item drops.
- **Dynamic Event Capture**: Capture and assert on any Bukkit event dynamically without writing custom listeners.
- **Clickable Chat Assertions**: Verify clickable chat message text and actions, extract a click action, and click it.
- **Command Tab-Completion**: Query a player's permission-filtered command completions exactly as a real client would.
- **Platform Awareness**: Write tests that adapt to Paper or Spigot specifics.


## Assertions

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
The [assertion coverage register](assertion-coverage.md) records every supported surface, intentional exclusions,
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

For public API changes, update the [assertion coverage and decisions](assertion-coverage.md) register, including
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

## Diagnose setup without running a server

Run `mvn lightkeeper:doctor`, or `mvn -pl :your-e2e-module -am lightkeeper:doctor` from a reactor root.
The aggregate goal inspects the selected preparation modules once: Maven/Java, transport, effective server settings,
producer guards, target selection, Failsafe bindings/skips and direct-JUnit ownership metadata. Configured server
JDK versions are read from local release metadata without executing them; unknown wrapper versions are warnings. It never executes build
phases, resolves server downloads or writes project files; Maven may resolve the plugin itself. Errors include a fix
and fail the command; warnings cover expected first-run conditions such as an unbuilt JAR or absent IDE metadata.
Run normal `verify` once to initialize those generated inputs. It validates available selected archives statically,
without loading classes or requiring the target plugin to enable.

The [website configurator](https://pimvanderloos.github.io/LightKeeper/setup.html) supplies fragments derived from the
executable single-module and reactor examples, plus a complete first test. Merge existing POM sections and preserve
your compiler, API dependency and packaging configuration. No automatic POM rewriting is performed.

## Preparation progress and failure reports

Direct JUnit streams actual Maven resource/compile/package phases, producer-guard waits and LightKeeper server
preparation. A 15-second elapsed-time update shows that a long phase is still running. Normal Maven preparation
reports whether the server JAR and base-server caches are reused or refreshed; first-run server bootstrap is explicit.
The framework reports server startup, agent connection and readiness with measured elapsed time. There are no
percentage estimates or assumptions that the tested plugin must enable successfully.

Build failure messages include the first useful Maven error (including compiler source locations), module, phase,
next action, original log and a self-contained HTML report. Failed native builds retain
`.lightkeeper/launches/<id>/index.html` beside `maven.log`; successful launch metadata is cleaned normally.
Server lifecycle failures link their report in the existing `lightkeeper-diagnostics/bundle-<timestamp>/` directory
beside the server directory. Per-test diagnostics add `index.html` to the existing outcome/errors/console bundle
under `target/lightkeeper-reports/` (relative to the test JVM's working directory). Existing diagnostics mode and
directory properties keep their meaning. Overview excerpts are bounded, escaped and redact common credential
fields; original complete logs remain unchanged. Report creation is best-effort and never replaces the original
failure. Fix the reported build error and rerun; a failed build never uses an older prepared runtime.

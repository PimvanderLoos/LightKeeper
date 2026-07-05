# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

LightKeeper is a Maven-based end-to-end testing stack for Minecraft plugins. It provisions a real Paper or Spigot server, installs test assets, boots the server with an embedded agent plugin, and gives JUnit tests a high-level API for worlds, synthetic players, inventories, events, and assertions — all over a Unix Domain Socket (UDS) transport.

## Build Commands

```bash
# Fast module verification (unit + integration tests, no quality gates)
mvn -pl lightkeeper-integration-tests -am verify

# Full quality pipeline (what CI runs)
mvn --batch-mode -Perrorprone clean verify checkstyle:checkstyle pmd:check jacoco:report -Dgoal=helpmojo

# Unit tests only (single module)
mvn -pl <module-name> test

# Run a specific test class
mvn -pl <module-name> test -Dtest=MyClassTest

# Skip quality checks during development
mvn -pl lightkeeper-integration-tests -am verify -Dcheckstyle.skip=true -Dpmd.skip=true
```

## Code Quality

Three enforced gates — all must pass before merge:

- **Checkstyle**: rules in `coderules/checkstyle.xml`, suppressions in `coderules/checkstyle-suppressions.xml`
- **PMD**: ruleset in `coderules/pmd-ruleset.xml`
- **ErrorProne + NullAway** (profile `errorprone`): enabled by `-Perrorprone`; NullAway targets `nl.pim16aap2` packages; suppress with `@SuppressWarnings("NullAway")` only when unavoidable (e.g. after a preceding `isNotNull()` guard)

## Module Architecture

```
lightkeeper-protocol              Versioned typed RPC commands and responses shared by client and agent.

lightkeeper-runtime-core          RuntimeManifest (written by plugin, read by framework) and shared
                                  cross-process protocol constants.

lightkeeper-agent-spigot          Bukkit plugin deployed into the test server. AgentRequestDispatcher routes
                                  JSON-over-UDS requests to AgentWorldActions / AgentPlayerActions /
                                  AgentMenuActions / AgentEventCapture.

lightkeeper-framework-junit       Test-side JUnit API. LightkeeperExtension manages server lifecycle.
                                  ILightkeeperFramework is the test entry point. UdsAgentClient sends
                                  requests over the UDS socket.

lightkeeper-maven-plugin          prepare-server and cleanup-server Mojos. Downloads/builds server JARs,
                                  provisions plugins/worlds, writes RuntimeManifest, and starts the server
                                  process before Failsafe runs.

lightkeeper-nms-parent            NMS abstraction: lightkeeper-nms-api defines the interface;
                                  lightkeeper-nms-v1_21_R7 implements it for MC 1.21.

lightkeeper-spigot-test-plugin    Functional test plugin providing the lktestgui InventoryGUI workflow,
                                  used by the integration tests themselves.

lightkeeper-integration-tests     Integration tests that run against both Paper and Spigot via two
                                  independent Failsafe executions (integration-paper, integration-spigot).

lightkeeper-report-aggregate      JaCoCo aggregate coverage report across all modules.
```

## Request Flow

1. Test calls `ILightkeeperFramework` method → `UdsAgentClient` sends a typed `IAgentCommand` as JSON over UDS.
2. `SpigotLightkeeperAgentPlugin` reads from the socket on the server side → `AgentRequestDispatcher` parses the request and routes it to the appropriate domain handler.
3. The domain handler executes on the Bukkit main thread (via `AgentMainThreadExecutor`) and returns the command's typed response.
4. `UdsAgentClient` deserialises the response and returns the typed result to the test.

`RuntimeManifest` (written by `PrepareServerMojo`, read by `LightkeeperExtension`) carries the UDS socket path, auth token, protocol version, and server directory. The system property `lightkeeper.runtimeManifestPath` wires the two together.

## Test Server Lifecycle

`LightkeeperExtension` has two scoping modes:

- **Shared** (default): one server instance per test class; `beginMethodScope` / `endMethodScope` clean up per-test state (players, etc.) between methods.
- **Fresh** (`@FreshServer`): fresh server per class or per method. Place on the test class for all methods, or on individual methods for per-method restarts.

Tests are named `*IT.java` for integration tests (picked up by Failsafe) and `*Test.java` for unit tests (Surefire).

## Integration Test Reports

After running `lightkeeper-integration-tests`:

- Failsafe reports: `lightkeeper-integration-tests/target/failsafe-reports/{paper,spigot}/`
- Runtime manifests: `lightkeeper-integration-tests/target/lightkeeper/*-runtime-manifest.json`
- Server work dirs: `lightkeeper-integration-tests/target/lightkeeper-server/{paper,spigot}/`

## Key Dependency Versions

| Library       | Version  | Property                   |
|---------------|----------|----------------------------|
| Java          | 21       | `maven.compiler.release`   |
| JUnit         | 6.0.3    | `version.junit`            |
| Mockito       | 5.23.0   | `version.mockito`          |
| AssertJ       | 4.0.0-M1 | `version.assertj`          |
| Lombok        | 1.18.44  | `version.lombok`           |
| Dagger        | 2.56.1   | `version.dagger`           |
| Jackson       | 3.1.3    | `version.jackson-databind` |
| Spigot API    | 1.21.11-R0.2-SNAPSHOT | `version.spigot-api` |

Versions are centralised in the root `pom.xml` and referenced as `${version.*}` properties.

## Assertions

Import from `LightkeeperAssertions` (extends AssertJ's `Assertions`):

```java
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.*;

assertThat(player).receivedMessage("clicked");
assertThat(world).hasBlockAt(1, 100, 0).ofType("minecraft:stone");
assertThat(menu).hasTitle("Main Menu");
```

## NMS Layer

When adding NMS-dependent behaviour, define the contract in `lightkeeper-nms-api` and implement it in `lightkeeper-nms-v1_21_R7`. New MC versions get their own `-nms-vX_XX_RY` module added to `lightkeeper-nms-parent`.

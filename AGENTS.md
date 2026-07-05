## Build & Validation

- Before committing, run all checks: `mvn clean verify install -Perrorprone -Dmaven.javadoc.skip=true checkstyle:checkstyle pmd:check jacoco:report`
  All checks must pass. Fix any errors before proposing changes.


## Git handling

- When adding new files, also add them to git.
- Never add `AGENTS.md` or any file in the `.codex` directory.
- Rationale: `.codex` is local Codex-cli working state (plans/scratch output) and must stay out of version control.

## Project LightKeeper

LightKeeper is an end-to-end integration testing stack for Minecraft plugins.
It runs real Paper/Spigot server instances, injects a LightKeeper runtime agent plugin, and lets JUnit tests
control/assert server state through a typed framework API.

### What LightKeeper does

- Provisions test server runtimes via Maven (`prepare-server` goal):
  - Resolves/builds Paper or Spigot server artifacts
  - Caches server artifacts and prepared base server directories
  - Installs test assets (plugins, worlds, config overlays)
  - Auto-provisions the embedded `lightkeeper-agent-spigot` plugin
  - Writes a runtime manifest JSON for test execution
- Executes tests via JUnit framework:
  - `LightkeeperExtension` injects `ILightkeeperFramework`
  - Framework starts/stops the server process from the runtime manifest
  - Framework connects to in-server agent over Unix Domain Socket (UDS)
- Performs rich E2E interactions/assertions:
  - World operations (create worlds, set/query blocks, load/unload chunks)
  - Synthetic players (spawn/remove, permissions, health, commands, teleportation, inventory, item drops)
  - GUI/menu actions (snapshot, click, drag)
  - Dynamic event capture (listen to any Bukkit event)
  - Message/chat assertions (plain text and clickable components)
  - Fluent AssertJ helpers and platform awareness (Paper/Spigot)
  - Server lifecycle control (crash and restart mid-test)

### Primary modules

- `lightkeeper-maven-plugin`: Maven goals `prepare-server` and `cleanup-server` for provisioning and lifecycle cleanup.
- `lightkeeper-framework-junit`: Public test API (`ILightkeeperFramework`, handles/builders, JUnit extension, assertions).
- `lightkeeper-agent-spigot`: In-server RPC agent handling handshake/auth and action dispatch.
- `lightkeeper-protocol`: Shared typed command and response records for the versioned wire protocol.
- `lightkeeper-runtime-core`: Shared protocol constants and runtime manifest model.
- `lightkeeper-nms-parent`:
  - `lightkeeper-nms-api`: NMS contracts
  - `lightkeeper-nms-v1_21_R7`: Concrete adapter implementation
- `lightkeeper-spigot-test-plugin`: Functional fixture plugin used by integration tests (`/lktestgui` flow).
- `lightkeeper-integration-tests`: Full-stack tests validating provisioning + runtime behavior on both Paper and Spigot.
- `lightkeeper-report-aggregate`: Aggregated reporting module.

### Runtime flow (canonical)

1. Maven `prepare-server` runs in `pre-integration-test`.
2. Runtime manifest is generated with socket path/auth token/protocol/server paths.
3. JUnit tests start framework (`Lightkeeper.start(...)` or `LightkeeperExtension`).
4. Framework starts Minecraft server process and handshakes with the agent via UDS.
5. Tests execute actions/assertions through framework handles.
6. Maven `cleanup-server` may delete server work directories after successful test runs.

### Current platform/runtime constraints

- Java 21, Maven 3.9+
- Transport is UDS (Linux/macOS preferred)
- Agent validates supported Bukkit/NMS version at startup (currently 1.21.11 / `v1_21_R7` in this codebase)

### Typical test API examples

- `framework.mainWorld()`, `framework.newWorld(...)`
- `framework.buildPlayer().withPermissions(...).build()`
- `player.executeCommand("lktestgui").andWaitForMenuOpen(...).clickAtIndex(...)`
- `assertThat(world).hasBlockAt(x, y, z).ofType("minecraft:stone")`
- `assertThat(player).receivedMessage("...")`

## Package and naming conventions

- Keep public framework API in `lightkeeper-framework-junit/.../framework`; keep implementation details in
  `lightkeeper-framework-junit/.../framework/internal`.
- Keep runtime protocol commands/responses in `lightkeeper-protocol/.../protocol`; keep runtime manifests and
  cross-process constants in `lightkeeper-runtime-core/.../runtime`.
- Keep in-server action handling in `lightkeeper-agent-spigot/.../agent/spigot`.
- Keep Maven provisioning/lifecycle logic in `lightkeeper-maven-plugin/.../maven/...`.
- Naming conventions:
  - `*Handle`: user-facing runtime objects (`WorldHandle`, `PlayerHandle`, `MenuHandle`).
  - `I*Builder` + `Default*Builder`: fluent builders and default implementations.
  - `*Spec`: immutable input configuration value objects.
  - `*Snapshot`: immutable read models returned from agent/framework state reads.
  - `*Actions`: grouped agent-side action executors per domain.
  - `*Mojo`: Maven goals.
  - `*Provider`: server resolution/provisioning backends.

## Error handling and logging conventions

- Validate inputs at API boundaries and fail fast with `IllegalArgumentException` for invalid caller input.
- Use `IllegalStateException` for runtime/protocol/lifecycle failures that represent invalid state or failed operations.
- Keep RPC/protocol failures structured in agent responses (`success/errorCode/errorMessage`) and translate once at
  the framework boundary.
- Log at lifecycle boundaries (start/stop/major transitions/failures) and avoid duplicate logging at multiple layers.
- Include actionable context in thrown exceptions and logs (action, identifiers, file/path, protocol versions).

## Test conventions

- Place module unit tests in each module under `src/test/java`.
- Place cross-module end-to-end tests in `lightkeeper-integration-tests`.
- Keep integration tests focused on real runtime behavior (server startup, RPC actions, world/player/menu assertions).
- Test method names must follow `methodToTest_whatWeTest`.
- Test bodies must include explicit `// setup`, `// execute`, and `// verify` sections.
- For new functionality, cover both:
  - Happy-path behavior.
  - Failure/validation behavior with clear assertions.

## How to extend LightKeeper

### Add a new runtime RPC action

1. Add a namespace class in `lightkeeper-protocol` with nested `Command` and `Response` records.
2. Register the command in `IAgentCommand` with both a `@JsonSubTypes.Type` entry and a sealed `permits` entry;
   add the response to `IAgentResponse`'s `permits` list.
3. Implement the relevant agent-side `*Actions` handler and add the exhaustive dispatcher switch arm.
4. Add the typed framework client call in `lightkeeper-framework-junit/.../framework/internal/UdsAgentClient`.
5. Expose API through `ILightkeeperFramework`, handle/builder types, and assertions if user-facing.
6. Add protocol serialization, agent dispatch/handler, and framework client unit tests.
7. Add integration coverage in `lightkeeper-integration-tests` proving end-to-end behavior on Paper and Spigot.

### Add support for a new NMS/server revision

1. Add a new module under `lightkeeper-nms-parent` (for example `lightkeeper-nms-vX_Y_RZ`).
2. Implement `IBotPlayerNmsAdapter` in that module.
3. Register the adapter in `SpigotLightkeeperAgentPlugin` NMS adapter map and compatibility checks.
4. Update module dependencies/shading so agent packaging includes the new adapter.
5. Add focused adapter unit tests and end-to-end integration coverage for synthetic player behavior.
6. Keep existing adapters untouched unless behavior must be shared; avoid version-specific branching outside adapter
   boundaries.

### Add a new framework API surface (handle/builder/assertion)

1. Define/extend the public interface in `lightkeeper-framework-junit/.../framework`.
2. Implement behavior in `.../framework/internal` using `IFrameworkGateway` and `UdsAgentClient`.
3. Preserve lifecycle invariants: framework must be open, player scope must be tracked/cleaned up correctly.
4. Add/extend AssertJ assertions in `.../framework/assertions` for the new behavior.
5. Add unit tests plus integration tests that demonstrate fluent API usage and runtime effect.

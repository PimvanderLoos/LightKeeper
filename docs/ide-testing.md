# IDE test setup

IDE test setup prepares one local Minecraft runtime so ordinary JUnit class and method runs can start LightKeeper
without VM options, a Maven before-launch task, or `junit-platform.properties`. Preparation is explicit; later IDE runs
discover and validate the runtime owned by the test's Maven module.

## Consumer setup

Prerequisites are Java 21 or newer, Maven 3.9 or newer, Linux or macOS for Unix-domain sockets, and a released
LightKeeper version containing IDE test setup. Keep the existing `prepare-server` execution and framework dependency
from the main README.

1. Package or install every locally developed plugin referenced by the LightKeeper Maven configuration.
2. Run the configured preparation execution from the module or reactor that owns the tests:

   ```sh
   mvn lightkeeper:prepare-server@prepare-server -Dlightkeeper.ide=true
   ```

3. Confirm the output says `Created IDE test setup` or `Reused IDE test setup` and names the expected module and
   platform. It deliberately does not print the runtime authentication token.
4. Click the JUnit gutter button beside a LightKeeper test class or method.

For plugin configuration inherited from a parent POM, invoke the goal for the child module containing the test output.
In a multi-module checkout, each child module owns its own `.lightkeeper/ide/` discovery record; LightKeeper never
selects a parent or sibling module's runtime.

## LightKeeper contributor setup

Install the current plugin, framework, agent, and fixture plugin before invoking the goal directly:

```sh
mvn -pl lightkeeper-integration-tests -am install \
  -DskipTests -DskipITs -Dlightkeeper.skip=true -Dmaven.javadoc.skip=true
mvn -pl lightkeeper-integration-tests \
  lightkeeper:prepare-server@prepare-server-paper -Dlightkeeper.ide=true
```

The second command selects Paper. Replace the execution ID with `prepare-server-spigot` to prepare and select Spigot:

```sh
mvn -pl lightkeeper-integration-tests \
  lightkeeper:prepare-server@prepare-server-spigot -Dlightkeeper.ide=true
```

The selected execution supplies the platform identity. The repository's `lightkeeper.expectedServerType` property is
only a Failsafe assertion input and is not required for gutter launches.

## Daily use and refresh

Normal IDE compilation is enough after test-only changes. `mvn clean` removes `target` but leaves the prepared runtime
under the owning module's `.lightkeeper/ide/`; compiling the tests again restores the class-output anchor used for
discovery. Ordinary unit tests that do not use `LightkeeperExtension` or `Lightkeeper.start(Class<?>)` do no LightKeeper
work.

After changing a server plugin, package/install that artifact and rerun the same setup command. Rebuild the LightKeeper
agent after framework changes that also require new protocol or agent behavior. A setup rerun reuses the server when
the effective packaged inputs are unchanged and creates a new preparation when their hashes or launch configuration
change.

To discard persistent world/plugin state without changing inputs, request a fresh runtime:

```sh
mvn lightkeeper:prepare-server@prepare-server -Dlightkeeper.ide=true -Dlightkeeper.ide.refresh=true
```

Refresh creates a new preparation and atomically switches later launches to it. It never mutates a runtime held by a
running test. Old preparations are not pruned automatically in this release. To recover all generated state, close any
running LightKeeper test and move the module's `.lightkeeper/` directory to trash, then run setup again.

## Overrides and persistence

`-Dlightkeeper.runtimeManifestPath=/absolute/path/runtime-manifest.json` remains authoritative for a launch. An invalid
explicit path fails immediately and does not fall back to IDE discovery. `Lightkeeper.start(Path)` has the same explicit
contract. Custom IDE class-output layouts are not guessed; use the explicit property for those layouts.

The prepared server is intentionally persistent. Worlds, plugin data, and other live server files carry over between
runs. The setup fingerprint records resolved server metadata, launch settings, the embedded agent, deployed plugins,
world templates, and overlays. Launch validation proves that durable files are intact and that still-present local
inputs match what setup examined. It cannot detect unbuilt source edits or a newly published remote snapshot until
Maven resolves those inputs during another setup run.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Discovery is missing or corrupt | Setup has not run for this module, or `.lightkeeper` was removed | Rerun the module's setup execution |
| Ownership or checkout path mismatch | The checkout/module moved after setup | Rerun setup in the new checkout |
| Runtime is stale or incompatible | A required artifact, source input, schema, or protocol changed | Rebuild local artifacts, then rerun setup |
| Runtime is busy | Another setup or test JVM holds the module lock | Close that test/server and retry |
| Server state is dirty | Reuse preserves worlds and plugin data | Run setup with `-Dlightkeeper.ide.refresh=true` |
| Server cache disappeared | Durable preparation references a removed required artifact | Rerun setup; Maven recreates what is needed |

Debug the JUnit side with the IDE's test-JVM debugger. Code inside Bukkit/Paper runs in the separate server JVM; use
server JVM debug arguments in the Maven plugin configuration when that process must be debugged.

## Verified workflow

The command-line JUnit workflow was verified on 2026-09-06 with IntelliJ IDEA 2026.2.2 installed, Temurin JDK 25.0.4
(project release 21), Maven 3.9.9, and Linux 7.2.2. The actual IntelliJ gutter UI was not driven from the CLI session.

| Check | Command/result |
| --- | --- |
| First Paper setup with an expired cached jar | `prepare-server-paper -Dlightkeeper.ide=true`: created in 1.864 s |
| Repeated setup | same command: reused in 0.893 s |
| Clean and compile | `mvn -pl lightkeeper-integration-tests clean test-compile -DskipITs`: passed in 2.570 s |
| Class selection without manifest property | `-Dtest=LightkeeperExtensionIT test -DskipITs`: 2 tests passed in 18.412 s |
| Method selection without manifest property | `-Dtest=LightkeeperExtensionIT#mainWorld_shouldInjectFrameworkFromExtension test -DskipITs`: passed in 11.143 s |
| Ordinary unit-test control | `-Dtest=PlaceHolderTest test -DskipITs`: passed in 1.060 s with no server start |

The class/method timings include Maven and Minecraft startup/shutdown. Resolver unit coverage completed three discovery
checks in 0.012 s in the validation run; treat that as a regression signal, not a general performance guarantee.

# Direct JUnit and IDE tests

After one normal `mvn verify`, run an ordinary JUnit class or method after saving production edits. LightKeeper
packages the current production sources and resources automatically through the project's real Maven lifecycle.
No LightKeeper IDE plugin, IDE before-launch task, manifest VM option or `lightkeeper.ide` flag is required.

## Initial setup

Use Java 21, Maven 3.9+, and Linux/macOS for the supported Unix-domain-socket transport. Add the starter test dependency
and bind both `lock-build` and `prepare-server`, plus Failsafe with `failIfNoTests=true`, as in the
[main README](../README.md#quick-start). No execution ID or explicit phase is needed in a single-module plugin.

1. Run `mvn verify` in the plugin project. For a reactor, use the root command below.
2. Save changes to production Java sources, filtered resources or other Maven packaging inputs.
3. Start a JUnit class/method in the IDE, or use a standard JUnit launcher with the compiled tests and dependencies.

```sh
mvn -pl :my-plugin-e2e -am verify
```

In a reactor, inherit **only the lightweight `lock-build` goal** from the parent. Bind `prepare-server` in the test
module, declare a test-scoped dependency on the producer and select it with `pluginUnderTest.artifactId`. All selected
producer modules must run the guard before modifying production outputs. A missing upstream guard fails with the
producer identity and the required parent configuration. Advanced generators that modify outputs before initialize
must order the guard before those mutations too.

Maven's lifecycle, dependency graph and actual artifact registration remain authoritative. LightKeeper does not zip
IDE class directories, infer JAR names or install your producer. Shade/Assembly replacements and attached artifacts
are selected from the current Maven invocation. A stale installed copy does not replace a selected reactor artifact.

## What runs at launch

A Failsafe test recognizes the immutable export from its actual ancestor Maven JVM by PID and start time. It uses that
invocation's current snapshot without starting another producer build. A leftover metadata file from an earlier build
does not enable this shortcut.

A direct JUnit launch invokes Maven from the retained reactor POM, selects the test module with `-pl` and its upstreams
with `-am`, and runs through `pre-integration-test`. Unit/IT execution is suppressed in that preparation child. Maven
re-evaluates the current POM, performs compilation/filtering/packaging and prepares the selected runtime. The child
holds the selected modules' output leases through packaging, immutable capture and successful Maven exit. JUnit accepts only its own unique export
after a successful build; build failures never fall back to an old runtime.

Preparation runs once per selected module/variant within a JUnit launch. Multiple test classes share immutable inputs,
then copy them into separate mutable class sessions. Separate launches serialize producer preparation and may run
Minecraft concurrently afterwards. Shared server caches also use kernel leases through rebuild, pruning and copying.

Deleted source/resource outputs are refreshed before Maven rebuilds changed production inputs, so a deleted resource
cannot survive silently in `target/classes`. Unchanged inputs retain incremental output. This refresh leaves test
classes in place; packaging still runs for every direct JUnit launch.

## Build context and variants

The module plan retains explicitly requested active/inactive profiles, the Maven executable and Java home, existing
settings/toolchains **paths**, and a documented allowlist of non-secret user properties. Credentials remain in Maven
settings or the environment. Maven reads the current POM, wrapper and `.mvn` configuration on each native build.

Retained properties include `revision`, `changelist`, `sha1`, `maven.repo.local`, compiler release/source/target/encoding
and the supported persistent `lightkeeper.*` preparation inputs. The exact allowlist is
[`MavenBuildContext.RETAINED_PROPERTIES`](../lightkeeper-runtime-core/src/main/java/nl/pim16aap2/lightkeeper/runtime/MavenBuildContext.java).
Arbitrary CLI properties/options, test selectors, transient skip controls and repository authentication are not replayed.
Keep durable custom build inputs in the POM, `.mvn` configuration or an explicitly selected profile. Rerun normal
`mvn verify` to change retained invocation context or regenerate metadata after moving a checkout.

A module with one active preparation needs no execution ID. If several preparations are active, select the intended
variant explicitly with `-Dlightkeeper.executionId=<execution-id>`; ambiguity never selects a sibling arbitrarily.
For independent Paper/Spigot profiles, run initial setup with the desired profile. A later POM change is evaluated by
the preparation child, including a renamed single default execution.

`lightkeeper.buildTimeoutSeconds` controls the producer-build timeout and checkout-lease wait; the default is 300
seconds. Cancellation terminates the owned Maven child tree and waits up to five seconds for exit, then restores
the caller's interrupt status. Repeated interrupts do not abandon that cleanup wait. A watchdog also guards the
child if the test JVM dies.

## Session lifecycle and explicit overrides

Ordinary methods share one server process within their class. `@FreshServer` creates a new process against the same
class session, retaining worlds and plugin data. `framework.server().restart()` retains that data too. Independent
classes/launches start from unchanged prepared inputs, with independent copied JARs, sockets, tokens and runtime ports.
Maven acquires all selected module leases in canonical path order and retains them until its JVM exits. Root and
submodule entry points therefore coordinate the same output files, including with Maven `-T` and several IT modules.
Another cooperating build with overlapping modules can wait through Maven IT execution; this preserves the current
invocation's artifacts for every later snapshot. A direct JUnit preparation child exits before Minecraft starts, so
its runtime session holds no producer locks. Cache leases are released after copying. The current-build bridge
supports the standard Maven CLI/wrapper process, rather than a persistent daemon or embedded multi-invocation JVM.

Successful `.lightkeeper/sessions/` directories are removed after the process stops. Failed sessions remain for
diagnosis; framework diagnostics are written under `target/lightkeeper-reports/`. Native launch snapshots and metadata
are removed when the JUnit launch closes. Normal Maven snapshots survive the invocation's Failsafe forks and are
removed at Maven exit. A hard-killed build can leave diagnostic/generated files behind, but its kernel locks expire
with the owning process and those files never authorize reuse by a later native launch.

`-Dlightkeeper.runtimeManifestPath=/absolute/path/runtime-manifest.json` and `Lightkeeper.start(Path)` remain
authoritative. They bypass discovery and producer builds, retain direct control over the existing server directory,
and support deliberate state reuse. Invalid explicit paths fail immediately. Maven's effective custom test-output
path is supported through an ownership anchor; unrelated custom IDE output paths require an explicit manifest.

## Troubleshooting and migration

| Symptom | Action |
| --- | --- |
| Missing module ownership anchor | Bind `lock-build` and `prepare-server`, then run normal `mvn verify` once. |
| Preparation build failed | Open the reported `.lightkeeper/launches/<id>/maven.log`, fix the producer build and rerun JUnit. |
| Several active preparations | Select `lightkeeper.executionId` or activate one server profile. |
| Direct `mvn -Dtest=... test` runs before packaging | Use standard Failsafe: `mvn "-Dit.test=MyPluginIT#method" verify`, or direct JUnit. |
| Moved checkout or changed retained CLI context | Run normal `mvn verify` from the intended reactor entry point. |

Remove required `lightkeeper.ide` preparation commands and basic Failsafe manifest-property wiring when migrating to
the automatic route. Keep explicit manifests only where their direct-directory/state-reuse contract is intentional.
Ordinary unit tests without the extension or `Lightkeeper.start(Class<?>)` do no LightKeeper work.

The native-launcher path has been exercised with Temurin 21.0.10, Maven 3.9.9, Linux, Paper 1.21.11 and Spigot 1.21.11,
including changed production code, a Shade/Assembly reactor, stale installed artifacts and multiple JUnit classes.
The IntelliJ gutter UI itself was not automated. Debug test/framework code in the IDE; Bukkit/Paper production code
runs in the separate Minecraft JVM and needs server JVM debug arguments.

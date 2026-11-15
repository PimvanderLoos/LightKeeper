# Consumer acceptance evidence

The executable projects in [examples](../examples/README.md) use installed/published Maven artifacts, not SDK
reactor class directories. They are copied outside the LightKeeper checkout before execution. The CI build runs:

```sh
python3 .github/scripts/consumer_checks.py --output "$RUNNER_TEMP/lightkeeper-consumers"
```

Run this after installing the SDK. Set `JAVA_HOME` to JDK 21 and include its `bin` directory in `PATH`.
The runner requires a new output directory and retains separate logs plus `results.json`. It uses secret-free
settings to resolve the locally installed SDK instead of an older remote snapshot. Its isolated-repository case
seeds only installed SDK/Bukkit snapshot artifacts; third-party releases resolve from the warmed Maven cache
as a read-only file repository. No private Maven settings or credentials are copied.

The recorded Linux environment was Temurin **21.0.10**, Maven **3.9.9**, JUnit **6.1.3**, Failsafe **3.6.1** and
Paper/Spigot **1.21.11**. Test expectation/probe properties belong to the fixture harness; none are required
LightKeeper bootstrap settings. Minecraft runs in its own JVM.

| ID | Requirement | Evidence |
| --- | --- | --- |
| A01 | Minimal single-module setup | `single-paper` and `single-spigot`: normal `verify` installs the actual main plugin and executes its command. No custom parent or manifest properties. |
| A02 | Saved production source freshness | Native method launch observes `code-v2` after only editing saved sources. `LK_BUILD` occurs exactly once. |
| A03 | Filtered/deleted resources | Same launch observes `resource-v2` from a changed POM property and confirms removed.txt is absent from the deployed JAR. Output invalidator tests also cover removed classes. |
| A04 | Current reactor wins over stale installation | Runner first installs `stale-installed`, restores v1, runs reactor `verify` without install, then edits core to v2 and observes it through native JUnit. |
| A05 | Actual packaging and explicit selectors | Reactor checks the Assembly replacement JAR and relocated class. Attached-classifier and explicit-path native runs pass; an invalid explicit path fails despite a valid main JAR. |
| A06 | Multiple valid plugin JARs | Core's shaded JAR and packager's Assembly JAR both contain valid metadata; the declared packager selection succeeds. Resolver tests cover ambiguous logical coordinates. |
| A07 | Invalid metadata/archive and server-only | `PluginDescriptorValidatorTest` covers missing, malformed, oversized, duplicate-key and corrupted payload inputs. `PluginUnderTestResolverTest` covers absent artifacts; Mojo tests cover explicit server-only. |
| A08 | Intentional target load failure | Valid descriptor names an unavailable main class; native JUnit reaches a passing `isAbsent()` assertion. No blanket startup-log, main-class, dependency-installation or enabled-state gate. |
| A09 | Live Maven versus later native run | Normal single-module/reactor Failsafe logs contain no `LK_BUILD`. Subsequent direct launches require one fresh producer build. |
| A10 | Method/class/multiple-class selection | Programmatic JUnit uses a fully qualified injected-parameter method selector, multiple classes and four tests with one preparation. Ordinary unit tests provision no server. Failure results are also shared across classes. |
| A11 | Concurrent launches/modules | Runner launches two native processes together, each with its own build result/session. Lease tests cover canonical aliases, sorted module acquisition and rollback. Separate real `-T4` two-test-module and root-versus-submodule probes passed. |
| A12 | Stable active runtime during rebuild | `StableArtifactIT` retains identical private JAR bytes and v2 behavior while another Maven process packages v3. The next direct launch observes v3. Windows deployment was not exercised. |
| A13 | Effective configuration refresh | Changed POM filtering and classifier/path selectors take effect without manual preparation. Separate custom-test-output, renamed-execution and Paper-to-Spigot probes passed. `NativeMavenPreparationTest` covers new explicit/ambiguous variants after model refresh. |
| A14 | Missing outputs and recovery | Runner removes the packaged plugin and prepared server directory before a native launch; real Maven regenerates both. Resolver/metadata tests reject missing, corrupt, foreign or incompatible ownership metadata with an initial-build recovery message. |
| A15 | Session state and restart | `FreshSessionIT` preserves its class's world across fresh server processes and explicit restart; `IndependentSessionIT` starts with clean world state. |
| A16 | Failed/cancelled producers | Real invalid Java fails once for two classes, never starts Minecraft or reuses the old runtime, and the next launch recovers. Unit tests terminate timed-out/interrupted children. A separate SIGKILL-parent probe verified child reaping, released kernel locks and a successful next launch. |
| A17 | Effective skips and empty selection | Runner verifies no provisioning for `skipITs` and `maven.test.skip`, and a clear failure for no matching IT. Skip-policy tests cover runner configuration/expressions and Failsafe 3.5 versus 3.6 semantics. |
| A18 | Explicit and advanced contracts | Resolver/session tests preserve authoritative explicit manifests, direct-directory state reuse and no inferred build. Existing provisioning tests cover Maven/URL/Modrinth/path assets and configured worlds/overlays. |
| A19 | Standalone starter resolution | Fresh alternate local repository compiles the public test API and executes JUnit using starter, coherent engine/API and Bukkit artifact dependencies. Direct-launch classpaths contain Maven artifacts only. |
| A20 | Native IDE route | Ordinary direct JUnit class/method runs exercise the same extension callback. Actual IntelliJ gutter UI interaction was not automated. |
| A21 | Actual AnimatedArchitecture | Its isolated migration worktree passed a clean 23-module reactor build and the enabled-state IT with explicit Vault. A saved production edit then passed direct JUnit after one automatic real Shade/Assembly build, with an in-server source marker. |
| A22 | Optional early Surefire route | Deliberately unsupported before packaging: use Failsafe `verify` or direct JUnit. An unprepared ancestor Maven invocation fails with that recovery instead of recursively rebuilding its outputs. |

The focused Java suites live beside their implementation; the CI job also runs the complete SDK quality pipeline.
The acceptance table distinguishes automated committed checks from additional local process/runtime probes.
It does not claim browser/UI automation or additional supported transports.

## AnimatedArchitecture's build JDK

The current AA checkout requires **Java 25+**, unlike the SDK's Java 21 minimum. Its WorldEdit **7.4.6** dependency
contains class-file version **69**. A JDK **21.0.10** build correctly failed at `hook-plotsquared-6` because that
compiler accepts class-file version **65**. The clean rerun with Temurin **25.0.4**, Maven **3.9.9** passed:

```sh
mvn -pl :animatedarchitecture-lightkeeper-e2e -am \
    -Dit.test=AnimatedArchitecturePluginIT -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.javadoc.skip=true clean verify
```

No AA production code changes were needed. The native-source proof temporarily added a distinctive startup
message after compiling its test assertion, saved only the production edit, and ran the test through the JUnit
launcher. It observed that message in Minecraft with exactly one `LK_BUILD`; temporary proof edits were restored.
Vault is a checksum-pinned fixture for that test, not an inferred dependency or a universal framework requirement.

## Supported boundaries

Initial Maven `verify` and normal IDE import must establish compiled tests, dependencies and module metadata.
Automatic preparation uses saved production inputs and real Maven packaging; it cannot repair an absent JUnit
classpath before discovery. The live bridge supports a standard Maven CLI/wrapper process; persistent daemon
or embedded multi-invocation JVMs are outside its PID/start-time ownership contract. Cooperating builds acquire
selected-module output leases in canonical order and retain them until Maven exits. Consequently another build
can wait through a normal Maven IT run. A native producer exits before Minecraft starts and holds no output
lease during runtime testing. Arbitrary external writers that ignore those leases are outside that guarantee.

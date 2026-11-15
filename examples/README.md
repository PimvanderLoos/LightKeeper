# Executable consumers

These projects are independent of the LightKeeper reactor. Use Java 21 and Maven 3.9+ on Linux/macOS.
Both resolve `1.2.0-SNAPSHOT` from EldoNexus; no custom parent, manifest wiring or IDE plugin is needed.

1. Single-module plugin: `mvn -f examples/single-module/pom.xml verify`.
2. Separate test module: `mvn -f examples/reactor/pom.xml -pl :e2e -am verify`.
3. After that initial build, edit production code and run `SourceFreshnessIT` directly in your IDE.
   The extension runs actual Maven packaging before starting Minecraft. Update the expected command result
   in the test when intentionally changing its behavior.

The starter is a **test dependency** supplying the JUnit/framework API and engine. The Maven plugin binds
`lock-build` before producer writes and `prepare-server` after packaging. A reactor inherits the lock goal
from its parent; only the test module binds preparation and declares/selects its producer dependency.
The examples deliberately use real resource filtering, Shade relocation and an Assembly replacement main JAR.
`FreshSessionIT` demonstrates that process restarts preserve class-owned world data; another class starts clean.

Run the automated source-freshness regressions against locally installed SDK artifacts:

```sh
python3 .github/scripts/consumer_checks.py --output /tmp/lightkeeper-consumer-checks
```

The runner copies these sources outside the checkout, performs initial Maven builds, edits saved production
sources/POM/resources and launches JUnit without bootstrap VM options. Optional `expected.*` properties only
change assertions in the test harness. It verifies Paper and Spigot, a stale installed reactor artifact,
method/class selection, one build per launch, failed-build deduplication, concurrent launches, rebuilding while
a prior session still runs, and intentional target-plugin load failures.
It retains logs/reports in the output directory. Generated runtime metadata contains authentication material;
do not publish `.lightkeeper` or runtime manifests.

The CI workflow runs the same runner after installing the SDK. An isolated-repository test resolves the starter
and compiles tests using only installed Maven artifacts, with no LightKeeper reactor class directories.
Actual IntelliJ UI interaction and Windows UDS execution remain manual/unverified; direct JUnit uses the same
extension callback as an ordinary native gutter run. Maven `test` is too early for these packaged-plugin ITs;
use Failsafe `verify` in the terminal.

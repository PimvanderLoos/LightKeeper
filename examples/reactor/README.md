# A separate integration-test module

This independent reactor packages the plugin with Shade in `core`, replaces the main artifact with Assembly in
`packager`, and tests that final JAR from `e2e`. It models projects such as AnimatedArchitecture without requiring
their production dependencies.

Run from the LightKeeper checkout with Java 21 and Maven 3.9+:

```sh
mvn -f examples/reactor/pom.xml -pl :e2e -am verify
```

The parent inherits only the lightweight `lock-build` goal so upstream producer outputs are protected before
compilation. Only `e2e` binds `prepare-server` and Failsafe. Its test-scoped dependency on `packager` gives Maven
the required reactor order; `pluginUnderTest.artifactId` selects that dependency's real Assembly artifact.

Copy that shape into your reactor, retaining its production dependencies and packaging. Use your producer's
artifact ID in both the dependency and selector. No prior `mvn install`, explicit JAR filename, manifest VM option
or IDE plugin is required. Keep the Failsafe binding in the test module so unrelated modules do not fail because
they contain no ITs.

After the initial build, edit saved production sources and run `SourceFreshnessIT` as ordinary JUnit. The extension
packages the selected module and its upstreams before starting Minecraft. The [standalone example](../single-module)
also contains the fully copyable `GettingStartedIT` for a first world interaction.

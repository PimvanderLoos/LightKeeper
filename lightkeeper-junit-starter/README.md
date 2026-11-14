# LightKeeper JUnit Starter

Use `lightkeeper-junit-starter` as a **test dependency** to write JUnit integration tests against a real
Minecraft server. It supplies the test libraries needed to use LightKeeper without assembling that stack yourself.

## Starter or Maven plugin?

For the standard Maven workflow, use **both**. They do different jobs:

| Module | Declare it as | Responsibility |
| --- | --- | --- |
| `lightkeeper-junit-starter` | A dependency with `test` scope | Supplies the framework API, JUnit and assertions. Your tests use `LightkeeperExtension` to start/stop the server and interact with it. |
| `lightkeeper-maven-plugin` | A plugin under `build/plugins` | Prepares server inputs: resolves the server, installs the LK agent and selected plugin, applies fixtures, and writes runtime metadata. |

Adding a test dependency does not bind Maven goals. Adding a build plugin does not put the LightKeeper API or
JUnit engine on your test classpath. Maven runs your existing plugin packaging steps before `prepare-server` uses
the resulting artifact.

## What the starter includes

- The `lightkeeper-framework-junit` API and JUnit extension.
- Jupiter API, parameterized tests, engine and Platform launcher at matching default versions.
- AssertJ for assertions.
- Bukkit API types referenced by the public test API.

Declare the starter with `test` scope so these libraries stay out of your production plugin's runtime dependencies.
Keep your existing production API dependencies and packaging configuration.

## Add it to an existing Maven plugin project

Use the same `lightkeeper.version` for the starter and Maven plugin. The current development version is
`1.2.0-SNAPSHOT`; repository declarations and the complete Failsafe setup are in the
[consumer setup guide](../README.md#quick-start).

```xml
<dependency>
    <groupId>nl.pim16aap2.lightkeeper</groupId>
    <artifactId>lightkeeper-junit-starter</artifactId>
    <version>${lightkeeper.version}</version>
    <scope>test</scope>
</dependency>
```

Bind server preparation in that module:

```xml
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
```

Write `*IT` tests using `@ExtendWith(LightkeeperExtension.class)` and configure Failsafe as shown in the guide.
The starter alone does not activate the extension for unrelated unit tests. Run the Maven integration-test
workflow with `mvn verify`; see the [IDE guide](../docs/ide-testing.md) for direct JUnit launches.

## When to use the framework dependency directly

Use `lightkeeper-framework-junit` instead when you already manage JUnit, AssertJ and Bukkit test dependencies
yourself. It exposes the same test API; the starter adds a convenient dependency combination.
Explicitly prepared runtime manifests and `Lightkeeper.start(Path)` remain available for advanced setups.

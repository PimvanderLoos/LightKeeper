/**
 * Internal framework runtime components for process lifecycle management, agent RPC, and handle wiring.
 * Native JUnit preparation invokes the consumer's real Maven lifecycle and owns immutable inputs until class
 * sessions have copied them. Live Failsafe executions consume their current Maven export without a nested build.
 * Bounded Maven-log feedback exposes actual build phases while preparation runs. Build, server-lifecycle and
 * per-test failures share a redacted, escaped diagnostic overview linking their original retained logs.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.framework.internal;

/**
 * Server preparation, read-only setup diagnosis and focused Maven collaborators.
 * <p>
 * Run {@code mvn lightkeeper:doctor} to inspect effective setup without building or starting a server.
 * This package resolves server/runtime inputs, prepares server assets, and writes the runtime manifest consumed by
 * integration test execution. The default target is the current module's actual packaged main artifact;
 * {@code pluginUnderTest} selects a declared producer dependency, classifier or explicit file instead.
 * Module-local fixture conventions and effective Failsafe skips keep ordinary setup concise while preserving
 * explicit asset sources and intentional server-only or plugin-load-failure tests.
 */
package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

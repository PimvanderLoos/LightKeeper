/**
 * Early lifecycle coordination protecting real Maven producer artifacts through immutable snapshot capture.
 * The lightweight initialize guard can be inherited throughout a consumer reactor; server preparation remains
 * local to test modules. Each Maven process owns its lease and releases it before Failsafe begins.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

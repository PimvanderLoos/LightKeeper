/**
 * Early lifecycle coordination protecting real Maven producer artifacts through immutable snapshot capture.
 * The lightweight initialize guard can be inherited throughout a consumer reactor; server preparation remains
 * local to test modules, and leases are released before Failsafe or borrowed by a verified JUnit bootstrap child.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

/**
 * Early lifecycle coordination protecting real Maven producer artifacts through immutable snapshot capture.
 * The lightweight initialize guard can be inherited throughout a consumer reactor; server preparation remains
 * local to test modules. Acquire selected module leases in canonical path order and retain them until Maven exits,
 * so root/module entry points, parallel builds and later snapshots observe coherent producer artifacts.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

/**
 * Early lifecycle coordination protecting real Maven producer artifacts through immutable snapshot capture.
 * The lightweight initialize guard can be inherited throughout a consumer reactor; server preparation remains
 * local to test modules. Serial Maven releases ownership before Failsafe; parallel reactors retain it until their
 * JVM exits so one module's snapshot cannot expose another module's still-changing outputs to a competing build.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

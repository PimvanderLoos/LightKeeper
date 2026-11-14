/**
 * Shared server preparation and isolated session inputs used by Maven provisioning and direct JUnit launches.
 * Download metadata and server specifications describe prepared inputs independently of Maven's project model.
 * Maven owns dependency selection; the framework owns each mutable test session and server process.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.provisioning;

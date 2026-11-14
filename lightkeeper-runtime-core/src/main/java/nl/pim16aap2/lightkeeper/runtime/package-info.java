/**
 * Runtime manifests, versioned test plans and process coordination shared across Maven, JUnit and the in-server agent.
 * {@link nl.pim16aap2.lightkeeper.runtime.ModuleTestPlan} records module ownership and supported Maven build context;
 * {@link nl.pim16aap2.lightkeeper.runtime.MavenRuntimeContext} ties a variant's immutable inputs to its real producer.
 * Build leases protect packaging and capture, while runtime manifests describe independently mutable server sessions.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.runtime;

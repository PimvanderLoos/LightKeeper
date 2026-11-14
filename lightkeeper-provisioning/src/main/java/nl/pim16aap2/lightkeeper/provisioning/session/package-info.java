/**
 * Immutable prepared server snapshots and independently owned mutable runtime sessions.
 * Maven captures stopped prepared inputs with {@link nl.pim16aap2.lightkeeper.provisioning.session.ServerSnapshots};
 * each JUnit class or standalone framework creates its own session, socket and handshake token from that snapshot.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.provisioning.session;

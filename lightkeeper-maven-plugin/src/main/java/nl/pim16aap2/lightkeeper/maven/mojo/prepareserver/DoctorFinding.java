package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

/**
 * One setup observation with a concrete recovery action when needed.
 *
 * @param level
 *     Severity; warnings describe conditions that do not prevent initial setup.
 * @param subject
 *     Human-readable checked input.
 * @param detail
 *     Observation without dumping the Maven model or credentials.
 * @param action
 *     Recovery instruction, or an empty string when none is needed.
 */
record DoctorFinding(Level level, String subject, String detail, String action)
{
    /** Readiness outcomes rendered by the doctor command. */
    enum Level
    {
        /** Input is ready. */
        OK,
        /** An initial build or explicit user choice is still needed. */
        WARN,
        /** Configuration prevents the supported test route. */
        ERROR
    }
}

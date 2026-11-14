package nl.pim16aap2.lightkeeper.provisioning;

/** Runtime failure while resolving, validating or materializing prepared server inputs. */
public final class ProvisioningException extends IllegalStateException
{
    /** Serialization format version for the exception's inherited contract. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an actionable preparation failure.
     *
     * @param message
     *     Operation and input context describing the failure.
     */
    public ProvisioningException(String message)
    {
        super(message);
    }

    /**
     * Creates a preparation failure retaining its underlying cause.
     *
     * @param message
     *     Operation and input context describing the failure.
     * @param cause
     *     Original failure.
     */
    public ProvisioningException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

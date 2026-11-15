package example.internal;

/** Revision provider relocated by Shade; editing this source must change the next native test's behavior. */
public final class Revision
{
    private Revision()
    {
    }

    /** Returns the saved production revision. */
    public static String value()
    {
        return "v1";
    }
}

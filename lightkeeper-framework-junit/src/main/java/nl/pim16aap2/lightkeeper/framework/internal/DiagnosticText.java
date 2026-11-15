package nl.pim16aap2.lightkeeper.framework.internal;

import java.util.regex.Pattern;

/** Shared escaping and credential redaction for copied diagnostics and forwarded build feedback. */
final class DiagnosticText
{
    /** Strip terminal escape sequences before interpreting or showing process output. */
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    /** URL user information must never be repeated in a generated overview. */
    private static final Pattern USER_INFO = Pattern.compile("(?i)(https?://)[^\\s/@]+@");
    /** Common property, header and query-string secret values. */
    private static final Pattern SECRET = Pattern.compile(
        "(?i)((?:password|token|secret|authorization|api[-_.]?key)[\"']?\\s*[=:]\\s*)"
            + "(?:\"[^\"]*\"|'[^']*'|[^\\s<]+)"
    );
    /** Authentication header values can contain a second whitespace-delimited token. */
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[^\\s<]+");

    private DiagnosticText()
    {
    }

    static String redact(String value)
    {
        final String plain = ANSI.matcher(value).replaceAll("");
        final String urls = USER_INFO.matcher(plain).replaceAll("$1[redacted]@");
        return SECRET.matcher(BEARER.matcher(urls).replaceAll("$1[redacted]")).replaceAll("$1[redacted]");
    }

    static String html(String value)
    {
        return redact(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

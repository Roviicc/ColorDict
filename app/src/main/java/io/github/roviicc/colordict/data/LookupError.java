package io.github.roviicc.colordict.data;

/** Why a lookup or connotation check failed, and what the user can do about it. */
public final class LookupError {

    /** The action the screen offers next to the message. */
    public enum Fix {
        /** Show "Try again". */
        RETRY,
        /** Show "Open Settings" (missing or rejected API key, unavailable model). */
        OPEN_SETTINGS,
        /** Nothing the user can do from here. */
        NONE
    }

    /** User-facing message. */
    public final String message;
    public final Fix fix;

    public LookupError(String message, Fix fix) {
        this.message = message;
        this.fix = fix;
    }

    /** Checked exception carrying a {@link LookupError}, for blocking code. */
    public static final class Exception extends java.lang.Exception {
        public final LookupError error;

        public Exception(String message, Fix fix) {
            super(message);
            this.error = new LookupError(message, fix);
        }
    }
}

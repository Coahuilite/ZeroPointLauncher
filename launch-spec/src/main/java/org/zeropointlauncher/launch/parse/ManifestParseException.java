package org.zeropointlauncher.launch.parse;

public final class ManifestParseException extends RuntimeException {
    public ManifestParseException(String message) {
        super(message);
    }

    public ManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

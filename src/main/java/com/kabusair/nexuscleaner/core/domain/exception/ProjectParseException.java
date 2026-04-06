package com.kabusair.nexuscleaner.core.domain.exception;

/** Thrown when a {@code pom.xml} or {@code build.gradle} cannot be parsed. */
public class ProjectParseException extends NexusCleanerException {

    public ProjectParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectParseException(String message) {
        super(message);
    }
}

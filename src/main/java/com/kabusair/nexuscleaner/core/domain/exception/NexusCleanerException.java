package com.kabusair.nexuscleaner.core.domain.exception;

/** Base runtime exception for all NexusCleaner domain errors. */
public class NexusCleanerException extends RuntimeException {

    public NexusCleanerException(String message) {
        super(message);
    }

    public NexusCleanerException(String message, Throwable cause) {
        super(message, cause);
    }
}

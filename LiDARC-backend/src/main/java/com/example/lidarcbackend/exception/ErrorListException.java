package com.example.lidarcbackend.exception;

import java.util.Collections;
import java.util.List;

public abstract class ErrorListException extends Exception {
    private final List<String> errors;
    private final String messageSummary;
    private final String errorListDescriptor;

    /**
     * Constructs a new {@code ErrorListException} with a list of validation errors.
     *
     * @param errorListDescriptor a descriptor providing context for the error list
     * @param messageSummary      a brief summary message describing the issue
     * @param errors              a list of specific error messages
     */
    public ErrorListException(String errorListDescriptor, String messageSummary, List<String> errors) {
        super(messageSummary);
        this.errorListDescriptor = errorListDescriptor;
        this.messageSummary = messageSummary;
        this.errors = errors;
    }

    /**
     * Returns a formatted error message combining the summary, descriptor, and individual errors.
     *
     * <p>See {@link Throwable#getMessage()} for general information about this method.</p>
     *
     * <p>Note: this implementation constructs the message using
     * {@link #messageSummary}, {@link #errorListDescriptor}, and {@link #errors}.</p>
     */
    @Override
    public String getMessage() {
        return "%s. %s: %s."
                .formatted(messageSummary, errorListDescriptor, String.join(", ", errors));
    }

    /**
     * Retrieves the summary of the error.
     *
     * @return a brief message summarizing the validation issue
     */
    public String summary() {
        return messageSummary;
    }

    /**
     * Retrieves an unmodifiable list of validation errors.
     *
     * @return a list of specific error messages
     */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }
}

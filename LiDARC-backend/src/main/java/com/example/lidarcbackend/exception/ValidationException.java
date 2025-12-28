package com.example.lidarcbackend.exception;

import java.util.List;

public class ValidationException extends ErrorListException {
    /**
     * Constructs a ValidationException with a summary message and a list of detailed validation errors.
     *
     * @param messageSummary a brief summary of the validation context
     * @param errors         the list of specific validation error messages
     */
    public ValidationException(String messageSummary, List<String> errors) {
        super("Failed validations", messageSummary, errors);
    }
}

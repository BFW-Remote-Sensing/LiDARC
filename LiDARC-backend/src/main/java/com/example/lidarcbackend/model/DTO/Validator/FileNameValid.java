package com.example.lidarcbackend.model.DTO.Validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileNameValidator.class)
public @interface FileNameValid {
  String message() default "Invalid file name format.";
  Class<?>[] groups() default {};
  Class<?extends Payload>[] payload() default {};

}

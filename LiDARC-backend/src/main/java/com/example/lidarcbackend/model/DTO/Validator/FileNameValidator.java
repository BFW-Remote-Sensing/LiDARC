package com.example.lidarcbackend.model.DTO.Validator;

import io.minio.org.apache.commons.validator.routines.RegexValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Annotation;


public class FileNameValidator implements ConstraintValidator<FileNameValid, String>, Annotation {

  @Override
  public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
    String pattern = "^[a-zA-Z0-9._-]+$"; // Example pattern: only allows alphanumeric characters, dots, underscores, and hyphens
    RegexValidator regexValidator = new RegexValidator(pattern);
    return  regexValidator.isValid(s);
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return FileNameValid.class;
  }
}

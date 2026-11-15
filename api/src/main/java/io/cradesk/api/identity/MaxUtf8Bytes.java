package io.cradesk.api.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {

    // Máximo de bytes permitidos en UTF-8.
    int value();

    // Mensaje de error de la validación.
    String message() default "must be at most {value} bytes in UTF-8";

    // Grupos de validación (requerido por Bean Validation).
    Class<?>[] groups() default {};

    // Datos extra de la validación (requerido por Bean Validation).
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

        private int max;

        // Guarda el máximo de bytes indicado en la anotación.
        @Override
        public void initialize(MaxUtf8Bytes annotation) {
            max = annotation.value();
        }

        // Acepta el texto si es null o si ocupa como máximo el límite de bytes en UTF-8.
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}

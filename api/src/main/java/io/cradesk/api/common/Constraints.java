package io.cradesk.api.common;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class Constraints {

    // Constructor privado para que esta clase de utilidades no se instancie.
    private Constraints() {
    }

    // Indica si el error vino de la restricción de base de datos con ese nombre.
    public static boolean isViolated(DataIntegrityViolationException e, String constraintName) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}

package io.cradesk.api.common;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Helps tell database integrity errors apart. Spring wraps many different
 * failures (unique, foreign key, NOT NULL, bad data) in the same
 * {@link DataIntegrityViolationException}; only a violation of one specific,
 * named unique constraint should become a "duplicate" 409.
 */
public final class Constraints {

    private Constraints() {
    }

    /** True if {@code e} was caused by the constraint (or unique index) named {@code constraintName}. */
    public static boolean isViolated(DataIntegrityViolationException e, String constraintName) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}

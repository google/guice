package com.google.inject;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Indicates a member that accepts injection of null values.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface Nullable {
}

package com.google.inject.persist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p> Any method or class marked with this annotation will require the existence
 * of a unit of work.
 * <p>Marking a method {@code @RequiresUnitOfWork} will start a unit of work if none
 * exists before the method executes and end it if it was started after the method returns.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RequiresUnitOfWork {
}

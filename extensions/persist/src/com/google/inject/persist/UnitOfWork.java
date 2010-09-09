package com.google.inject.persist;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The Default unit of work binding annotation used to bind persistence
 * artifacts to. You may also place this annotation on methods to intercept
 * and wrap them with a unit of work (i.e. start and end work around methods).
 * This is analogous to starting an ending transactions but at a coarser
 * granularity. For example, in JPA a unit of work would span a single
 * {@code EntityManager}'s lifespan. 
 */
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
public @interface UnitOfWork {
}

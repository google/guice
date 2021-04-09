package com.google.inject.testing.fieldbinder;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.RestrictedBindingSource;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Test annotation used to test restricted binding source feature in Kotlin. */
@RestrictedBindingSource.Permit
@Retention(RUNTIME)
@Target({TYPE, TYPE_USE})
public @interface TestPermit {}

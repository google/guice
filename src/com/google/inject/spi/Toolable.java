package com.google.inject.spi;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Stage;


/**
 * Instructs an {@link Injector} running in {@link Stage#TOOL} that
 * a method should be injected.  This is typically useful for for
 * extensions to Guice that perform additional validation in an 
 * {@literal @}{@link Inject}ed method.  This only applies to objects
 * that are already constructed when bindings are created (ie., 
 * something bound using toProvider, toInstance, or requestInjection).
 * 
 * @author sberlin@gmail.com (Sam Berlin)
 */
@Target({ METHOD })
@Retention(RUNTIME)
@Documented
public @interface Toolable {
}

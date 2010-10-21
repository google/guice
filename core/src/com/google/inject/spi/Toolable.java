package com.google.inject.spi;

import com.google.inject.Injector;
import com.google.inject.Stage;
import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;


/**
 * Instructs an {@link Injector} running in {@link Stage#TOOL} that a method should be injected.
 * This is typically useful for for extensions to Guice that perform additional validation in an
 * injected method or field.  This only applies to objects that are already constructed when
 * bindings are created (ie., something bound using {@link
 * com.google.inject.binder.LinkedBindingBuilder#toProvider toProvider}, {@link
 * com.google.inject.binder.LinkedBindingBuilder#toInstance toInstance}, or {@link
 * com.google.inject.Binder#requestInjection requestInjection}.
 * 
 * @author sberlin@gmail.com (Sam Berlin)
 * @since 3.0
 */
@Target({ METHOD })
@Retention(RUNTIME)
@Documented
public @interface Toolable {
}

package com.google.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Apply this to implementation classes when you want only one instance (per {@link Injector}) to be
 * reused for all injections for that binding.
 *
 * @author 11712617@mail.sustech.edu.cn (Xinhao Xiang)
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RUNTIME)
@ScopeAnnotation
public @interface EagerSingleton {
}
package com.google.inject;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation restricting the binding of the target type to permitted modules.
 *
 * <p>If a binding's type or qualifier annotation type is annotated with
 * {@code @RestrictedBindingSource}, then only modules annotated with a permit from {@link #permits}
 * are allowed to create it (note that if both the type and qualifier annotation type are restricted
 * this way, both restrictions are enforced) -- otherwise, an error message including the {@link
 * #explanation} is issued.
 *
 * <p>This allows libraries to prevent their clients from binding their keys, similar to how
 * declaring a class final prevents subtyping. For example, a library may want to prevent users from
 * creating mock bindings for tests, using the {@link #explanation} - included in the error message
 * - to point them to a supported testing module.
 *
 * <p>TODO(user): Concrete Example!
 *
 * <p>Note that a binding is created by a stack of modules, where the module at the bottom of the
 * stack creates it directly, and its ancestor modules up the stack create it indirectly. A binding
 * restricted this way can be created if one of the modules on the stack is annotated with one of
 * the annotations in {@link #permits}.
 *
 * <p><b>Warning:</b> This is an experimental API, currently in developement.
 *
 * @author vzm@google.com (Vladimir Makaric)
 */
@Inherited
@Retention(RUNTIME)
@Target(TYPE)
public @interface RestrictedBindingSource {
  /**
   * Explanation of why binding this target type is restricted.
   *
   * <p>Will appear as the error message if the target type is bound by non-allowed modules.
   */
  String explanation();

  /**
   * Meta-annotation indicating that the target annotation is a permit for binding restricted
   * bindings. Annotating a module with a permit gives the module permission to bind the restricted
   * bindings guarded by the permit (see {@link #permits}).
   */
  @Documented
  @Retention(RUNTIME)
  @Target(ANNOTATION_TYPE)
  public @interface Permit {}

  /**
   * List of {@code Permit} annotations (must be non-empty), one of which has has to be present on a
   * restricted binding's module stack.
   */
  Class<? extends Annotation>[] permits();

  /** Level of restriction. Determines how violations are handled. */
  public static enum RestrictionLevel {
    WARNING,
    ERROR;
  }

  RestrictionLevel restrictionLevel() default RestrictionLevel.ERROR;

  // TODO(user): Add exemption mechanism similar to RestrictedApi's allowedOnPath.
}

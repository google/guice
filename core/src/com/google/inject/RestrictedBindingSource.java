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
 * Annotation restricting the binding of the target type to permitted sources.
 *
 * <p>Bindings restricted by this annotation may only be created by sources annotated with a permit
 * from {@link #permits} -- otherwise, an error message including the {@link #explanation} is
 * issued.
 *
 * <p>There are two kinds of binding source:
 *
 * <ol>
 *   <li>Module: a module is the source of a binding if it creates it (either directly, or
 *       indirectly by installing another module). For example: if module A creates restricted
 *       binding X, and module C installs module B that installs A; then all 3 modules C,B,A are
 *       sources of X, and it's enough for any one of them to be annotated with a permit from X's
 *       restriction.
 *   <li>Method Scanner ({@code ModuleAnnotatedMethodScanner}): If a binding was created by a
 *       scanner, then that scanner is also a source of the binding (in addition to the module
 *       sources) and a permit may be given to the scanner by annotating its class.
 * </ol>
 *
 * <p>Bindings with qualifier annotations are restricted solely by the annotation on their qualifier
 * (restrictions on the type are ignored for qualified bindings). Unqualified bindings are
 * restricted by the annotation on their type.
 *
 * <p>This allows libraries to prevent their clients from binding their keys, similar to how
 * declaring a class final prevents subtyping. For example, a library may want to prevent users from
 * creating mock bindings for tests, using the {@link #explanation} - included in the error message
 * - to point them to a supported testing module.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @RestrictedBindingSource.Permit
 * @Retention(RetentionPolicy.RUNTIME)
 * @interface NetworkPermit {}
 *
 * @RestrictedBindingSource(
 *   explanation = "Only NetworkModule can create network bindings.",
 *   permits = {NetworkPermit.class})
 * @Qualifier
 * @Retention(RetentionPolicy.RUNTIME)
 * public @interface GatewayIpAdress {}
 *
 * @NetworkPermit
 * public final class NetworkModule extends AbstractModule {
 *   @Provides
 *   @GatewayIpAdress // Allowed because the module is annotated with @NetworkPermit.
 *   int provideGatewayIp() { ... }
 * }
 * }</pre>
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
   * bindings. Annotating a binding source (defined in top-level javadoc) with a permit gives it
   * permission to bind the restricted bindings guarded by the permit (see {@link #permits}).
   */
  @Documented
  @Retention(RUNTIME)
  @Target(ANNOTATION_TYPE)
  public @interface Permit {}

  /**
   * List of {@code Permit} annotations (must be non-empty), one of which has has to be present on a
   * restricted binding's source (defined in top-level javadoc).
   */
  Class<? extends Annotation>[] permits();

  /**
   * Exempt modules whose fully qualified class names match this regex.
   *
   * <p>If any module on the binding's module stack matches this regex, the binding is allowed (no
   * permit necessary). No module is exempt by default (empty string).
   *
   * <p>Inteded to be used when retrofitting a binding with this restriction. When restricting an
   * existing binding, it's often practical to first restrict with exemptions for existing
   * violations (to prevent new violations), before updating the code in violation to use the
   * permitted module(s).
   */
  String exemptModules() default "";

  /** Level of restriction. Determines how violations are handled. */
  public static enum RestrictionLevel {
    WARNING,
    ERROR;
  }

  RestrictionLevel restrictionLevel() default RestrictionLevel.ERROR;
}

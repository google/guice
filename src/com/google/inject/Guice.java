package com.google.inject;

import java.util.Arrays;

/**
 * The entry point to the Guice framework. Creates {@link Injector}s from
 * {@link Module}s.
 */
public final class Guice {

  private Guice() {}

  /**
   * Creates an injector for the given set of modules.
   */
  public static Injector createInjector(Module... modules)
      throws CreationException {
    return createInjector(Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules.
   */
  public static Injector createInjector(Iterable<Module> modules)
      throws CreationException {
    return createInjector(Stage.DEVELOPMENT, modules);
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage.
   */
  public static Injector createInjector(Stage stage, Module... modules)
      throws CreationException {
    return createInjector(stage, Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage.
   */
  public static Injector createInjector(Stage stage, Iterable<Module> modules)
      throws CreationException {
    BinderImpl binder = new BinderImpl(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    return binder.createInjector();
  }
}

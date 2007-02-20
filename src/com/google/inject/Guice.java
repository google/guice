package com.google.inject;

import java.util.Arrays;

/**
 * The entry point to the Guice framework. Creates {@link Container}s from
 * {@link Module}s.
 */
public final class Guice {

  private Guice() {}

  /**
   * Creates a {@code Container} from {@code Module}s.
   */
  public static Container createContainer(Module... modules)
      throws CreationException {
    return createContainer(Arrays.asList(modules));
  }

  /**
   * Creates a {@code Container} from {@code Module}s.
   */
  public static Container createContainer(Iterable<Module> modules)
      throws CreationException {
    return createContainer(Stage.DEVELOPMENT, modules);
  }

  /**
   * Creates a {@code Container} from {@code Module}s in a given development
   * {@link Stage}.
   */
  public static Container createContainer(Stage stage, Module... modules)
      throws CreationException {
    return createContainer(stage, Arrays.asList(modules));
  }

  /**
   * Creates a {@code Container} from {@code Module}s in a given development
   * {@link Stage}.
   */
  public static Container createContainer(Stage stage, Iterable<Module> modules)
      throws CreationException {
    BinderImpl binder = new BinderImpl(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    return binder.createContainer();
  }
}

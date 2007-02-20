package com.google.inject;

import java.util.Arrays;

/**
 * TODO
 */
public final class Guice {

  public static Container createContainer(Module... modules)
      throws CreationException {
    return createContainer(Arrays.asList(modules));
  }

  public static Container createContainer(Iterable<Module> modules)
      throws CreationException {
    return createContainer(Stage.DEVELOPMENT, modules);
  }

  public static Container createContainer(Stage stage, Module... modules)
      throws CreationException {
    return createContainer(stage, Arrays.asList(modules));
  }

  public static Container createContainer(Stage stage, Iterable<Module> modules)
      throws CreationException {
    BinderImpl binder = new BinderImpl(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    return binder.createContainer();

  }

  private Guice() {}
}

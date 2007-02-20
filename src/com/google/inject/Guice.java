package com.google.inject;

import java.util.Arrays;

/**
 * Javadoc.
 *
 * @author Kevin Bourrillion (kevinb9n@gmail.com)
 */
public class Guice {

  public static Container newContainer(Module... modules)
      throws CreationException {
    return newContainer(Arrays.asList(modules));
  }

  public static Container newContainer(Iterable<Module> modules)
      throws CreationException {
    BinderImpl binder = new BinderImpl();
    for (Module module : modules) {
      binder.install(module);
    }
    return binder.createContainer();
  }
}

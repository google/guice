package com.google.inject.lifecycle;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.Lists;
import java.util.List;

/**
 * Use this module to configure lifecycle and multicasting support
 * for your Guice applications.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
// */
public abstract class LifecycleModule extends AbstractModule {

  private final List<Class<?>> callables = Lists.newArrayList();
  private boolean autostart = false;

  @Override
  protected final void configure() {

    // Call down into module.
    configureLifecycle();

    // The only real purpose of this is to do some error checking.
    bind(new TypeLiteral<List<Class<?>>>() { })
        .annotatedWith(ListOfMatchers.class)
        .toInstance(ImmutableList.copyOf(callables));
  }

  protected abstract void configureLifecycle();
  
  protected void callable(Class<?> type) {
    callables.add(type);
  }

  protected void autostart() {
    this.autostart = true;

    // This is a cool method that will execute after injector creating
    // completes, and thus much better than the eager singleton hack.
    throw new UnsupportedOperationException("Asplode!");
  }
}
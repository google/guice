package com.google.inject.lifecycle;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Maps;
import com.google.inject.internal.Preconditions;
import com.google.inject.matcher.Matcher;
import static com.google.inject.matcher.Matchers.any;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
class BroadcastingLifecycle implements Lifecycle {
  private final Injector injector;
  private final List<Class<?>> callableClasses;

  // @GuardedBy(this)
  private Map<Class<?>, List<Key<?>>> callableKeys;

  private volatile boolean started = false;

  @Inject
  public BroadcastingLifecycle(Injector injector, @ListOfMatchers List<Class<?>> callableClasses) {
    this.injector = injector;
    this.callableClasses = callableClasses;

    // Self start. Eventually we may want to move this to a hook-in from Guice-core.
    start();
  }

  public void start() {
    if (started) {
      // throw? log warning?
      return;
    }

    // OK to start the startables now.
    // Guaranteed to return in order of module binding..
    Map<Key<?>, Binding<?>> allBindings = injector.getBindings();

    List<Binding<Startable>> startables = Lists.newArrayList();
    Map<Class<?>, List<Key<?>>> callableKeys = Maps.newLinkedHashMap();

    // Do not collapse into loop below (in synchronized block). Time complexity is still linear.
    for (Binding<?> binding : allBindings.values()) {

      Class<?> bindingType = binding.getKey().getTypeLiteral().getRawType();

      // inner loop N*M complexity
      for (Class<?> callable : callableClasses) {
        if (callable.isAssignableFrom(bindingType)) {

          // we don't want to instantiate these right now...
          List<Key<?>> list = callableKeys.get(callable);

          // Multimap put.
          if (null == list) {
            list = Lists.newArrayList();
            callableKeys.put(callable, list);
          }

          list.add(binding.getKey());
        }
      }

      // check startables now.
      if (Startable.class.isAssignableFrom(bindingType)) {

        // First make sure this is a singleton.
        Preconditions.checkState(Scopes.isSingleton(binding),
            "Egregious error, all Startables must be scopes as singletons!");

        //noinspection unchecked
        startables.add((Binding<Startable>) binding);
      }
    }

    synchronized (this) {
      for (Binding<Startable> binding : startables) {

        // Go go zilla go! (sequential startup)
        injector.getInstance(binding.getKey()).start();
      }

      // Safely publish keymap.
      this.callableKeys = callableKeys;

      // success!
      started = true;
    }
  }

  public <T> T broadcast(Class<T> clazz) {
    return broadcast(clazz, any());
  }

  public <T> T broadcast(Class<T> clazz, Matcher<? super T> matcher) {
    final List<T> ts = instantiateForBroadcast(clazz, matcher);

    @SuppressWarnings("unchecked") T caster = (T) Proxy
        .newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, new InvocationHandler() {
          public Object invoke(Object o, Method method, Object[] objects) throws Throwable {

            // propagate the method call with the same arg list to all instances.
            for (T t : ts) {
              method.invoke(t, objects);
            }

            // We can't return from multiple instances, so just return null.
            return null;
          }
        });

    return caster;
  }

  private <T> List<T> instantiateForBroadcast(Class<T> clazz, Matcher<? super T> matcher) {
    final List<T> ts = Lists.newArrayList();
    for (Key<?> key : callableKeys.get(clazz)) {
      // Should this get instancing happen during method call?
      @SuppressWarnings("unchecked") // Guarded by getInstance
          T t = (T) injector.getInstance(key);

      if (matcher.matches(t)) {
        ts.add(t);
      }
    }
    return ts;
  }

  public <T> T broadcast(Class<T> clazz, final ExecutorService executorService) {
    return broadcast(clazz, executorService, any());
  }

  public <T> T broadcast(Class<T> clazz, final ExecutorService executorService,
      Matcher<? super T> matcher) {
    final List<T> ts = instantiateForBroadcast(clazz, matcher);

    @SuppressWarnings("unchecked") T caster = (T) Proxy
        .newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, new InvocationHandler() {
          public Object invoke(Object o, final Method method, final Object[] objects)
              throws Throwable {

            // propagate the method call with the same arg list to all instances.
            for (final T t : ts) {
              // Submit via executor service. TODO See if this can be parallelized by
              // yet another dimension, i.e. inParallel(N)
              executorService.submit(new Callable() {
                public Object call() throws Exception {
                  return method.invoke(t, objects);
                }
              });
            }

            // We can't return from multiple instances, so just return null.
            return null;
          }
        });

    return caster;
  }
}

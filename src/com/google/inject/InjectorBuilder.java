package com.google.inject;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import com.google.inject.internal.InternalInjectorCreator;

/**
 * The advanced entry point to the Guice framework. Creates {@link Injector}s from
 * {@link Module}s, allowing many options to be configured for the Injector.
 *
 * <p>Guice supports a model of development that draws clear boundaries between
 * APIs, Implementations of these APIs, Modules which configure these
 * implementations, and finally Applications which consist of a collection of
 * Modules. It is the Application, which typically defines your {@code main()}
 * method, that bootstraps the Guice Injector using the {@code Guice} class, as
 * in this example:
 * <pre>
 *     public class FooApplication {
 *       public static void main(String[] args) {
 *         Injector injector = new InjectorBuilder().
 *             .stage(Stage.PRODUCTION)
 *             . . . 
 *             .addModules(
 *                new ModuleA(),
 *                new ModuleB(),
 *                . . .
 *                new FooApplicationFlagsModule(args)
 *             )
 *             .build();
 *         );
 *
 *         // Now just bootstrap the application and you're done
 *         FooStarter starter = injector.getInstance(FooStarter.class);
 *         starter.runApplication();
 *       }
 *     }
 * </pre>
 * 
 * @since 2.1
 */
public class InjectorBuilder {
  
  private final InternalInjectorCreator creator = new InternalInjectorCreator();
  
  private Stage stage = Stage.DEVELOPMENT;
  private boolean jitDisabled = false;
  private boolean allowCircularProxy = true;
  
  /**
   * Sets the stage for the injector. If the stage is {@link Stage#PRODUCTION}, 
   * singletons will be eagerly loaded when the Injector is built.
   */
  public InjectorBuilder stage(Stage stage) {
    this.stage = stage;
    return this;
  }

  /**
   * If explicit bindings are required, then classes that are not explicitly
   * bound in a module cannot be injected. Bindings created through a linked
   * binding (<code>bind(Foo.class).to(FooImpl.class)</code>) are allowed, but
   * the implicit binding (FooImpl) cannot be directly injected unless it is
   * also explicitly bound.
   * 
   * Tools can still retrieve bindings for implicit bindings (bindings created
   * through a linked binding) if explicit bindings are required, however
   * {@link Binding#getProvider} cannot be used.
   * 
   * By default, explicit bindings are not required.
   */
  public InjectorBuilder requireExplicitBindings() {
    this.jitDisabled = true;
    return this;
  }
  
  /**
   * Prevents Guice from constructing a {@link Proxy} when a circular dependency
   * is found.
   */
  public InjectorBuilder disableCircularProxies() {
    this.allowCircularProxy = false;
    return this;
  }

  /** Adds more modules that will be used when the Injector is created. */
  public InjectorBuilder addModules(Iterable<? extends Module> modules) {
    creator.addModules(modules);
    return this;
  }
  
  /** Adds more modules that will be used when the Injector is created. */
  public InjectorBuilder addModules(Module... modules) {
    creator.addModules(Arrays.asList(modules));
    return this;
  }

  /** Builds the injector. */
  public Injector build() {
    creator.injectorOptions(new InternalInjectorCreator.InjectorOptions(stage, jitDisabled, allowCircularProxy));
    return creator.build();
  }

}
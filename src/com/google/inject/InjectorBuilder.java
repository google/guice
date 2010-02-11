package com.google.inject;

/**
 * Allows Injectors to be built with many different parameters.
 * 
 * @since 2.1
 */
public interface InjectorBuilder {

  /**
   * Sets the stage for the created injector. If the stage is {@link Stage#PRODUCTION}, 
   * singletons will be eagerly loaded in when the Injector is built.
   */
  InjectorBuilder stage(Stage stage);

  /**
   * If explicit bindings are required, then classes cannot inject classes
   * that are not explicitly bound in a module.  Bindings created through
   * a linked binding <code>bind(Foo.class).to(FooImpl.class)</code> are allowed,
   * but the implicit binding (FooImpl) cannot directly injected unless it is
   * also explicitly bound.
   * 
   * Tools can still retrieve bindings for implicit bindings (bindings created
   * through a linked binding) if explicit bindings are required, however 
   * the {@link Binding#getProvider} method of the binding cannot be used.
   * 
   * By default, explicit bindings are not required.
   */
  InjectorBuilder requireExplicitBindings();
  
  /** Adds more modules that will be used when the Injector is created. */
  InjectorBuilder addModules(Iterable<? extends Module> modules);
  
  /** Adds more modules that will be used when the Injector is created. */
  InjectorBuilder addModules(Module... modules);

  /** Builds the injector. */
  Injector build();

}
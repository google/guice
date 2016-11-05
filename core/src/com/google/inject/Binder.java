/*
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Message;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Collects configuration information (primarily <i>bindings</i>) which will be used to create an
 * {@link Injector}. Guice provides this object to your application's {@link Module} implementors so
 * they may each contribute their own bindings and other registrations.
 *
 * <h3>The Guice Binding EDSL</h3>
 *
 * Guice uses an <i>embedded domain-specific language</i>, or EDSL, to help you create bindings
 * simply and readably. This approach is great for overall usability, but it does come with a small
 * cost: <b>it is difficult to learn how to use the Binding EDSL by reading method-level
 * javadocs</b>. Instead, you should consult the series of examples below. To save space, these
 * examples omit the opening {@code binder}, just as you will if your module extends {@link
 * AbstractModule}.
 *
 * <pre>
 *     bind(ServiceImpl.class);</pre>
 *
 * This statement does essentially nothing; it "binds the {@code ServiceImpl} class to itself" and
 * does not change Guice's default behavior. You may still want to use this if you prefer your
 * {@link Module} class to serve as an explicit <i>manifest</i> for the services it provides. Also,
 * in rare cases, Guice may be unable to validate a binding at injector creation time unless it is
 * given explicitly.
 *
 * <pre>
 *     bind(Service.class).to(ServiceImpl.class);</pre>
 *
 * Specifies that a request for a {@code Service} instance with no binding annotations should be
 * treated as if it were a request for a {@code ServiceImpl} instance. This <i>overrides</i> the
 * function of any {@link ImplementedBy @ImplementedBy} or {@link ProvidedBy @ProvidedBy}
 * annotations found on {@code Service}, since Guice will have already "moved on" to {@code
 * ServiceImpl} before it reaches the point when it starts looking for these annotations.
 *
 * <pre>
 *     bind(Service.class).toProvider(ServiceProvider.class);</pre>
 *
 * In this example, {@code ServiceProvider} must extend or implement {@code Provider<Service>}. This
 * binding specifies that Guice should resolve an unannotated injection request for {@code Service}
 * by first resolving an instance of {@code ServiceProvider} in the regular way, then calling {@link
 * Provider#get get()} on the resulting Provider instance to obtain the {@code Service} instance.
 *
 * <p>The {@link Provider} you use here does not have to be a "factory"; that is, a provider which
 * always <i>creates</i> each instance it provides. However, this is generally a good practice to
 * follow. You can then use Guice's concept of {@link Scope scopes} to guide when creation should
 * happen -- "letting Guice work for you".
 *
 * <pre>
 *     bind(Service.class).annotatedWith(Red.class).to(ServiceImpl.class);</pre>
 *
 * Like the previous example, but only applies to injection requests that use the binding annotation
 * {@code @Red}. If your module also includes bindings for particular <i>values</i> of the
 * {@code @Red} annotation (see below), then this binding will serve as a "catch-all" for any values
 * of {@code @Red} that have no exact match in the bindings.
 *
 * <pre>
 *     bind(ServiceImpl.class).in(Singleton.class);
 *     // or, alternatively
 *     bind(ServiceImpl.class).in(Scopes.SINGLETON);</pre>
 *
 * Either of these statements places the {@code ServiceImpl} class into singleton scope. Guice will
 * create only one instance of {@code ServiceImpl} and will reuse it for all injection requests of
 * this type. Note that it is still possible to bind another instance of {@code ServiceImpl} if the
 * second binding is qualified by an annotation as in the previous example. Guice is not overly
 * concerned with <i>preventing</i> you from creating multiple instances of your "singletons", only
 * with <i>enabling</i> your application to share only one instance if that's all you tell Guice you
 * need.
 *
 * <p><b>Note:</b> a scope specified in this way <i>overrides</i> any scope that was specified with
 * an annotation on the {@code ServiceImpl} class.
 *
 * <p>Besides {@link Singleton}/{@link Scopes#SINGLETON}, there are servlet-specific scopes
 * available in {@code com.google.inject.servlet.ServletScopes}, and your Modules can contribute
 * their own custom scopes for use here as well.
 *
 * <pre>
 *     bind(new TypeLiteral&lt;PaymentService&lt;CreditCard>>() {})
 *         .to(CreditCardPaymentService.class);</pre>
 *
 * This admittedly odd construct is the way to bind a parameterized type. It tells Guice how to
 * honor an injection request for an element of type {@code PaymentService<CreditCard>}. The class
 * {@code CreditCardPaymentService} must implement the {@code PaymentService<CreditCard>} interface.
 * Guice cannot currently bind or inject a generic type, such as {@code Set<E>}; all type parameters
 * must be fully specified.
 *
 * <pre>
 *     bind(Service.class).toInstance(new ServiceImpl());
 *     // or, alternatively
 *     bind(Service.class).toInstance(SomeLegacyRegistry.getService());</pre>
 *
 * In this example, your module itself, <i>not Guice</i>, takes responsibility for obtaining a
 * {@code ServiceImpl} instance, then asks Guice to always use this single instance to fulfill all
 * {@code Service} injection requests. When the {@link Injector} is created, it will automatically
 * perform field and method injection for this instance, but any injectable constructor on {@code
 * ServiceImpl} is simply ignored. Note that using this approach results in "eager loading" behavior
 * that you can't control.
 *
 * <pre>
 *     bindConstant().annotatedWith(ServerHost.class).to(args[0]);</pre>
 *
 * Sets up a constant binding. Constant injections must always be annotated. When a constant
 * binding's value is a string, it is eligile for conversion to all primitive types, to {@link
 * Enum#valueOf(Class, String) all enums}, and to {@link Class#forName class literals}. Conversions
 * for other types can be configured using {@link #convertToTypes(Matcher, TypeConverter)
 * convertToTypes()}.
 *
 * <pre>
 *   {@literal @}Color("red") Color red; // A member variable (field)
 *    . . .
 *     red = MyModule.class.getDeclaredField("red").getAnnotation(Color.class);
 *     bind(Service.class).annotatedWith(red).to(RedService.class);</pre>
 *
 * If your binding annotation has parameters you can apply different bindings to different specific
 * values of your annotation. Getting your hands on the right instance of the annotation is a bit of
 * a pain -- one approach, shown above, is to apply a prototype annotation to a field in your module
 * class, so that you can read this annotation instance and give it to Guice.
 *
 * <pre>
 *     bind(Service.class)
 *         .annotatedWith(Names.named("blue"))
 *         .to(BlueService.class);</pre>
 *
 * Differentiating by names is a common enough use case that we provided a standard annotation,
 * {@link com.google.inject.name.Named @Named}. Because of Guice's library support, binding by name
 * is quite easier than in the arbitrary binding annotation case we just saw. However, remember that
 * these names will live in a single flat namespace with all the other names used in your
 * application.
 *
 * <pre>
 *     Constructor<T> loneCtor = getLoneCtorFromServiceImplViaReflection();
 *     bind(ServiceImpl.class)
 *         .toConstructor(loneCtor);</pre>
 *
 * In this example, we directly tell Guice which constructor to use in a concrete class
 * implementation. It means that we do not need to place {@literal @}Inject on any of the
 * constructors and that Guice treats the provided constructor as though it were annotated so. It is
 * useful for cases where you cannot modify existing classes and is a bit simpler than using a
 * {@link Provider}.
 *
 * <p>The above list of examples is far from exhaustive. If you can think of how the concepts of one
 * example might coexist with the concepts from another, you can most likely weave the two together.
 * If the two concepts make no sense with each other, you most likely won't be able to do it. In a
 * few cases Guice will let something bogus slip by, and will then inform you of the problems at
 * runtime, as soon as you try to create your Injector.
 *
 * <p>The other methods of Binder such as {@link #bindScope}, {@link #bindInterceptor}, {@link
 * #install}, {@link #requestStaticInjection}, {@link #addError} and {@link #currentStage} are not
 * part of the Binding EDSL; you can learn how to use these in the usual way, from the method
 * documentation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author kevinb@google.com (Kevin Bourrillion)
 */
public interface Binder {

  /*if[AOP]*/
  /**
   * Binds method interceptor[s] to methods matched by class and method matchers. A method is
   * eligible for interception if:
   *
   * <ul>
   * <li>Guice created the instance the method is on
   * <li>Neither the enclosing type nor the method is final
   * <li>And the method is package-private, protected, or public
   * </ul>
   *
   * @param classMatcher matches classes the interceptor should apply to. For example: {@code
   *     only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For example: {@code
   *     annotatedWith(Transactional.class)}.
   * @param interceptors to bind. The interceptors are called in the order they are given.
   */
  void bindInterceptor(
      Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher,
      org.aopalliance.intercept.MethodInterceptor... interceptors);
  /*end[AOP]*/

  /** Binds a scope to an annotation. */
  void bindScope(Class<? extends Annotation> annotationType, Scope scope);

  /** See the EDSL examples at {@link Binder}. */
  <T> LinkedBindingBuilder<T> bind(Key<T> key);

  /** See the EDSL examples at {@link Binder}. */
  <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral);

  /** See the EDSL examples at {@link Binder}. */
  <T> AnnotatedBindingBuilder<T> bind(Class<T> type);

  /** See the EDSL examples at {@link Binder}. */
  AnnotatedConstantBindingBuilder bindConstant();

  /**
   * Upon successful creation, the {@link Injector} will inject instance fields and methods of the
   * given object.
   *
   * @param type of instance
   * @param instance for which members will be injected
   * @since 2.0
   */
  <T> void requestInjection(TypeLiteral<T> type, T instance);

  /**
   * Upon successful creation, the {@link Injector} will inject instance fields and methods of the
   * given object.
   *
   * @param instance for which members will be injected
   * @since 2.0
   */
  void requestInjection(Object instance);

  /**
   * Upon successful creation, the {@link Injector} will inject static fields and methods in the
   * given classes.
   *
   * @param types for which static members will be injected
   */
  void requestStaticInjection(Class<?>... types);

  /** Uses the given module to configure more bindings. */
  void install(Module module);

  /** Gets the current stage. */
  Stage currentStage();

  /**
   * Records an error message which will be presented to the user at a later time. Unlike throwing
   * an exception, this enable us to continue configuring the Injector and discover more errors.
   * Uses {@link String#format(String, Object[])} to insert the arguments into the message.
   */
  void addError(String message, Object... arguments);

  /**
   * Records an exception, the full details of which will be logged, and the message of which will
   * be presented to the user at a later time. If your Module calls something that you worry may
   * fail, you should catch the exception and pass it into this.
   */
  void addError(Throwable t);

  /**
   * Records an error message to be presented to the user at a later time.
   *
   * @since 2.0
   */
  void addError(Message message);

  /**
   * Returns the provider used to obtain instances for the given injection key. The returned
   * provider will not be valid until the {@link Injector} has been created. The provider will throw
   * an {@code IllegalStateException} if you try to use it beforehand.
   *
   * @since 2.0
   */
  <T> Provider<T> getProvider(Key<T> key);

  /**
   * Returns the provider used to obtain instances for the given injection key. The returned
   * provider will be attached to the injection point and will follow the nullability specified in
   * the dependency. Additionally, the returned provider will not be valid until the {@link
   * Injector} has been created. The provider will throw an {@code IllegalStateException} if you try
   * to use it beforehand.
   *
   * @since 4.0
   */
  <T> Provider<T> getProvider(Dependency<T> dependency);

  /**
   * Returns the provider used to obtain instances for the given injection type. The returned
   * provider will not be valid until the {@link Injector} has been created. The provider will throw
   * an {@code IllegalStateException} if you try to use it beforehand.
   *
   * @since 2.0
   */
  <T> Provider<T> getProvider(Class<T> type);

  /**
   * Returns the members injector used to inject dependencies into methods and fields on instances
   * of the given type {@code T}. The returned members injector will not be valid until the main
   * {@link Injector} has been created. The members injector will throw an {@code
   * IllegalStateException} if you try to use it beforehand.
   *
   * @param typeLiteral type to get members injector for
   * @since 2.0
   */
  <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral);

  /**
   * Returns the members injector used to inject dependencies into methods and fields on instances
   * of the given type {@code T}. The returned members injector will not be valid until the main
   * {@link Injector} has been created. The members injector will throw an {@code
   * IllegalStateException} if you try to use it beforehand.
   *
   * @param type type to get members injector for
   * @since 2.0
   */
  <T> MembersInjector<T> getMembersInjector(Class<T> type);

  /**
   * Binds a type converter. The injector will use the given converter to convert string constants
   * to matching types as needed.
   *
   * @param typeMatcher matches types the converter can handle
   * @param converter converts values
   * @since 2.0
   */
  void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher, TypeConverter converter);

  /**
   * Registers a listener for injectable types. Guice will notify the listener when it encounters
   * injectable types matched by the given type matcher.
   *
   * @param typeMatcher that matches injectable types the listener should be notified of
   * @param listener for injectable types matched by typeMatcher
   * @since 2.0
   */
  void bindListener(Matcher<? super TypeLiteral<?>> typeMatcher, TypeListener listener);

  /**
   * Registers listeners for provisioned objects. Guice will notify the listeners just before and
   * after the object is provisioned. Provisioned objects that are also injectable (everything
   * except objects provided through Providers) can also be notified through TypeListeners
   * registered in {@link #bindListener}.
   *
   * @param bindingMatcher that matches bindings of provisioned objects the listener should be
   *     notified of
   * @param listeners for provisioned objects matched by bindingMatcher
   * @since 4.0
   */
  void bindListener(Matcher<? super Binding<?>> bindingMatcher, ProvisionListener... listeners);

  /**
   * Returns a binder that uses {@code source} as the reference location for configuration errors.
   * This is typically a {@link StackTraceElement} for {@code .java} source but it could any binding
   * source, such as the path to a {@code .properties} file.
   *
   * @param source any object representing the source location and has a concise {@link
   *     Object#toString() toString()} value
   * @return a binder that shares its configuration with this binder
   * @since 2.0
   */
  Binder withSource(Object source);

  /**
   * Returns a binder that skips {@code classesToSkip} when identify the calling code. The caller's
   * {@link StackTraceElement} is used to locate the source of configuration errors.
   *
   * @param classesToSkip library classes that create bindings on behalf of their clients.
   * @return a binder that shares its configuration with this binder.
   * @since 2.0
   */
  Binder skipSources(Class... classesToSkip);

  /**
   * Creates a new private child environment for bindings and other configuration. The returned
   * binder can be used to add and configuration information in this environment. See {@link
   * PrivateModule} for details.
   *
   * @return a binder that inherits configuration from this binder. Only exposed configuration on
   *     the returned binder will be visible to this binder.
   * @since 2.0
   */
  PrivateBinder newPrivateBinder();

  /**
   * Instructs the Injector that bindings must be listed in a Module in order to be injected.
   * Classes that are not explicitly bound in a module cannot be injected. Bindings created through
   * a linked binding (<code>bind(Foo.class).to(FooImpl.class)</code>) are allowed, but the implicit
   * binding (<code>FooImpl</code>) cannot be directly injected unless it is also explicitly bound (
   * <code>bind(FooImpl.class)</code>).
   *
   * <p>Tools can still retrieve bindings for implicit bindings (bindings created through a linked
   * binding) if explicit bindings are required, however {@link Binding#getProvider} will fail.
   *
   * <p>By default, explicit bindings are not required.
   *
   * <p>If a parent injector requires explicit bindings, then all child injectors (and private
   * modules within that injector) also require explicit bindings. If a parent does not require
   * explicit bindings, a child injector or private module may optionally declare itself as
   * requiring explicit bindings. If it does, the behavior is limited only to that child or any
   * grandchildren. No siblings of the child will require explicit bindings.
   *
   * <p>In the absence of an explicit binding for the target, linked bindings in child injectors
   * create a binding for the target in the parent. Since this behavior can be surprising, it causes
   * an error instead if explicit bindings are required. To avoid this error, add an explicit
   * binding for the target, either in the child or the parent.
   *
   * @since 3.0
   */
  void requireExplicitBindings();

  /**
   * Prevents Guice from injecting dependencies that form a cycle, unless broken by a {@link
   * Provider}. By default, circular dependencies are not disabled.
   *
   * <p>If a parent injector disables circular dependencies, then all child injectors (and private
   * modules within that injector) also disable circular dependencies. If a parent does not disable
   * circular dependencies, a child injector or private module may optionally declare itself as
   * disabling circular dependencies. If it does, the behavior is limited only to that child or any
   * grandchildren. No siblings of the child will disable circular dependencies.
   *
   * @since 3.0
   */
  void disableCircularProxies();

  /**
   * Requires that a {@literal @}{@link Inject} annotation exists on a constructor in order for
   * Guice to consider it an eligible injectable class. By default, Guice will inject classes that
   * have a no-args constructor if no {@literal @}{@link Inject} annotation exists on any
   * constructor.
   *
   * <p>If the class is bound using {@link LinkedBindingBuilder#toConstructor}, Guice will still
   * inject that constructor regardless of annotations.
   *
   * @since 4.0
   */
  void requireAtInjectOnConstructors();

  /**
   * Requires that Guice finds an exactly matching binding annotation. This disables the error-prone
   * feature in Guice where it can substitute a binding for <code>{@literal @}Named Foo</code> when
   * attempting to inject <code>{@literal @}Named("foo") Foo</code>.
   *
   * @since 4.0
   */
  void requireExactBindingAnnotations();

  /**
   * Adds a scanner that will look in all installed modules for annotations the scanner can parse,
   * and binds them like {@literal @}Provides methods. Scanners apply to all modules installed in
   * the injector. Scanners installed in child injectors or private modules do not impact modules in
   * siblings or parents, however scanners installed in parents do apply to all child injectors and
   * private modules.
   *
   * @since 4.0
   */
  void scanModulesForAnnotatedMethods(ModuleAnnotatedMethodScanner scanner);
}

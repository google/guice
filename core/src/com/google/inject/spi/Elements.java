/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.internal.InternalFlags.getIncludeStackTraceOption;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.AnnotatedElementBuilder;
import com.google.inject.internal.AbstractBindingBuilder;
import com.google.inject.internal.BindingBuilder;
import com.google.inject.internal.ConstantBindingBuilderImpl;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ExposureBuilder;
import com.google.inject.internal.GuiceInternal;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import com.google.inject.internal.MoreTypes;
import com.google.inject.internal.PrivateElementsImpl;
import com.google.inject.internal.ProviderMethod;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.internal.util.SourceProvider;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exposes elements of a module so they can be inspected, validated or {@link
 * Element#applyTo(Binder) rewritten}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class Elements {

  private static final BindingTargetVisitor<Object, Object> GET_INSTANCE_VISITOR =
      new DefaultBindingTargetVisitor<Object, Object>() {
        @Override
        public Object visit(InstanceBinding<?> binding) {
          return binding.getInstance();
        }

        @Override
        protected Object visitOther(Binding<?> binding) {
          throw new IllegalArgumentException();
        }
      };

  /** Records the elements executed by {@code modules}. */
  public static List<Element> getElements(Module... modules) {
    return getElements(Stage.DEVELOPMENT, Arrays.asList(modules));
  }

  /** Records the elements executed by {@code modules}. */
  public static List<Element> getElements(Stage stage, Module... modules) {
    return getElements(stage, Arrays.asList(modules));
  }

  /** Records the elements executed by {@code modules}. */
  public static List<Element> getElements(Iterable<? extends Module> modules) {
    return getElements(Stage.DEVELOPMENT, modules);
  }

  /** Records the elements executed by {@code modules}. */
  public static List<Element> getElements(Stage stage, Iterable<? extends Module> modules) {
    RecordingBinder binder = new RecordingBinder(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    binder.scanForAnnotatedMethods();
    for (RecordingBinder child : binder.privateBindersForScanning) {
      child.scanForAnnotatedMethods();
    }
    binder.permitMapConstruction.finish();
    // Free the memory consumed by the stack trace elements cache
    StackTraceElements.clearCache();
    return Collections.unmodifiableList(binder.elements);
  }

  // TODO(user): Consider moving the RecordingBinder to com.google.inject.internal and removing these
  // internal 'friend' methods.
  /**
   * Internal version of Binder.withSource for establishing a trusted ElementSource chain for
   * source-restricting bindings that are re-written using {@link Element#applyTo}.
   *
   * <p>Using Binder.withSource is not trustworthy because it's a public API that external users can
   * use to spoof the original ElementSource of a binding by calling withSource(bogusElementSource).
   */
  public static Binder withTrustedSource(
      GuiceInternal guiceInternal, Binder binder, Object source) {
    checkNotNull(guiceInternal);
    if (binder instanceof RecordingBinder) {
      return ((RecordingBinder) binder).withTrustedSource(source);
    }
    // Preserve existing (untrusted) behavior for non-standard Binder implementations.
    return binder.withSource(source);
  }

  private static class ElementsAsModule implements Module {
    private final Iterable<? extends Element> elements;

    ElementsAsModule(Iterable<? extends Element> elements) {
      this.elements = elements;
    }

    @Override
    public void configure(Binder binder) {
      for (Element element : elements) {
        element.applyTo(binder);
      }
    }
  }

  /** Returns the module composed of {@code elements}. */
  public static Module getModule(final Iterable<? extends Element> elements) {
    return new ElementsAsModule(elements);
  }

  @SuppressWarnings("unchecked")
  static <T> BindingTargetVisitor<T, T> getInstanceVisitor() {
    return (BindingTargetVisitor<T, T>) GET_INSTANCE_VISITOR;
  }

  private static class ModuleInfo {
    private final ModuleSource moduleSource;
    private final boolean skipScanning;

    private ModuleInfo(ModuleSource moduleSource, boolean skipScanning) {
      this.moduleSource = moduleSource;
      this.skipScanning = skipScanning;
    }
  }

  private static class RecordingBinder implements Binder, PrivateBinder {
    private final Stage stage;
    private final Map<Module, ModuleInfo> modules;
    private final List<Element> elements;
    private final Object source;
    private final SourceProvider sourceProvider;
    private final Set<ModuleAnnotatedMethodScanner> scanners;
    /** The binder where exposed bindings will be created */
    private final RecordingBinder parent;

    private final PrivateElementsImpl privateElements;
    /** All children private binders, so we can scan through them. */
    private final List<RecordingBinder> privateBindersForScanning;

    private final BindingSourceRestriction.PermitMapConstruction permitMapConstruction;

    /** The current modules stack */
    private ModuleSource moduleSource = null;
    /**
     * The current scanner.
     *
     * <p>Note that scanners cannot nest, ie. a scanner cannot install a module that requires
     * scanning - except the built-in @Provides* methods. The built-in scanner isn't tracked by this
     * variable, only custom scanners are.
     */
    private ModuleAnnotatedMethodScanner scannerSource = null;

    private ModuleAnnotatedMethodScanner currentScanner = null;
    private boolean trustedSource = false;

    private RecordingBinder(Stage stage) {
      this.stage = stage;
      this.modules = Maps.newLinkedHashMap();
      this.scanners = Sets.newLinkedHashSet();
      this.elements = Lists.newArrayList();
      this.source = null;
      this.sourceProvider =
          SourceProvider.DEFAULT_INSTANCE.plusSkippedClasses(
              Elements.class,
              RecordingBinder.class,
              AbstractModule.class,
              ConstantBindingBuilderImpl.class,
              AbstractBindingBuilder.class,
              BindingBuilder.class);
      this.parent = null;
      this.privateElements = null;
      this.privateBindersForScanning = Lists.newArrayList();
      this.permitMapConstruction = new BindingSourceRestriction.PermitMapConstruction();
    }

    /** Creates a recording binder that's backed by {@code prototype}. */
    private RecordingBinder(
        RecordingBinder prototype,
        Object source,
        SourceProvider sourceProvider,
        boolean trustedSource) {
      checkArgument(source == null ^ sourceProvider == null);

      this.stage = prototype.stage;
      this.modules = prototype.modules;
      this.elements = prototype.elements;
      this.scanners = prototype.scanners;
      this.currentScanner = prototype.currentScanner;
      this.source = source;
      this.trustedSource = trustedSource;
      this.moduleSource = prototype.moduleSource;
      this.sourceProvider = sourceProvider;
      this.parent = prototype.parent;
      this.privateElements = prototype.privateElements;
      this.privateBindersForScanning = prototype.privateBindersForScanning;
      this.permitMapConstruction = prototype.permitMapConstruction;
      this.scannerSource = prototype.scannerSource;
    }

    /** Creates a private recording binder. */
    private RecordingBinder(RecordingBinder parent, PrivateElementsImpl privateElements) {
      this.stage = parent.stage;
      this.modules = Maps.newLinkedHashMap();
      this.scanners = Sets.newLinkedHashSet();
      this.currentScanner = parent.currentScanner;
      this.elements = privateElements.getElementsMutable();
      this.source = parent.source;
      this.moduleSource = parent.moduleSource;
      this.sourceProvider = parent.sourceProvider;
      this.parent = parent;
      this.privateElements = privateElements;
      this.privateBindersForScanning = parent.privateBindersForScanning;
      this.permitMapConstruction = parent.permitMapConstruction;
      this.scannerSource = parent.scannerSource;
    }

    /*if[AOP]*/
    @Override
    public void bindInterceptor(
        Matcher<? super Class<?>> classMatcher,
        Matcher<? super Method> methodMatcher,
        org.aopalliance.intercept.MethodInterceptor... interceptors) {
      elements.add(
          new InterceptorBinding(getElementSource(), classMatcher, methodMatcher, interceptors));
    }
    /*end[AOP]*/

    @Override
    public void bindScope(Class<? extends Annotation> annotationType, Scope scope) {
      elements.add(new ScopeBinding(getElementSource(), annotationType, scope));
    }

    @Override
    @SuppressWarnings("unchecked") // it is safe to use the type literal for the raw type
    public void requestInjection(Object instance) {
      checkNotNull(instance, "instance");
      requestInjection((TypeLiteral<Object>) TypeLiteral.get(instance.getClass()), instance);
    }

    @Override
    public <T> void requestInjection(TypeLiteral<T> type, T instance) {
      checkNotNull(instance, "instance");
      elements.add(
          new InjectionRequest<T>(
              getElementSource(), MoreTypes.canonicalizeForKey(type), instance));
    }

    @Override
    public <T> MembersInjector<T> getMembersInjector(final TypeLiteral<T> typeLiteral) {
      final MembersInjectorLookup<T> element =
          new MembersInjectorLookup<T>(
              getElementSource(), MoreTypes.canonicalizeForKey(typeLiteral));
      elements.add(element);
      return element.getMembersInjector();
    }

    @Override
    public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
      return getMembersInjector(TypeLiteral.get(type));
    }

    @Override
    public void bindListener(Matcher<? super TypeLiteral<?>> typeMatcher, TypeListener listener) {
      elements.add(new TypeListenerBinding(getElementSource(), listener, typeMatcher));
    }

    @Override
    public void bindListener(
        Matcher<? super Binding<?>> bindingMatcher, ProvisionListener... listeners) {
      elements.add(new ProvisionListenerBinding(getElementSource(), bindingMatcher, listeners));
    }

    @Override
    public void requestStaticInjection(Class<?>... types) {
      for (Class<?> type : types) {
        elements.add(new StaticInjectionRequest(getElementSource(), type));
      }
    }

    /**
     * Applies all scanners to the modules we've installed. We skip certain PrivateModules because
     * store them in more than one Modules map and only want to process them through one of the
     * maps. (They're stored in both maps to prevent a module from being installed more than once.)
     */
    void scanForAnnotatedMethods() {
      Iterable<ModuleAnnotatedMethodScanner> scanners = getAllScanners();
      // Note: we must iterate over a copy of the modules because calling install(..)
      // will mutate modules, otherwise causing a ConcurrentModificationException.
      for (Map.Entry<Module, ModuleInfo> entry : Maps.newLinkedHashMap(modules).entrySet()) {
        Module module = entry.getKey();
        ModuleInfo info = entry.getValue();
        if (info.skipScanning) {
          continue;
        }
        for (ModuleAnnotatedMethodScanner scanner : scanners) {
          currentScanner = scanner;
          moduleSource = entry.getValue().moduleSource;
          permitMapConstruction.restoreCurrentModulePermits(moduleSource);
          try {
            install(ProviderMethodsModule.forModule(module, scanner));
          } catch (RuntimeException e) {
            Collection<Message> messages = Errors.getMessagesFromThrowable(e);
            if (!messages.isEmpty()) {
              elements.addAll(messages);
            } else {
              addError(e);
            }
          }
        }
      }
      moduleSource = null;
    }

    @Override
    public void install(Module module) {
      // Ignore duplicate installations of the same module instance.
      if (modules.containsKey(module)) {
        return;
      }
      // Whether the module installed is a ProviderMethodModule for a custom scanner.
      boolean customScanner = false;
      Class<?> newModuleClass = null;
      RecordingBinder binder = this;
      // Update the module source for the new module
      if (module instanceof ProviderMethodsModule) {
        ProviderMethodsModule providerMethodsModule = (ProviderMethodsModule) module;
        if (!providerMethodsModule.isScanningBuiltInProvidesMethods()) {
          scannerSource = providerMethodsModule.getScanner();
          customScanner = true;
        }
        // There are two reason's we'd want to get the module source in a ProviderMethodsModule.
        // ModuleAnnotatedMethodScanner lets users scan their own modules for @Provides-like
        // bindings.  If they install the module at a top-level, then moduleSource can be null.
        // Also, if they pass something other than 'this' to it, we'd have the wrong source.
        Class<?> delegateClass = providerMethodsModule.getDelegateModuleClass();
        if (moduleSource == null
            || !moduleSource.getModuleClassName().equals(delegateClass.getName())) {
          newModuleClass = delegateClass;
        }
      } else {
        if (moduleScanning()) {
          forbidNestedScannerMethods(module);
        }
        newModuleClass = module.getClass();
      }
      if (newModuleClass != null) {
        moduleSource = getModuleSource(newModuleClass);
        permitMapConstruction.pushModule(newModuleClass, moduleSource);
      }
      boolean skipScanning = false;
      if (module instanceof PrivateModule) {
        binder = (RecordingBinder) binder.newPrivateBinder();
        // Store the module in the private binder too so we scan for it.
        binder.modules.put(module, new ModuleInfo(moduleSource, false));
        skipScanning = true; // don't scan this module in the parent's module set.
      }
      // Always store this in the parent binder (even if it was a private module)
      // so that we know not to process it again, and so that scanners inherit down.
      modules.put(module, new ModuleInfo(moduleSource, skipScanning));
      try {
        module.configure(binder);
      } catch (RuntimeException e) {
        Collection<Message> messages = Errors.getMessagesFromThrowable(e);
        if (!messages.isEmpty()) {
          elements.addAll(messages);
        } else {
          addError(e);
        }
      }
      binder.install(ProviderMethodsModule.forModule(module));
      // We are done with this module, so undo module source change
      if (newModuleClass != null) {
        moduleSource = moduleSource.getParent();
        permitMapConstruction.popModule();
      }
      // Only wipe the scannerSource once custom scanner installation is finished. This way all
      // bindings created by the custom scanner will have it as their scanner source, including
      // bindings created by the built-in scanner scanning @Provides* methods in modules installed
      // by the custom scanner.
      if (customScanner) {
        scannerSource = null;
      }
    }

    private void forbidNestedScannerMethods(Module module) {
      for (ModuleAnnotatedMethodScanner scanner : getAllScanners()) {
        ProviderMethodsModule providerMethodsModule =
            (ProviderMethodsModule) ProviderMethodsModule.forModule(module, scanner);
        for (ProviderMethod<?> method : providerMethodsModule.getProviderMethods(this)) {
          addError(
              "Scanner %s is installing a module with %s method. Installing modules with custom "
                  + "provides methods from a ModuleAnnotatedMethodScanner is not supported.",
              currentScanner, method.getAnnotation().annotationType().getCanonicalName());
        }
      }
    }

    /**
     * Get all scanners registered in this binder and its ancestors.
     *
     * <p>Should only be called during module scanning, because at that point registering new
     * scanners is forbidden.
     */
    private Iterable<ModuleAnnotatedMethodScanner> getAllScanners() {
      if (privateElements == null) {
        return scanners;
      }
      // Private binders have their own set of scanners and they inherit from their parent.
      return Iterables.concat(scanners, parent.getAllScanners());
    }

    @Override
    public Stage currentStage() {
      return stage;
    }

    @Override
    public void addError(String message, Object... arguments) {
      elements.add(new Message(getElementSource(), Errors.format(message, arguments)));
    }

    @Override
    public void addError(Throwable t) {
      String message = "An exception was caught and reported. Message: " + t.getMessage();
      elements.add(new Message(ImmutableList.of((Object) getElementSource()), message, t));
    }

    @Override
    public void addError(Message message) {
      elements.add(message);
    }

    @Override
    public <T> AnnotatedBindingBuilder<T> bind(Key<T> key) {
      BindingBuilder<T> builder =
          new BindingBuilder<T>(this, elements, getElementSource(), MoreTypes.canonicalizeKey(key));
      return builder;
    }

    @Override
    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
      return bind(Key.get(typeLiteral));
    }

    @Override
    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type) {
      return bind(Key.get(type));
    }

    @Override
    public AnnotatedConstantBindingBuilder bindConstant() {
      return new ConstantBindingBuilderImpl<Void>(this, elements, getElementSource());
    }

    @Override
    public <T> Provider<T> getProvider(final Key<T> key) {
      return getProvider(Dependency.get(key));
    }

    @Override
    public <T> Provider<T> getProvider(final Dependency<T> dependency) {
      final ProviderLookup<T> element = new ProviderLookup<>(getElementSource(), dependency);
      elements.add(element);
      return element.getProvider();
    }

    @Override
    public <T> Provider<T> getProvider(Class<T> type) {
      return getProvider(Key.get(type));
    }

    @Override
    public void convertToTypes(
        Matcher<? super TypeLiteral<?>> typeMatcher, TypeConverter converter) {
      elements.add(new TypeConverterBinding(getElementSource(), typeMatcher, converter));
    }

    @Override
    public RecordingBinder withSource(final Object source) {
      return source == this.source
          ? this
          : new RecordingBinder(
              this, source, /* sourceProvider = */ null, /* trustedSource = */ false);
    }

    public RecordingBinder withTrustedSource(final Object source) {
      return source == this.source
          ? this
          : new RecordingBinder(
              this, source, /* sourceProvider = */ null, /* trustedSource = */ true);
    }

    @Override
    public RecordingBinder skipSources(Class<?>... classesToSkip) {
      // if a source is specified explicitly, we don't need to skip sources
      if (source != null) {
        return this;
      }

      SourceProvider newSourceProvider = sourceProvider.plusSkippedClasses(classesToSkip);
      return new RecordingBinder(
          this, /* source = */ null, newSourceProvider, /* trustedSource = */ false);
    }

    @Override
    public PrivateBinder newPrivateBinder() {
      PrivateElementsImpl privateElements = new PrivateElementsImpl(getElementSource());
      RecordingBinder binder = new RecordingBinder(this, privateElements);
      elements.add(privateElements);
      // Don't want to scan private modules installed by scanners.
      if (!moduleScanning()) {
        privateBindersForScanning.add(binder);
      }
      return binder;
    }

    @Override
    public void disableCircularProxies() {
      elements.add(new DisableCircularProxiesOption(getElementSource()));
    }

    @Override
    public void requireExplicitBindings() {
      elements.add(new RequireExplicitBindingsOption(getElementSource()));
    }

    @Override
    public void requireAtInjectOnConstructors() {
      elements.add(new RequireAtInjectOnConstructorsOption(getElementSource()));
    }

    @Override
    public void requireExactBindingAnnotations() {
      elements.add(new RequireExactBindingAnnotationsOption(getElementSource()));
    }

    @Override
    public void scanModulesForAnnotatedMethods(ModuleAnnotatedMethodScanner scanner) {
      if (moduleScanning()) {
        addError(
            "Attempting to register ModuleAnnotatedMethodScanner %s from scanner %s. Scanners are"
                + " not allowed to register other scanners.",
            currentScanner, scanner);
        return;
      }
      scanners.add(scanner);
      elements.add(new ModuleAnnotatedMethodScannerBinding(getElementSource(), scanner));
    }

    @Override
    public void expose(Key<?> key) {
      exposeInternal(key);
    }

    @Override
    public AnnotatedElementBuilder expose(Class<?> type) {
      return exposeInternal(Key.get(type));
    }

    @Override
    public AnnotatedElementBuilder expose(TypeLiteral<?> type) {
      return exposeInternal(Key.get(type));
    }

    private <T> AnnotatedElementBuilder exposeInternal(Key<T> key) {
      if (privateElements == null) {
        addError(
            "Cannot expose %s on a standard binder. "
                + "Exposed bindings are only applicable to private binders.",
            key);
        return new AnnotatedElementBuilder() {
          @Override
          public void annotatedWith(Class<? extends Annotation> annotationType) {}

          @Override
          public void annotatedWith(Annotation annotation) {}
        };
      }

      ExposureBuilder<T> builder =
          new ExposureBuilder<T>(this, getElementSource(), MoreTypes.canonicalizeKey(key));
      privateElements.addExposureBuilder(builder);
      return builder;
    }

    private ModuleSource getModuleSource(Class<?> module) {
      StackTraceElement[] partialCallStack;
      if (getIncludeStackTraceOption() == IncludeStackTraceOption.COMPLETE) {
        partialCallStack = getPartialCallStack(new Throwable().getStackTrace());
      } else {
        partialCallStack = new StackTraceElement[0];
      }
      if (moduleSource == null) {
        return new ModuleSource(module, partialCallStack, permitMapConstruction.getPermitMap());
      }
      return moduleSource.createChild(module, partialCallStack);
    }

    private ElementSource getElementSource() {
      // Full call stack
      StackTraceElement[] callStack = null;
      // The call stack starts from current top module configure and ends at this method caller
      StackTraceElement[] partialCallStack = new StackTraceElement[0];
      // The element original source
      ElementSource originalSource = null;
      // The element declaring source
      Object declaringSource = source;
      if (declaringSource instanceof ElementSource) {
        originalSource = (ElementSource) declaringSource;
        declaringSource = originalSource.getDeclaringSource();
      }
      IncludeStackTraceOption stackTraceOption = getIncludeStackTraceOption();
      if (stackTraceOption == IncludeStackTraceOption.COMPLETE
          || (stackTraceOption == IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE
              && declaringSource == null)) {
        callStack = new Throwable().getStackTrace();
      }
      if (stackTraceOption == IncludeStackTraceOption.COMPLETE) {
        partialCallStack = getPartialCallStack(callStack);
      }
      if (declaringSource == null) {
        // So 'source' and 'originalSource' are null otherwise declaringSource has some value
        if (stackTraceOption == IncludeStackTraceOption.COMPLETE
            || stackTraceOption == IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE) {
          // With the above conditions and assignments 'callStack' is non-null
          declaringSource = sourceProvider.get(callStack);
        } else { // or if (stackTraceOption == IncludeStackTraceOptions.OFF)
          // As neither 'declaring source' nor 'call stack' is available use 'module source'
          declaringSource = sourceProvider.getFromClassNames(moduleSource.getModuleClassNames());
        }
      }
      // Build the binding call stack
      return new ElementSource(
          originalSource,
          trustedSource,
          declaringSource,
          moduleSource,
          partialCallStack,
          scannerSource);
    }

    /**
     * Removes the {@link #moduleSource} call stack from the beginning of current call stack. It
     * also removes the last two elements in order to make {@link #install(Module)} the last call in
     * the call stack.
     */
    private StackTraceElement[] getPartialCallStack(StackTraceElement[] callStack) {
      int toSkip = 0;
      if (moduleSource != null) {
        toSkip = moduleSource.getStackTraceSize();
      }
      // -1 for skipping 'getModuleSource' and 'getElementSource' calls
      int chunkSize = callStack.length - toSkip - 1;

      StackTraceElement[] partialCallStack = new StackTraceElement[chunkSize];
      System.arraycopy(callStack, 1, partialCallStack, 0, chunkSize);
      return partialCallStack;
    }

    /** Returns if the binder is in the module scanning phase. */
    private boolean moduleScanning() {
      return currentScanner != null;
    }

    @Override
    public String toString() {
      return "Binder";
    }
  }
}

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.RestrictedBindingSource;
import com.google.inject.RestrictedBindingSource.RestrictionLevel;
import com.google.inject.internal.Errors;
import com.google.inject.internal.GuiceInternal;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Contains abstractions for enforcing {@link RestrictedBindingSource}.
 *
 * <p>Enforcement happens in two phases:
 *
 * <ol>
 *   <li>Data structures for enforcement are built during Binder configuration. {@link
 *       PermitMapConstruction} encapsulates this process, and the {@link PermitMap} is the end
 *       result.
 *   <li>Restrictions are enforced by checking each binding for violations with {@link #check},
 *       which uses the {@link PermitMap}(s) built during Binder configuration.
 * </ol>
 *
 * <p>Note: None of this is thread-safe because it's only used while the Injector is being built,
 * which happens on a single thread.
 *
 * @author vzm@google.com (Vladimir Makaric)
 */
public final class BindingSourceRestriction {
  private BindingSourceRestriction() {}

  private static final Logger logger = Logger.getLogger(RestrictedBindingSource.class.getName());

  /** Mapping between an element source and its permit annotations. */
  interface PermitMap {
    ImmutableSet<Class<? extends Annotation>> getPermits(ElementSource elementSource);

    void clear();
  }

  /**
   * Returns all the restriction violations found on the given Module Elements, as error messages.
   *
   * <p>Note: Intended to be used on Module Elements, not Injector Elements, ie. the result of
   * {@link Elements#getElements} not {@code Injector.getElements}. The Module Elements this check
   * cares about are:
   *
   * <ul>
   *   <li>Module Bindings, which are always explicit and always have an {@link ElementSource} (with
   *       a Module Stack), unlike Injector Bindings, which may be implicit and bereft of an
   *       ElementSource.
   *   <li>{@link PrivateElements}, which represent the recursive case of this check. They contain a
   *       list of elements that this check is recursively called on.
   * </ul>
   */
  public static ImmutableList<Message> check(GuiceInternal guiceInternal, List<Element> elements) {
    checkNotNull(guiceInternal);
    ImmutableList<Message> errorMessages = check(elements);
    // Clear all the permit maps after the checks are done.
    elements.forEach(BindingSourceRestriction::clear);
    return errorMessages;
  }

  private static ImmutableList<Message> check(List<Element> elements) {
    ImmutableList.Builder<Message> errorMessagesBuilder = ImmutableList.builder();
    for (Element element : elements) {
      errorMessagesBuilder.addAll(check(element));
    }
    return errorMessagesBuilder.build();
  }

  private static ImmutableList<Message> check(Element element) {
    return element.acceptVisitor(
        new DefaultElementVisitor<ImmutableList<Message>>() {
          // Base case.
          @Override
          protected ImmutableList<Message> visitOther(Element element) {
            return ImmutableList.of();
          }
          // Base case.
          @Override
          public <T> ImmutableList<Message> visit(Binding<T> binding) {
            Optional<Message> errorMessage = check(binding);
            if (errorMessage.isPresent()) {
              return ImmutableList.of(errorMessage.get());
            }
            return ImmutableList.of();
          }
          // Recursive case.
          @Override
          public ImmutableList<Message> visit(PrivateElements privateElements) {
            return check(privateElements.getElements());
          }
        });
  }

  private static Optional<Message> check(Binding<?> binding) {
    Key<?> key = binding.getKey();
    // Module Bindings are all explicit and have an ElementSource.
    ElementSource elementSource = (ElementSource) binding.getSource();
    // If the key is annotated then only the annotation restriction matters, the type restriction is
    // ignored (an annotated type is essentially a new type).
    RestrictedBindingSource restriction =
        key.getAnnotationType() == null
            ? key.getTypeLiteral().getRawType().getAnnotation(RestrictedBindingSource.class)
            : key.getAnnotationType().getAnnotation(RestrictedBindingSource.class);
    if (restriction == null) {
      return Optional.empty();
    }
    ImmutableSet<Class<? extends Annotation>> permits = getAllPermits(elementSource);
    ImmutableSet<Class<? extends Annotation>> acceptablePermits =
        ImmutableSet.copyOf(restriction.permits());
    boolean bindingPermitted = permits.stream().anyMatch(acceptablePermits::contains);
    if (bindingPermitted || isExempt(elementSource, restriction.exemptModules())) {
      return Optional.empty();
    }
    String violationMessage =
        getViolationMessage(
            key, restriction.explanation(), acceptablePermits, key.getAnnotationType() != null);
    if (restriction.restrictionLevel() == RestrictionLevel.WARNING) {
      Formatter sourceFormatter = new Formatter();
      Errors.formatSource(sourceFormatter, elementSource);
      logger.log(Level.WARNING, violationMessage + "\n" + sourceFormatter);
      return Optional.empty();
    }
    return Optional.of(new Message(elementSource, violationMessage));
  }

  private static String getViolationMessage(
      Key<?> key,
      String explanation,
      ImmutableSet<Class<? extends Annotation>> acceptablePermits,
      boolean annotationRestricted) {
    return String.format(
        "Unable to bind key: %s. One of the modules that created this binding has to be annotated"
            + " with one of %s, because the key's %s is annotated with @RestrictedBindingSource."
            + " %s",
        key,
        acceptablePermits.stream().map(a -> "@" + a.getName()).collect(toList()),
        annotationRestricted ? "annotation" : "type",
        explanation);
  }

  /** Get all permits on the element source chain. */
  private static ImmutableSet<Class<? extends Annotation>> getAllPermits(
      ElementSource elementSource) {
    ImmutableSet.Builder<Class<? extends Annotation>> permitsBuilder = ImmutableSet.builder();
    permitsBuilder.addAll(elementSource.moduleSource.getPermitMap().getPermits(elementSource));
    if (elementSource.scanner != null) {
      getPermits(elementSource.scanner.getClass()).forEach(permitsBuilder::add);
    }
    if (elementSource.getOriginalElementSource() != null
        && elementSource.trustedOriginalElementSource) {
      permitsBuilder.addAll(getAllPermits(elementSource.getOriginalElementSource()));
    }
    return permitsBuilder.build();
  }

  private static boolean isExempt(ElementSource elementSource, String exemptModulesRegex) {
    if (exemptModulesRegex.isEmpty()) {
      return false;
    }
    Pattern exemptModulePattern = Pattern.compile(exemptModulesRegex);
    // TODO(b/156759807): Switch to Streams.stream (instead of inlining it).
    return StreamSupport.stream(getAllModules(elementSource).spliterator(), false)
        .anyMatch(moduleName -> exemptModulePattern.matcher(moduleName).matches());
  }

  private static Iterable<String> getAllModules(ElementSource elementSource) {
    List<String> modules = elementSource.getModuleClassNames();
    if (elementSource.getOriginalElementSource() == null
        || !elementSource.trustedOriginalElementSource) {
      return modules;
    }
    return Iterables.concat(modules, getAllModules(elementSource.getOriginalElementSource()));
  }

  private static void clear(Element element) {
    element.acceptVisitor(
        new DefaultElementVisitor<Void>() {
          // Base case.
          @Override
          protected Void visitOther(Element element) {
            Object source = element.getSource();
            // Some Module Elements, like Message, don't always have an ElementSource.
            if (source instanceof ElementSource) {
              clear((ElementSource) source);
            }
            return null;
          }
          // Recursive case.
          @Override
          public Void visit(PrivateElements privateElements) {
            privateElements.getElements().forEach(BindingSourceRestriction::clear);
            return null;
          }
        });
  }

  private static void clear(ElementSource elementSource) {
    while (elementSource != null) {
      elementSource.moduleSource.getPermitMap().clear();
      elementSource = elementSource.getOriginalElementSource();
    }
  }

  /**
   * Builds the map from each module to all the permit annotations on its module stack.
   *
   * <p>Bindings refer to the module that created them via a {@link ModuleSource}. The map built
   * here maps a module's {@link ModuleSource} to all the {@link RestrictedBindingSource.Permit}
   * annotations found on the path from the root of the module hierarchy to it. This path contains
   * all the modules that transitively install the module (including the module itself). This path
   * is also known as the module stack.
   *
   * <p>The map is built by piggybacking on the depth-first traversal of the module hierarchy during
   * Binder configuration.
   */
  static final class PermitMapConstruction {
    private static final class PermitMapImpl implements PermitMap {
      Map<ModuleSource, ImmutableSet<Class<? extends Annotation>>> modulePermits;

      @Override
      public ImmutableSet<Class<? extends Annotation>> getPermits(ElementSource elementSource) {
        return modulePermits.get(elementSource.moduleSource);
      }

      @Override
      public void clear() {
        modulePermits = null;
      }
    }

    final Map<ModuleSource, ImmutableSet<Class<? extends Annotation>>> modulePermits =
        new HashMap<>();
    // Maintains the permits on the current module installation path.
    ImmutableSet<Class<? extends Annotation>> currentModulePermits = ImmutableSet.of();
    // Stack tracking the currentModulePermits during module traversal.
    final Deque<ImmutableSet<Class<? extends Annotation>>> modulePermitsStack = new ArrayDeque<>();

    final PermitMapImpl permitMap = new PermitMapImpl();

    /**
     * Returns a possibly unfinished map. The map should only be used after the construction is
     * finished.
     */
    PermitMap getPermitMap() {
      return permitMap;
    }

    /**
     * Sets the permits on the current module installation path to the permits on the given module
     * source so that subsequently installed modules may inherit them. Used only for method
     * scanning, so that modules installed by scanners inherit permits from the method's module.
     */
    void restoreCurrentModulePermits(ModuleSource moduleSource) {
      currentModulePermits = modulePermits.get(moduleSource);
    }

    /** Called by the Binder prior to entering a module's configure method. */
    void pushModule(Class<?> module, ModuleSource moduleSource) {
      List<Class<? extends Annotation>> newModulePermits =
          getPermits(module)
              .filter(permit -> !currentModulePermits.contains(permit))
              .collect(toList());
      // Save the parent module's permits so that they can be restored when the Binder exits this
      // new (child) module's configure method.
      modulePermitsStack.push(currentModulePermits);
      if (!newModulePermits.isEmpty()) {
        currentModulePermits =
            ImmutableSet.<Class<? extends Annotation>>builder()
                .addAll(currentModulePermits)
                .addAll(newModulePermits)
                .build();
      }
      modulePermits.put(moduleSource, currentModulePermits);
    }

    /** Called by the Binder when it exits a module's configure method. */
    void popModule() {
      // Restore the parent module's permits.
      currentModulePermits = modulePermitsStack.pop();
    }

    /** Finishes the {@link PermitMap}. Called by the Binder when all modules are installed. */
    void finish() {
      permitMap.modulePermits = modulePermits;
    }

    @VisibleForTesting
    static boolean isElementSourceCleared(ElementSource elementSource) {
      PermitMapImpl permitMap = (PermitMapImpl) elementSource.moduleSource.getPermitMap();
      return permitMap.modulePermits == null;
    }
  }

  private static Stream<Class<? extends Annotation>> getPermits(Class<?> clazz) {
    Stream<Annotation> annotations = Arrays.stream(clazz.getAnnotations());
    // Pick up annotations on anonymous classes (e.g. new @Bar Foo() { ... }):
    if (clazz.getAnnotatedSuperclass() != null) {
      annotations =
          Stream.concat(
              annotations, Arrays.stream(clazz.getAnnotatedSuperclass().getAnnotations()));
    }
    return annotations
        .map(Annotation::annotationType)
        .filter(a -> a.isAnnotationPresent(RestrictedBindingSource.Permit.class));
  }
}

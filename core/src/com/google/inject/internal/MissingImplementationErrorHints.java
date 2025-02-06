package com.google.inject.internal;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.min;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.BindingSourceRestriction;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.UntargettedBinding;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

// TODO(b/165344346): Migrate to use suggest hints API.
/** Helper class to find hints for {@link MissingImplementationError}. */
final class MissingImplementationErrorHints {

  private MissingImplementationErrorHints() {}

  /** When a binding is not found, show at most this many bindings with the same type */
  private static final int MAX_MATCHING_TYPES_REPORTED = 3;

  /** When a binding is not found, show at most this many bindings that have some similarities */
  private static final int MAX_RELATED_TYPES_REPORTED = 3;

  private static final String WILDCARD_EXTENDS = "? extends ";
  private static final String WILDCARD_SUPER = "? super ";

  private static final String WILDCARDS_WARNING =
      "\nYou might be running into a @JvmSuppressWildcards or @JvmWildcard issue.";

  private static final String WILDCARDS_POSSIBLE_FIXES =
      "\nConsider these options instead (these are guesses but use your best judgment):";

  /**
   * If the key is unknown and it is one of these types, it generally means there is a missing
   * annotation.
   */
  private static final ImmutableSet<Class<?>> COMMON_AMBIGUOUS_TYPES =
      ImmutableSet.<Class<?>>builder()
          .add(Object.class)
          .add(String.class)
          .addAll(Primitives.allWrapperTypes())
          .build();

  /**
   * Returns whether two types look similar (i.e. if you were to ignore their package). This helps
   * users who, for example, have injected the wrong Optional (java.util.Optional vs
   * com.google.common.base.Optional). For generic types, the entire structure must mostly match
   * (wildcard types of extends and super can be ignored) in addition to the simple names of the
   * generic arguments (e.g. Optional&lt;String&gt; won't be similar to Optional&lt;Integer&gt;).
   */
  static boolean areSimilarLookingTypes(Type a, Type b) {
    return areSimilarTypes(
        a, b, (aClass, bClass) -> aClass.getSimpleName().equals(bClass.getSimpleName()));
  }

  /**
   * Returns whether the two types are different but only differ in variance (e.g. Foo vs ? extends
   * Foo). Same types will return false.
   */
  static boolean differOnlyInVariance(Type a, Type b) {
    return !a.equals(b) && areSimilarTypes(a, b, Object::equals);
  }

  private static boolean areSimilarTypes(
      Type a, Type b, BiFunction<Class<?>, Class<?>, Boolean> classSimilarityChecker) {
    if (a instanceof Class && b instanceof Class) {
      return classSimilarityChecker.apply((Class<?>) a, (Class<?>) b);
    }
    if (a instanceof ParameterizedType && b instanceof ParameterizedType) {
      ParameterizedType aType = (ParameterizedType) a;
      ParameterizedType bType = (ParameterizedType) b;
      if (!areSimilarTypes(aType.getRawType(), bType.getRawType(), classSimilarityChecker)) {
        return false;
      }
      Type[] aArgs = aType.getActualTypeArguments();
      Type[] bArgs = bType.getActualTypeArguments();
      if (aArgs.length != bArgs.length) {
        return false;
      }
      for (int i = 0; i < aArgs.length; i++) {
        if (!areSimilarTypes(aArgs[i], bArgs[i], classSimilarityChecker)) {
          return false;
        }
      }
      return true;
    }
    if (a instanceof GenericArrayType && b instanceof GenericArrayType) {
      GenericArrayType aType = (GenericArrayType) a;
      GenericArrayType bType = (GenericArrayType) b;
      return areSimilarTypes(
          aType.getGenericComponentType(), bType.getGenericComponentType(), classSimilarityChecker);
    }
    if (a instanceof WildcardType && b instanceof WildcardType) {
      WildcardType aType = (WildcardType) a;
      WildcardType bType = (WildcardType) b;
      Type[] aLowerBounds = aType.getLowerBounds();
      Type[] bLowerBounds = bType.getLowerBounds();
      if (aLowerBounds.length != bLowerBounds.length) {
        return false;
      }
      for (int i = 0; i < aLowerBounds.length; i++) {
        if (!areSimilarTypes(aLowerBounds[i], bLowerBounds[i], classSimilarityChecker)) {
          return false;
        }
      }
      Type[] aUpperBounds = aType.getUpperBounds();
      Type[] bUpperBounds = bType.getUpperBounds();
      if (aUpperBounds.length != bUpperBounds.length) {
        return false;
      }
      for (int i = 0; i < aUpperBounds.length; i++) {
        if (!areSimilarTypes(aUpperBounds[i], bUpperBounds[i], classSimilarityChecker)) {
          return false;
        }
      }
      return true;
    }
    // The next section handles when one type is a wildcard type and the other is not (e.g. to
    // catch cases of `Foo` vs `? extends/super Foo`).
    if (a instanceof WildcardType ^ b instanceof WildcardType) {
      WildcardType wildcardType = a instanceof WildcardType ? (WildcardType) a : (WildcardType) b;
      Type otherType = (wildcardType == a) ? b : a;
      Type[] upperBounds = wildcardType.getUpperBounds();
      Type[] lowerBounds = wildcardType.getLowerBounds();
      if (upperBounds.length == 1 && lowerBounds.length == 0) {
        // This is the '? extends Foo' case
        return areSimilarTypes(upperBounds[0], otherType, classSimilarityChecker);
      }
      if (lowerBounds.length == 1
          && upperBounds.length == 1
          && upperBounds[0].equals(Object.class)) {
        // this is the '? super Foo' case
        return areSimilarTypes(lowerBounds[0], otherType, classSimilarityChecker);
      }
    }
    return false;
  }

  /**
   * Conceptually, this method converts aType to be like bType by adding
   * appropriate @JvmSuppressWildcards or @JvmWildcard annotations. This assumes that the two types
   * only differ in variance (e.g. `Foo` vs `? extends Foo`) and that aType is implemented in
   * Kotlin. For example, if this method is called with the (List&lt;? super String&gt;,
   * List&lt;String&gt;), it will return "List&lt;@JvmSuppressWildcards String&gt;".
   */
  static String convertToLatterViaJvmAnnotations(TypeLiteral<?> aType, TypeLiteral<?> bType) {
    String a = aType.toString();
    String b = bType.toString();
    int j = 0;
    StringBuilder conversion = new StringBuilder();
    for (int i = 0; i < a.length() && j < b.length(); i++, j++) {
      if (a.charAt(i) != b.charAt(j)) {
        if (a.startsWith(WILDCARD_EXTENDS, i)) {
          conversion.append("@JvmSuppressWildcards ");
          i += WILDCARD_EXTENDS.length(); // Skip over the "? extends " part
        } else if (a.startsWith(WILDCARD_SUPER, i)) {
          conversion.append("@JvmSuppressWildcards ");
          i += WILDCARD_SUPER.length(); // Skip over the "? super " part
        } else if (b.startsWith(WILDCARD_EXTENDS, j)) {
          conversion.append("@JvmWildcard ");
          j += WILDCARD_EXTENDS.length(); // Skip over the "? extends " part
        } else if (b.startsWith(WILDCARD_SUPER, j)) {
          conversion.append("@JvmWildcard ");
          j += WILDCARD_SUPER.length(); // Skip over the "? super " part
        }
      }
      conversion.append(a.charAt(i));
    }

    // convert any remaining wildcard types to the Kotlin-equivalent.
    return conversion.toString().replaceAll("\\? extends ", "out ").replaceAll("\\? super ", "in ");
  }

  private static boolean wasBoundInKotlin(Binding<?> binding) {
    if (binding.getSource() instanceof ElementSource) {
      ElementSource elementSource = (ElementSource) binding.getSource();
      Object declaringSource = elementSource.getDeclaringSource();
      if (declaringSource instanceof Member) {
        Member member = (Member) declaringSource;
        if (KotlinSupport.getInstance().isKotlinClass(member.getDeclaringClass())) {
          return true;
        }
      }
      if (declaringSource instanceof StackTraceElement) {
        StackTraceElement stackTraceElement = (StackTraceElement) declaringSource;
        if (stackTraceElement.getFileName() != null
            && stackTraceElement.getFileName().endsWith(".kt")) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isInjectionFromKotlin(List<Object> sources) {
    for (Object source : sources) {
      if (!(source instanceof Dependency)) {
        continue;
      }
      Dependency<?> dependency = (Dependency<?>) source;
      if (KotlinSupport.getInstance()
          .isKotlinClass(dependency.getInjectionPoint().getMember().getDeclaringClass())) {
        return true;
      }
    }
    return false;
  }

  static <T> ImmutableList<String> getSuggestions(
      Key<T> key, Injector injector, List<Object> sources) {
    ImmutableList.Builder<String> suggestions = ImmutableList.builder();
    TypeLiteral<T> type = key.getTypeLiteral();

    BindingSourceRestriction.getMissingImplementationSuggestion(GuiceInternal.GUICE_INTERNAL, key)
        .ifPresent(suggestions::add);

    boolean injectionFromKotlin = isInjectionFromKotlin(sources);

    // Keys which have a similar appearance as the desired key
    ImmutableList<Binding<?>> similarBindings =
        injector.getAllBindings().values().stream()
            .filter(b -> !(b instanceof UntargettedBinding)) // These aren't valid matches
            .filter(
                b -> areSimilarLookingTypes(b.getKey().getTypeLiteral().getType(), type.getType()))
            .collect(toImmutableList());

    // Bindings which differ only in variance.
    ImmutableList<Binding<?>> wildcardSuggestionBindings =
        similarBindings.stream()
            .filter(
                b ->
                    (injectionFromKotlin || wasBoundInKotlin(b))
                        && differOnlyInVariance(
                            b.getKey().getTypeLiteral().getType(), type.getType()))
            .collect(toImmutableList());

    if (!wildcardSuggestionBindings.isEmpty()) {
      suggestions.add(WILDCARDS_WARNING);
      suggestions.add(WILDCARDS_POSSIBLE_FIXES);
      for (Binding<?> wildcardSuggestionBinding : wildcardSuggestionBindings) {
        TypeLiteral<?> similarType = wildcardSuggestionBinding.getKey().getTypeLiteral();

        if (injectionFromKotlin) {
          suggestions.add(
              "\n    * Inject this: " + convertToLatterViaJvmAnnotations(type, similarType));
        }
        if (wasBoundInKotlin(wildcardSuggestionBinding)) {
          suggestions
              .add("\n    * ")
              .add(injectionFromKotlin ? "Or bind this: " : "Bind this: ")
              .add(
                  Messages.format(
                      "%s %s",
                      convertToLatterViaJvmAnnotations(similarType, type),
                      formatVarianceSuggestion(wildcardSuggestionBinding)));
        }
      }
    }

    similarBindings =
        similarBindings.stream()
            .filter(b -> !wildcardSuggestionBindings.contains(b))
            .collect(toImmutableList());

    List<String> possibleMatches = new ArrayList<>();
    if (!similarBindings.isEmpty()) {
      suggestions.add("\nDid you mean?");
      int howMany = min(similarBindings.size(), MAX_MATCHING_TYPES_REPORTED);
      for (int i = 0; i < howMany; ++i) {
        Key<?> bindingKey = similarBindings.get(i).getKey();
        // TODO: Look into a better way to prioritize suggestions. For example, possibly
        // use levenshtein distance of the given annotation vs actual annotation.
        suggestions.add(
            Messages.format(
                "\n    * %s",
                formatSuggestion(bindingKey, injector.getExistingBinding(bindingKey))));
      }
      int remaining = similarBindings.size() - MAX_MATCHING_TYPES_REPORTED;
      if (remaining > 0) {
        String plural = (remaining == 1) ? "" : "s";
        suggestions.add(
            Messages.format(
                "\n    * %d more binding%s with other annotations.", remaining, plural));
      }
    } else if (wildcardSuggestionBindings.isEmpty()) {
      // For now, do a simple substring search for possibilities. This can help spot
      // issues when there are generics being used (such as a wrapper class) and the
      // user has forgotten they need to bind based on the wrapper, not the underlying
      // class. In the future, consider doing a strict in-depth type search.
      // TODO: Look into a better way to prioritize suggestions. For example, possbily
      // use levenshtein distance of the type literal strings.
      String want = type.toString();
      Map<Key<?>, Binding<?>> bindingMap = injector.getAllBindings();
      for (Key<?> bindingKey : bindingMap.keySet()) {
        Binding<?> binding = bindingMap.get(bindingKey);
        // Ignore untargeted bindings, those aren't valid matches.
        if (binding instanceof UntargettedBinding) {
          continue;
        }
        String have = bindingKey.getTypeLiteral().toString();
        if (have.contains(want) || want.contains(have)) {
          possibleMatches.add(formatSuggestion(bindingKey, bindingMap.get(bindingKey)));
          // TODO: Consider a check that if there are more than some number of results,
          // don't suggest any.
          if (possibleMatches.size() > MAX_RELATED_TYPES_REPORTED) {
            // Early exit if we have found more than we need.
            break;
          }
        }
      }

      if (!possibleMatches.isEmpty() && (possibleMatches.size() <= MAX_RELATED_TYPES_REPORTED)) {
        suggestions.add("\nDid you mean?");
        for (String possibleMatch : possibleMatches) {
          suggestions.add(Messages.format("\n    * %s", possibleMatch));
        }
      }
    }

    // If where are no possibilities to suggest, then handle the case of missing
    // annotations on simple types. This is usually a bad idea.
    if (similarBindings.isEmpty()
        && possibleMatches.isEmpty()
        && wildcardSuggestionBindings.isEmpty()
        && key.getAnnotationType() == null
        && COMMON_AMBIGUOUS_TYPES.contains(key.getTypeLiteral().getRawType())) {
      // We don't recommend using such simple types without annotations.
      suggestions.add("\nThe key seems very generic, did you forget an annotation?");
    }

    return suggestions.build();
  }

  private static String formatSuggestion(Key<?> key, Binding<?> binding) {
    Formatter fmt = new Formatter();
    fmt.format("%s bound ", Messages.convert(key));
    new SourceFormatter(binding.getSource(), fmt, /* omitPreposition= */ false).format();
    return fmt.toString();
  }

  private static String formatVarianceSuggestion(Binding<?> binding) {
    Formatter fmt = new Formatter();
    new SourceFormatter(binding.getSource(), fmt, /* omitPreposition= */ false).format();
    return fmt.toString();
  }
}

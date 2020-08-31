package com.google.inject.internal;

import static java.lang.Math.min;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

// TODO(b/165344346): Migrate to use suggest hints API.
/** Helper class to find hints for {@link MissingImplementationError}. */
final class MissingImplementationErrorHints {

  private MissingImplementationErrorHints() {}

  /** When a binding is not found, show at most this many bindings with the same type */
  private static final int MAX_MATCHING_TYPES_REPORTED = 3;

  /** When a binding is not found, show at most this many bindings that have some similarities */
  private static final int MAX_RELATED_TYPES_REPORTED = 3;

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

  static <T> ImmutableList<String> getSuggestions(Key<T> key, Injector injector) {
    ImmutableList.Builder<String> suggestions = ImmutableList.builder();
    TypeLiteral<T> type = key.getTypeLiteral();
    // Keys which have similar strings as the desired key
    List<String> possibleMatches = new ArrayList<>();
    List<Binding<T>> sameTypes = injector.findBindingsByType(type);
    if (!sameTypes.isEmpty()) {
      suggestions.add("%nDid you mean?");
      int howMany = min(sameTypes.size(), MAX_MATCHING_TYPES_REPORTED);
      for (int i = 0; i < howMany; ++i) {
        // TODO: Look into a better way to prioritize suggestions. For example, possbily
        // use levenshtein distance of the given annotation vs actual annotation.
        suggestions.add(Messages.format("%n    * %s", sameTypes.get(i).getKey()));
      }
      int remaining = sameTypes.size() - MAX_MATCHING_TYPES_REPORTED;
      if (remaining > 0) {
        String plural = (remaining == 1) ? "" : "s";
        suggestions.add(
            Messages.format("%n    %d more binding%s with other annotations.", remaining, plural));
      }
      suggestions.add("%n");
    } else {
      // For now, do a simple substring search for possibilities. This can help spot
      // issues when there are generics being used (such as a wrapper class) and the
      // user has forgotten they need to bind based on the wrapper, not the underlying
      // class. In the future, consider doing a strict in-depth type search.
      // TODO: Look into a better way to prioritize suggestions. For example, possbily
      // use levenshtein distance of the type literal strings.
      String want = type.toString();
      Map<Key<?>, Binding<?>> bindingMap = injector.getAllBindings();
      for (Key<?> bindingKey : bindingMap.keySet()) {
        String have = bindingKey.getTypeLiteral().toString();
        if (have.contains(want) || want.contains(have)) {
          Formatter fmt = new Formatter();
          if (InternalFlags.enableExperimentalErrorMessages()) {
            fmt.format("%s bound ", Messages.convert(bindingKey));
            new SourceFormatter(
                    bindingMap.get(bindingKey).getSource(), fmt, /* omitPreposition= */ false)
                .format();
          } else {
            fmt.format("%s bound", Messages.convert(bindingKey));
            Messages.formatSource(fmt, bindingMap.get(bindingKey).getSource());
          }
          possibleMatches.add(fmt.toString());
          // TODO: Consider a check that if there are more than some number of results,
          // don't suggest any.
          if (possibleMatches.size() > MAX_RELATED_TYPES_REPORTED) {
            // Early exit if we have found more than we need.
            break;
          }
        }
      }

      if (!possibleMatches.isEmpty() && (possibleMatches.size() <= MAX_RELATED_TYPES_REPORTED)) {
        suggestions.add("%nDid you mean?");
        for (String possibleMatch : possibleMatches) {
          suggestions.add(Messages.format("%n    %s", possibleMatch));
        }
      }
    }

    // If where are no possibilities to suggest, then handle the case of missing
    // annotations on simple types. This is usually a bad idea.
    if (sameTypes.isEmpty()
        && possibleMatches.isEmpty()
        && key.getAnnotationType() == null
        && COMMON_AMBIGUOUS_TYPES.contains(key.getTypeLiteral().getRawType())) {
      // We don't recommend using such simple types without annotations.
      suggestions.add("%nThe key seems very generic, did you forget an annotation?");
    }

    return suggestions.build();
  }
}

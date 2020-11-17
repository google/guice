package com.google.inject.internal;

import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ErrorDetail;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Error reported by Guice when duplicate elements are found in a {@link Multibinder} that does not
 * permit duplicates.
 */
final class DuplicateElementError<T> extends InternalErrorDetail<DuplicateElementError<T>> {
  private final Key<Set<T>> setKey;
  private final ImmutableMultimap<T, Element<T>> elements;

  DuplicateElementError(
      Key<Set<T>> setKey, List<Binding<T>> bindings, T[] values, List<Object> sources) {
    this(setKey, indexElements(bindings, values), sources);
  }

  private DuplicateElementError(
      Key<Set<T>> setKey, ImmutableMultimap<T, Element<T>> elements, List<Object> sources) {
    super(
        ErrorId.DUPLICATE_ELEMENT,
        String.format("Duplicate elements found in Multibinder %s.", Messages.convert(setKey)),
        sources,
        null);
    this.setKey = setKey;
    this.elements = elements;
  }

  @Override
  protected void formatDetail(List<ErrorDetail<?>> others, Formatter formatter) {
    formatter.format("%n%s%n", Messages.bold("Duplicates:"));
    int duplicateIndex = 1;
    for (Map.Entry<T, Collection<Element<T>>> entry : elements.asMap().entrySet()) {
      formatter.format("%-2s: ", duplicateIndex++);
      if (entry.getValue().size() > 1) {
        Set<String> valuesAsString =
            entry.getValue().stream()
                .map(element -> element.value.toString())
                .collect(Collectors.toSet());
        if (valuesAsString.size() == 1) {
          // String representation of the duplicates elements are the same, so only print out one.
          formatter.format("Element: %s%n", Messages.redBold(valuesAsString.iterator().next()));
          formatter.format("    Bound at:%n");
          int index = 1;
          for (Element<T> element : entry.getValue()) {
            formatter.format("    %-2s: ", index++);
            formatElement(element, formatter);
          }
        } else {
          // Print out all elements as string when there are different string representations of the
          // elements. To keep the logic simple, same strings are not grouped together unless all
          // elements have the same string represnetation. This means some strings may be printed
          // out multiple times.
          // There is no indentation for the first duplicate element.
          boolean indent = false;
          for (Element<T> element : entry.getValue()) {
            if (indent) {
              formatter.format("    ");
            } else {
              indent = true;
            }
            formatter.format("Element: %s%n", Messages.redBold(element.value.toString()));
            formatter.format("    Bound at: ");
            formatElement(element, formatter);
          }
        }
      }
    }
    formatter.format("%n%s%n", Messages.bold("Multibinder declared at:"));
    // Multibinder source includes the key of the set. Filter it out since it's not useful in the
    // printed error stack.
    List<Object> filteredSource =
        getSources().stream()
            .filter(
                source -> {
                  if (source instanceof Dependency) {
                    return !((Dependency<?>) source).getKey().equals(setKey);
                  }
                  return true;
                })
            .collect(Collectors.toList());
    ErrorFormatter.formatSources(filteredSource, formatter);
  }

  private void formatElement(Element<T> element, Formatter formatter) {
    Object source = element.binding.getSource();
    new SourceFormatter(
            source,
            formatter,
            /** omitPreposition= */
            true)
        .format();
  }

  @Override
  public DuplicateElementError<T> withSources(List<Object> newSources) {
    return new DuplicateElementError<>(setKey, elements, newSources);
  }

  static <T> ImmutableMultimap<T, Element<T>> indexElements(List<Binding<T>> bindings, T[] values) {
    ImmutableMultimap.Builder<T, Element<T>> map = ImmutableMultimap.builder();
    for (int i = 0; i < values.length; i++) {
      map.put(values[i], new Element<T>(values[i], bindings.get(i)));
    }
    return map.build();
  }

  static class Element<T> {
    T value;
    Binding<T> binding;

    Element(T value, Binding<T> binding) {
      this.value = value;
      this.binding = binding;
    }
  }
}

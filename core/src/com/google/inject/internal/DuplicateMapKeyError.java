package com.google.inject.internal;

import com.google.common.collect.Multimap;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.spi.ErrorDetail;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Error reported by Guice when a duplicate key is found in a {@link MapBinder} that does not permit
 * duplicates.
 */
final class DuplicateMapKeyError<K, V> extends InternalErrorDetail<DuplicateMapKeyError<K, V>> {
  private final Key<Map<K, V>> mapKey;
  private final Multimap<K, Binding<V>> duplicates;

  DuplicateMapKeyError(
      Key<Map<K, V>> mapKey, Multimap<K, Binding<V>> duplicates, List<Object> sources) {
    super(ErrorId.DUPLICATE_MAP_KEY, getDuplicateKeysMessage(mapKey, duplicates), sources, null);
    this.mapKey = mapKey;
    this.duplicates = duplicates;
  }

  @Override
  protected final void formatDetail(List<ErrorDetail<?>> others, Formatter formatter) {
    formatter.format("%n%s%n", Messages.bold("Duplicates:"));

    for (Map.Entry<K, Collection<Binding<V>>> entry : duplicates.asMap().entrySet()) {
      formatter.format("  Key: %s%n", Messages.redBold(entry.getKey().toString()));
      formatter.format("  Bound at:%n");
      int index = 1;
      for (Binding<V> binding : entry.getValue()) {
        formatter.format("    %-2s: ", index++);
        new SourceFormatter(
                binding.getSource(),
                formatter,
                /** omitPreposition= */
                true)
            .format();
      }
      formatter.format("%n");
    }

    formatter.format("%s%n", Messages.bold("MapBinder declared at:"));
    ErrorFormatter.formatSources(getSources(), formatter);
  }

  @Override
  public DuplicateMapKeyError<K, V> withSources(List<Object> newSources) {
    return new DuplicateMapKeyError<>(mapKey, duplicates, newSources);
  }

  private static <K, V> String getDuplicateKeysMessage(
      Key<Map<K, V>> mapKey, Multimap<K, Binding<V>> duplicates) {
    Set<K> duplicateKeys = duplicates.keySet();
    String mapBinderKey = Messages.convert(mapKey).toString();
    String firstDuplicateKey = duplicateKeys.iterator().next().toString();
    if (duplicateKeys.size() == 1) {
      return String.format("Duplicate key \"%s\" found in %s.", firstDuplicateKey, mapBinderKey);
    } else {
      return String.format(
          "\"%s\" and %s other duplicate keys found in %s.",
          firstDuplicateKey, duplicateKeys.size() - 1, mapBinderKey);
    }
  }
}
